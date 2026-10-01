package com.karen_yao.chinesetravel.features.capture.ui

import androidx.lifecycle.ViewModel
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns one capture operation, its temporary image, and acknowledged UI commands. */
internal class CaptureViewModel(private val services: CaptureWorkflowDependencies) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureUiState())
    val uiState = mutableState.asStateFlow()
    private var generation = 0L
    private var attached = false
    private var resumed = false
    private var nextOperation = 0L
    private var operation: Long? = null
    private var nextEffect = 0L
    private var work: Job? = null
    private var lease: ManagedImageLease? = null
    private var location: CaptureLocation? = null
    private var locationFeedback: String? = null
    private var cameraDestination: File? = null
    private val abandonedCameraFiles = mutableMapOf<Long, File>()

    /** Called on the main thread when a new Fragment view is created. */
    @MainThread
    fun attachView(): Long {
        cancelOperation()
        generation++
        attached = true
        resumed = false
        mutableState.value = CaptureUiState()
        return generation
    }

    @MainThread
    fun onEvent(viewGeneration: Long, event: CaptureEvent) {
        // All producers (including the camera's main executor) enter on main. Reduce
        // synchronously so an effect is acknowledged before the UI drains the next one.
        // Late CameraX writes still need cleanup after the ViewModel has been cleared.
        // Only abandoned destinations may be deleted. Duplicate callbacks for successful
        // or handed-off images must never delete referenced files.
        val completedId = when (event) {
            is CaptureEvent.CameraSaved -> event.operationId
            is CaptureEvent.CameraCaptureFailed -> event.operationId
            else -> null
        }
        completedId?.let { abandonedCameraFiles.remove(it)?.let(::discard) }
        if (!attached || viewGeneration != generation) return
        reduce(event)
    }

    private fun reduce(event: CaptureEvent) {
        when (event) {
            CaptureEvent.Resumed -> { resumed = true; captureWhenReady() }
            CaptureEvent.Paused -> {
                resumed = false
                mutableState.value = uiState.value.copy(cameraReady = false)
                when (uiState.value.state) {
                    CaptureState.GettingLocation, CaptureState.WaitingForCamera,
                    CaptureState.Capturing -> cancelOperation()
                    else -> Unit
                }
            }
            CaptureEvent.ViewDestroyed -> {
                attached = false
                resumed = false
                cancelOperation()
                mutableState.value = CaptureUiState()
            }
            is CaptureEvent.CaptureTapped -> {
                if (!canBegin()) return
                if (!uiState.value.cameraReady) {
                    emit(CaptureEffect.Message("Camera not ready. Please wait..."))
                    return
                }
                begin()
                if (event.hasLocationPermission) getLocation() else {
                    state(CaptureState.RequestingLocationPermission)
                    emit(CaptureEffect.Message("Location is optional. Choose whether to attach it to this photo."))
                    emit(CaptureEffect.RequestLocationPermission)
                }
            }
            CaptureEvent.GalleryTapped -> if (canBegin()) openGallery()
            is CaptureEvent.PermissionResult -> {
                if (!matches(event.operationId, CaptureState.RequestingLocationPermission)) return
                if (event.granted) getLocation() else {
                    locationFeedback = (decideLocationAfterPermission(false) as
                        CaptureLocationDecision.CaptureWithoutLocation).message
                    state(CaptureState.WaitingForCamera)
                    captureWhenReady()
                }
            }
            is CaptureEvent.GalleryResult -> {
                if (!matches(event.operationId, CaptureState.SelectingGalleryImage)) return
                if (event.uri == null) cancelOperation() else importImage(event.uri)
            }
            is CaptureEvent.CameraReady -> {
                mutableState.value = uiState.value.copy(cameraReady = true, isBackCamera = event.isBackCamera)
                event.message?.let { emit(CaptureEffect.Message(it)) }
                captureWhenReady()
            }
            CaptureEvent.SwitchCameraTapped -> if (canBegin()) {
                mutableState.value = uiState.value.copy(cameraReady = false)
                emit(CaptureEffect.Message("Switching camera..."))
                emit(CaptureEffect.SwitchCamera)
            }
            is CaptureEvent.CameraFailed -> {
                mutableState.value = uiState.value.copy(cameraReady = false)
                when (uiState.value.state) {
                    CaptureState.SelectingGalleryImage, CaptureState.ImportingImage,
                    CaptureState.RecognizingText, CaptureState.WaitingForTextSelection,
                    CaptureState.AwaitingNoTextChoice, CaptureState.Saving ->
                        emit(CaptureEffect.Message(event.message))
                    else -> fail("Camera", event.message)
                }
            }
            is CaptureEvent.CameraSaved -> {
                if (!matches(event.operationId, CaptureState.Capturing) || event.file != cameraDestination) return
                cameraDestination = null
                locationFeedback?.let { emit(CaptureEffect.Message(it)) }
                recognize(event.file)
            }
            is CaptureEvent.CameraCaptureFailed -> {
                if (!matches(event.operationId, CaptureState.Capturing) || event.file != cameraDestination) return
                cameraDestination = null
                fail("Capture", "Capture failed: ${event.error.message}")
                if (event.error.cameraClosed) emit(CaptureEffect.RestartCamera)
            }
            is CaptureEvent.NoTextChoice -> {
                if (!matches(event.operationId, CaptureState.AwaitingNoTextChoice)) return
                cancelOperation()
                when (event.choice) {
                    NoTextAction.GALLERY -> openGallery()
                    NoTextAction.TRY_AGAIN -> emit(CaptureEffect.NavigateBack)
                    NoTextAction.HOME -> emit(CaptureEffect.NavigateHome)
                    NoTextAction.DISMISS -> Unit
                }
            }
            is CaptureEvent.EffectHandled -> {
                val envelope = uiState.value.effects.firstOrNull()?.takeIf { it.id == event.effectId } ?: return
                removeEffect(envelope.id)
                if (envelope.effect is CaptureEffect.OpenTextSelection) {
                    // commit() accepted the transaction: text selection now owns the image.
                    lease = null
                    finish()
                }
                if (envelope.effect == CaptureEffect.NavigateBack && uiState.value.state == CaptureState.Saving) finish()
            }
            is CaptureEvent.EffectFailed -> {
                val envelope = uiState.value.effects.firstOrNull()?.takeIf { it.id == event.effectId } ?: return
                removeEffect(envelope.id)
                val stage = if (envelope.effect == CaptureEffect.OpenGallery) "Gallery" else "Capture"
                val prefix = if (stage == "Gallery") "Could not open the photo picker" else "Could not process image"
                fail(stage, "$prefix: ${event.message}")
            }
        }
    }

    private fun canBegin() = resumed && uiState.value.controlsEnabled
    private fun matches(id: Long, expected: CaptureState) = operation == id && uiState.value.state == expected
    private fun begin() { cancelOperation(); operation = ++nextOperation }
    private fun openGallery() {
        begin()
        state(CaptureState.SelectingGalleryImage)
        emit(CaptureEffect.OpenGallery)
    }

    private fun getLocation() {
        state(CaptureState.GettingLocation)
        val id = checkNotNull(operation)
        work = viewModelScope.launch {
            try {
                val result = services.location.currentLocation()
                ensureActive()
                if (!matches(id, CaptureState.GettingLocation)) return@launch
                when (result) {
                    is CaptureLocationResult.Success -> location = result.location
                    is CaptureLocationResult.Unavailable -> locationFeedback =
                        decideLocationAfterUnavailable(result.reason).message
                }
                state(CaptureState.WaitingForCamera)
                captureWhenReady()
            } catch (cancelled: CancellationException) {
                if (operation == id) cancelOperation()
                throw cancelled
            }
            catch (_: Exception) {
                if (matches(id, CaptureState.GettingLocation)) {
                    locationFeedback = decideLocationAfterUnavailable(
                        com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider.FailureReason.REQUEST_FAILED
                    ).message
                    state(CaptureState.WaitingForCamera)
                    captureWhenReady()
                }
            }
        }
    }

    private fun captureWhenReady() {
        if (uiState.value.state != CaptureState.WaitingForCamera || !resumed || !uiState.value.cameraReady) return
        try {
            val file = services.createCameraFile()
            lease = ManagedImageLease(file, services.filesRoot)
            cameraDestination = file
            state(CaptureState.Capturing)
            emit(CaptureEffect.TakePhoto(file, location))
        } catch (error: Exception) { fail("Capture", "Capture failed: ${error.message}") }
    }

    private fun importImage(uri: String) {
        state(CaptureState.ImportingImage)
        val id = checkNotNull(operation)
        work = viewModelScope.launch {
            try {
                val file = services.galleryImporter.importImage(uri)
                if (!matches(id, CaptureState.ImportingImage)) { discard(file); return@launch }
                lease = ManagedImageLease(file, services.filesRoot)
                recognizeInJob(file, id)
            } catch (cancelled: CancellationException) {
                if (operation == id) cancelOperation()
                throw cancelled
            }
            catch (error: Exception) { if (operation == id) processingFailed(error) }
        }
    }

    private fun recognize(file: File) {
        val id = checkNotNull(operation)
        work = viewModelScope.launch {
            try { recognizeInJob(file, id) }
            catch (cancelled: CancellationException) {
                if (operation == id) cancelOperation()
                throw cancelled
            }
            catch (error: Exception) { if (operation == id) processingFailed(error) }
        }
    }

    private suspend fun recognizeInJob(file: File, id: Long) {
        state(CaptureState.RecognizingText)
        val outcome = services.recognizer.recognize(file)
        kotlin.coroutines.coroutineContext.ensureActive()
        if (!matches(id, CaptureState.RecognizingText)) return
        when (val decision = classifyRecognizedText(outcome.selectedPass.lines.map { it.text })) {
            RecognizedTextDecision.NoText -> {
                state(CaptureState.AwaitingNoTextChoice)
                emit(CaptureEffect.ShowNoTextDialog)
            }
            RecognizedTextDecision.TextTooShort -> {
                releaseImage()
                finish()
                emit(CaptureEffect.Message("Text too short, please try again"))
            }
            is RecognizedTextDecision.ChooseText -> {
                state(CaptureState.WaitingForTextSelection)
                emit(CaptureEffect.OpenTextSelection(decision.lines, file))
            }
            is RecognizedTextDecision.SaveText -> {
                state(CaptureState.Saving)
                lease?.markPersistenceStarted()
                val count = services.saver.save(decision.text, file)
                kotlin.coroutines.coroutineContext.ensureActive()
                if (!matches(id, CaptureState.Saving)) return
                emit(CaptureEffect.Message("Saved. Total rows: $count"))
                emit(CaptureEffect.NavigateBack)
            }
        }
    }

    private fun processingFailed(error: Exception) {
        val stage = when (uiState.value.state) {
            CaptureState.ImportingImage -> "Could not import image"
            CaptureState.Saving -> "Save failed"
            else -> "OCR failed"
        }
        fail(stage, "$stage: ${error.message}")
    }

    private fun fail(stage: String, message: String) {
        cancelOperation()
        state(CaptureState.Error(stage, message))
        emit(CaptureEffect.Message(message))
    }
    private fun cancelOperation() {
        work?.cancel()
        work = null
        cameraDestination?.let { file -> operation?.let { abandonedCameraFiles[it] = file } }
        cameraDestination = null
        releaseImage()
        operation = null
        location = null
        locationFeedback = null
        mutableState.value = uiState.value.copy(state = CaptureState.Idle, effects = emptyList())
    }
    private fun finish() {
        operation = null
        lease = null
        location = null
        locationFeedback = null
        state(CaptureState.Idle)
    }
    private fun releaseImage() {
        val owned = lease ?: return
        lease = null
        viewModelScope.launch(NonCancellable + services.cleanupDispatcher) { owned.discardIfSafe() }
    }
    private fun discard(file: File) {
        viewModelScope.launch(NonCancellable + services.cleanupDispatcher) {
            ManagedImageLease(file, services.filesRoot).discardIfSafe()
        }
    }
    private fun state(state: CaptureState) { mutableState.value = uiState.value.copy(state = state) }
    private fun emit(effect: CaptureEffect) {
        if (!attached) return
        val envelope = CaptureEffectEnvelope(++nextEffect, operation, generation, effect)
        mutableState.value = uiState.value.copy(effects = uiState.value.effects + envelope)
    }
    private fun removeEffect(id: Long) {
        mutableState.value = uiState.value.copy(effects = uiState.value.effects.filterNot { it.id == id })
    }
    override fun onCleared() {
        attached = false
        resumed = false
        cancelOperation()
        services.recognizer.close()
    }
}

internal class CaptureViewModelFactory(
    private val createServices: () -> CaptureWorkflowDependencies
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CaptureViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return CaptureViewModel(createServices()) as T
    }
}
