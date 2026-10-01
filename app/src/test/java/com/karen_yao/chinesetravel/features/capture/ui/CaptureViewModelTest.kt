package com.karen_yao.chinesetravel.features.capture.ui

import androidx.lifecycle.ViewModelStore
import com.karen_yao.chinesetravel.features.capture.camera.OcrLine
import com.karen_yao.chinesetravel.features.capture.camera.OcrOutcome
import com.karen_yao.chinesetravel.features.capture.camera.OcrPass
import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider.FailureReason
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var root: File
    private lateinit var model: CaptureViewModel
    private var generation = 0L
    private var saveCalls = 0
    private var recognitionCalls = 0
    private var locationCalls = 0
    private var importCalls = 0
    private var captureCalls = 0
    private var closed = 0
    private var location: suspend () -> CaptureLocationResult = {
        CaptureLocationResult.Unavailable(FailureReason.LOCATION_DISABLED)
    }
    private var recognize: suspend () -> OcrOutcome = { outcome("测试文本") }
    private var save: suspend () -> Int = { 1 }
    private var import: suspend (String) -> File = { error("unexpected import") }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        root = temporary.newFolder("managed")
        model = CaptureViewModel(CaptureWorkflowDependencies(
            filesRoot = root,
            location = CaptureLocationSource { locationCalls++; location() },
            recognizer = object : CaptureTextRecognizer {
                override suspend fun recognize(originalFile: File): OcrOutcome {
                    recognitionCalls++
                    return recognize()
                }
                override fun close() { closed++ }
            },
            galleryImporter = CaptureGallerySource { importCalls++; import(it) },
            saver = CaptureSaver { _, _ -> saveCalls++; save() },
            cleanupDispatcher = dispatcher,
            createCameraFile = {
                captureCalls++
                File.createTempFile("snap_", ".jpg", root)
            }
        ))
        store.put("capture", model)
        generation = model.attachView()
        send(CaptureEvent.Resumed)
        send(CaptureEvent.CameraReady(true))
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    @Test fun initialTapWithoutReadyCameraOnlyShowsExistingFeedback() = runTest(dispatcher) {
        send(CaptureEvent.Paused)
        send(CaptureEvent.Resumed)
        send(CaptureEvent.CaptureTapped(true))
        assertState(CaptureState.Idle)
        assertEquals("Camera not ready. Please wait...",
            (model.uiState.value.effects.single().effect as CaptureEffect.Message).text)
    }

    @Test fun busyCaptureAndGalleryTapsDoNotStartSecondOperation() = runTest(dispatcher) {
        capture()
        repeat(3) { send(CaptureEvent.CaptureTapped(true)); send(CaptureEvent.GalleryTapped) }
        assertState(CaptureState.Capturing)
        assertFalse(model.uiState.value.controlsEnabled)
        assertEquals(1, model.uiState.value.effects.count { it.effect is CaptureEffect.TakePhoto })
    }

    @Test fun permissionDenialBeforeCameraReadinessCapturesExactlyOnce() = runTest(dispatcher) {
        val id = requestPermission()
        send(CaptureEvent.Paused)
        assertState(CaptureState.RequestingLocationPermission)
        send(CaptureEvent.PermissionResult(id, false))
        assertState(CaptureState.WaitingForCamera)
        send(CaptureEvent.Resumed)
        assertState(CaptureState.WaitingForCamera)
        repeat(2) { send(CaptureEvent.CameraReady(true)) }
        send(CaptureEvent.PermissionResult(id, false))
        assertState(CaptureState.Capturing)
        val photos = model.uiState.value.effects.filter { it.effect is CaptureEffect.TakePhoto }
        assertEquals(1, photos.size)
        assertNull((photos.single().effect as CaptureEffect.TakePhoto).location)
    }

    @Test fun permissionGrantAfterReadinessUsesLocationOnce() = runTest(dispatcher) {
        val coordinates = CaptureLocation(49.2, -123.1)
        location = { CaptureLocationResult.Success(coordinates) }
        val id = requestPermission()
        send(CaptureEvent.Paused)
        send(CaptureEvent.Resumed)
        send(CaptureEvent.CameraReady(false))
        assertState(CaptureState.RequestingLocationPermission)
        send(CaptureEvent.PermissionResult(id, true))
        assertEquals(coordinates, (effect<CaptureEffect.TakePhoto>().effect as CaptureEffect.TakePhoto).location)
        assertFalse(model.uiState.value.isBackCamera)
    }

    @Test fun everyUnavailableLocationReasonStillCapturesAndRetainsFeedback() = runTest(dispatcher) {
        for (reason in FailureReason.entries) {
            location = { CaptureLocationResult.Unavailable(reason) }
            val command = capture()
            assertNull((command.effect as CaptureEffect.TakePhoto).location)
            recognize = { awaitCancellation() }
            complete(command)
            assertTrue(model.uiState.value.effects.any {
                (it.effect as? CaptureEffect.Message)?.text?.contains(decideLocationAfterUnavailable(reason).message) == true
            })
            destroyAndReattach()
        }
    }

    @Test fun lookupFailureAlsoContinuesWithoutCoordinates() = runTest(dispatcher) {
        location = { error("provider failed") }
        assertNull((capture().effect as CaptureEffect.TakePhoto).location)
    }

    @Test fun pauseCancelsLocationLookupAndItsLaterResult() = runTest(dispatcher) {
        val gate = CompletableDeferred<CaptureLocationResult>()
        location = { gate.await() }
        send(CaptureEvent.CaptureTapped(true))
        assertState(CaptureState.GettingLocation)
        send(CaptureEvent.Paused)
        gate.complete(CaptureLocationResult.Success(CaptureLocation(1.0, 2.0)))
        dispatcher.scheduler.runCurrent()
        assertState(CaptureState.Idle)
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun destructionWhileWaitingForCameraIgnoresOldReadyAndPermissionResults() = runTest(dispatcher) {
        val id = requestPermission()
        send(CaptureEvent.Paused)
        send(CaptureEvent.PermissionResult(id, false))
        assertState(CaptureState.WaitingForCamera)
        val oldView = generation
        destroyAndReattach()
        model.onEvent(oldView, CaptureEvent.CameraReady(true))
        model.onEvent(oldView, CaptureEvent.PermissionResult(id, false))
        dispatcher.scheduler.runCurrent()
        assertState(CaptureState.Idle)
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun pauseDuringCaptureCleansLateDestinationWithoutTouchingNewOperation() = runTest(dispatcher) {
        val old = capture()
        val oldFile = (old.effect as CaptureEffect.TakePhoto).file
        send(CaptureEvent.Paused)
        assertFalse(oldFile.exists())
        send(CaptureEvent.Resumed)
        send(CaptureEvent.CameraReady(true))
        val current = capture()
        oldFile.writeText("late camera write")
        send(CaptureEvent.CameraSaved(checkNotNull(old.operationId), oldFile))
        assertFalse(oldFile.exists())
        assertTrue((current.effect as CaptureEffect.TakePhoto).file.exists())
        assertState(CaptureState.Capturing)
    }

    @Test fun lateCameraCompletionAfterViewModelClearedStillCleansUp() = runTest(dispatcher) {
        val command = capture()
        val file = (command.effect as CaptureEffect.TakePhoto).file
        store.clear()
        dispatcher.scheduler.runCurrent()
        file.writeText("late")
        send(CaptureEvent.CameraSaved(checkNotNull(command.operationId), file))
        assertFalse(file.exists())
        assertTrue(model.uiState.value.effects.isEmpty())
        assertEquals(1, closed)
    }

    @Test fun cameraFailureAllowsRetryAndClosedCameraRequestsRestart() = runTest(dispatcher) {
        val command = capture()
        val file = (command.effect as CaptureEffect.TakePhoto).file
        send(CaptureEvent.CameraCaptureFailed(checkNotNull(command.operationId), file,
            CaptureCameraError("closed", true)))
        assertTrue(model.uiState.value.state is CaptureState.Error)
        assertTrue(model.uiState.value.controlsEnabled)
        assertFalse(file.exists())
        assertTrue(model.uiState.value.effects.any { it.effect == CaptureEffect.RestartCamera })
        send(CaptureEvent.GalleryTapped)
        assertState(CaptureState.SelectingGalleryImage)
    }

    @Test fun pickerPauseAndCancellationReturnToIdleAndIgnoreDuplicateResult() = runTest(dispatcher) {
        val id = gallery()
        send(CaptureEvent.GalleryTapped)
        send(CaptureEvent.Paused)
        assertState(CaptureState.SelectingGalleryImage)
        send(CaptureEvent.GalleryResult(id, null))
        send(CaptureEvent.GalleryResult(id, "ignored"))
        assertState(CaptureState.Idle)
        assertTrue(model.uiState.value.controlsEnabled)
        assertEquals(0, locationCalls)
        assertEquals(0, importCalls)
        assertEquals(0, captureCalls)
        assertEquals(0, saveCalls)
        assertEquals(0, recognitionCalls)
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun galleryCancellationAfterCaptureDoesNotRestartWorkOrKeepMessages() = runTest(dispatcher) {
        recognize = { outcome("字") }
        complete(capture())
        assertState(CaptureState.Idle)
        assertTrue(model.uiState.value.effects.any { it.effect is CaptureEffect.Message })
        val previousCounts = listOf(locationCalls, importCalls, captureCalls, recognitionCalls, saveCalls)
        repeat(3) {
            val id = gallery()
            send(CaptureEvent.Paused)
            send(CaptureEvent.GalleryResult(id, null))
            send(CaptureEvent.Resumed)
            send(CaptureEvent.CameraReady(true))
            send(CaptureEvent.GalleryResult(id, "content://late-image"))
            assertState(CaptureState.Idle)
            assertTrue(model.uiState.value.controlsEnabled)
            assertEquals(previousCounts, listOf(locationCalls, importCalls, captureCalls, recognitionCalls, saveCalls))
            assertTrue(model.uiState.value.effects.isEmpty())
        }
    }

    @Test fun oldPickerResultsCannotAffectANewPickerOrView() = runTest(dispatcher) {
        val oldId = gallery()
        send(CaptureEvent.GalleryResult(oldId, null))
        val currentId = gallery()
        val queue = model.uiState.value.effects
        send(CaptureEvent.GalleryResult(oldId, "content://old-image"))
        send(CaptureEvent.GalleryResult(oldId, null))
        assertState(CaptureState.SelectingGalleryImage)
        assertEquals(queue, model.uiState.value.effects)
        val oldGeneration = generation
        destroyAndReattach()
        val newId = gallery()
        model.onEvent(oldGeneration, CaptureEvent.GalleryResult(currentId, "content://old-view"))
        assertState(CaptureState.SelectingGalleryImage)
        send(CaptureEvent.GalleryResult(newId, null))
        assertTrue(model.uiState.value.effects.isEmpty())
        assertEquals(0, importCalls)
        assertEquals(0, recognitionCalls)
        assertEquals(0, locationCalls)
    }

    @Test fun galleryProgressUsesStateWithoutProgressMessagesOrLocationLookup() = runTest(dispatcher) {
        val imported = CompletableDeferred<File>()
        val recognized = CompletableDeferred<OcrOutcome>()
        val saved = CompletableDeferred<Int>()
        import = { imported.await() }
        recognize = { recognized.await() }
        save = { saved.await() }
        val id = gallery()
        acknowledgeAll()
        send(CaptureEvent.GalleryResult(id, "content://image"))
        assertState(CaptureState.ImportingImage)
        assertTrue(model.uiState.value.effects.isEmpty())
        val file = File(root, "gallery.jpg").apply { writeText("image") }
        imported.complete(file)
        dispatcher.scheduler.runCurrent()
        assertState(CaptureState.RecognizingText)
        assertTrue(model.uiState.value.effects.isEmpty())
        send(CaptureEvent.GalleryResult(id, "content://duplicate"))
        recognized.complete(outcome("测试文本"))
        dispatcher.scheduler.runCurrent()
        assertState(CaptureState.Saving)
        assertTrue(model.uiState.value.effects.isEmpty())
        saved.complete(1)
        dispatcher.scheduler.runCurrent()
        acknowledgeAll()
        assertState(CaptureState.Idle)
        assertEquals(1, importCalls)
        assertEquals(1, recognitionCalls)
        assertEquals(1, saveCalls)
        assertEquals(0, locationCalls)
        assertEquals(0, captureCalls)
    }

    @Test fun cameraProgressUsesStateAndRetainsSuccessfulLocation() = runTest(dispatcher) {
        val located = CompletableDeferred<CaptureLocationResult>()
        val coordinates = CaptureLocation(49.2, -123.1)
        location = { located.await() }
        recognize = { awaitCancellation() }
        send(CaptureEvent.CaptureTapped(true))
        assertState(CaptureState.GettingLocation)
        assertTrue(model.uiState.value.effects.isEmpty())
        located.complete(CaptureLocationResult.Success(coordinates))
        dispatcher.scheduler.runCurrent()
        assertState(CaptureState.Capturing)
        val photo = effect<CaptureEffect.TakePhoto>()
        assertEquals(coordinates, (photo.effect as CaptureEffect.TakePhoto).location)
        assertFalse(model.uiState.value.effects.any { it.effect is CaptureEffect.Message })
        complete(photo)
        assertState(CaptureState.RecognizingText)
        assertTrue(model.uiState.value.effects.isEmpty())
        assertEquals(1, locationCalls)
        assertEquals(1, recognitionCalls)
    }

    @Test fun importFailureRemovesPartialFileAndEntersRecoverableError() = runTest(dispatcher) {
        val directory = File(root, "imports").apply { mkdirs() }
        val partial = File(directory, "partial.jpg")
        import = {
            GalleryImageImporter(dispatcher) { partial.apply { writeText("partial") } }
                .importImage(directory) { error("open failed") }
        }
        send(CaptureEvent.GalleryResult(gallery(), "content://image"))
        assertTrue(model.uiState.value.state is CaptureState.Error)
        assertFalse(partial.exists())
        assertEquals(0, recognitionCalls)
    }

    @Test fun destroyDuringImportCancelsAndNeverRunsOcr() = runTest(dispatcher) {
        var cancelled = false
        import = { try { awaitCancellation() } finally { cancelled = true } }
        send(CaptureEvent.GalleryResult(gallery(), "content://image"))
        assertState(CaptureState.ImportingImage)
        send(CaptureEvent.ViewDestroyed)
        assertTrue(cancelled)
        assertEquals(0, recognitionCalls)
        assertState(CaptureState.Idle)
    }

    @Test fun successfulGalleryImportReachesOcrWithManagedImage() = runTest(dispatcher) {
        val file = File(root, "gallery.jpg").apply { writeText("image") }
        import = { file }
        recognize = { outcome("甲乙", "丙丁") }
        send(CaptureEvent.GalleryResult(gallery(), "content://image"))
        assertState(CaptureState.WaitingForTextSelection)
        assertEquals(file, (effect<CaptureEffect.OpenTextSelection>().effect as CaptureEffect.OpenTextSelection).file)
        assertEquals(1, importCalls)
        assertEquals(1, recognitionCalls)
        assertEquals(0, locationCalls)
    }

    @Test fun emptyOcrHoldsImageUntilDialogDismissal() = runTest(dispatcher) {
        recognize = { outcome() }
        val command = capture()
        val file = (command.effect as CaptureEffect.TakePhoto).file
        complete(command)
        assertState(CaptureState.AwaitingNoTextChoice)
        assertTrue(file.exists())
        acknowledgeAll()
        send(CaptureEvent.NoTextChoice(checkNotNull(command.operationId), NoTextAction.DISMISS))
        assertState(CaptureState.Idle)
        assertFalse(file.exists())
    }

    @Test fun noTextButtonsKeepExistingDestinationsAndReleaseImage() = runTest(dispatcher) {
        for (choice in listOf(NoTextAction.GALLERY, NoTextAction.HOME, NoTextAction.TRY_AGAIN)) {
            recognize = { outcome() }
            val command = capture()
            complete(command)
            send(CaptureEvent.NoTextChoice(checkNotNull(command.operationId), choice))
            assertFalse((command.effect as CaptureEffect.TakePhoto).file.exists())
            val expected = when (choice) {
                NoTextAction.GALLERY -> CaptureEffect.OpenGallery
                NoTextAction.HOME -> CaptureEffect.NavigateHome
                else -> CaptureEffect.NavigateBack
            }
            assertEquals(expected, model.uiState.value.effects.single().effect)
            destroyAndReattach()
        }
    }

    @Test fun shortOcrReleasesImageWithoutSaving() = runTest(dispatcher) {
        recognize = { outcome("字") }
        val command = capture()
        complete(command)
        assertState(CaptureState.Idle)
        assertFalse((command.effect as CaptureEffect.TakePhoto).file.exists())
        assertEquals(0, saveCalls)
    }

    @Test fun singleLineSuccessfulSaveRetainsImageAndNavigationAcknowledgementFinishes() = runTest(dispatcher) {
        val command = capture()
        complete(command)
        assertState(CaptureState.Saving)
        assertEquals(1, saveCalls)
        assertTrue((command.effect as CaptureEffect.TakePhoto).file.exists())
        acknowledgeAll()
        assertState(CaptureState.Idle)
        send(CaptureEvent.ViewDestroyed)
        assertTrue(command.effect.file.exists())
    }

    @Test fun saveFailureRetainsPotentiallyCommittedImage() = runTest(dispatcher) {
        save = { error("uncertain commit") }
        val command = capture()
        complete(command)
        assertTrue(model.uiState.value.state is CaptureState.Error)
        assertTrue((command.effect as CaptureEffect.TakePhoto).file.exists())
    }

    @Test fun destructionDuringSaveCancelsButRetainsImage() = runTest(dispatcher) {
        var cancelled = false
        save = { try { awaitCancellation() } finally { cancelled = true } }
        val command = capture()
        complete(command)
        assertState(CaptureState.Saving)
        send(CaptureEvent.ViewDestroyed)
        assertTrue(cancelled)
        assertTrue((command.effect as CaptureEffect.TakePhoto).file.exists())
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun destructionDuringOcrCancelsAndDeletesImageWithoutSavingOrNavigation() = runTest(dispatcher) {
        val gate = CompletableDeferred<OcrOutcome>()
        var cancelled = false
        recognize = { try { gate.await() } finally { cancelled = true } }
        val command = capture()
        complete(command)
        assertState(CaptureState.RecognizingText)
        val oldView = generation
        destroyAndReattach()
        gate.complete(outcome("甲乙", "丙丁"))
        model.onEvent(oldView, CaptureEvent.CameraSaved(checkNotNull(command.operationId),
            (command.effect as CaptureEffect.TakePhoto).file))
        dispatcher.scheduler.runCurrent()
        assertTrue(cancelled)
        assertFalse(command.effect.file.exists())
        assertEquals(0, saveCalls)
        assertEquals(1, recognitionCalls)
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun ordinaryPauseDoesNotCancelOcrAndEffectsWaitForUiAcknowledgement() = runTest(dispatcher) {
        val gate = CompletableDeferred<OcrOutcome>()
        recognize = { gate.await() }
        val command = capture()
        complete(command)
        send(CaptureEvent.Paused)
        assertState(CaptureState.RecognizingText)
        gate.complete(outcome("甲乙", "丙丁"))
        dispatcher.scheduler.runCurrent()
        assertState(CaptureState.WaitingForTextSelection)
        assertTrue((command.effect as CaptureEffect.TakePhoto).file.exists())
        send(CaptureEvent.Resumed)
        assertEquals(1, model.uiState.value.effects.count { it.effect is CaptureEffect.OpenTextSelection })
    }

    @Test fun ocrFailureReleasesManagedImage() = runTest(dispatcher) {
        recognize = { error("recognition failed") }
        val command = capture()
        complete(command)
        assertTrue(model.uiState.value.state is CaptureState.Error)
        assertFalse((command.effect as CaptureEffect.TakePhoto).file.exists())
        assertEquals(0, saveCalls)
    }

    @Test fun selectionAcknowledgementTransfersOriginalImageAndNeverReplays() = runTest(dispatcher) {
        recognize = { outcome("八邑酒楼", "永庆坊") }
        val command = capture()
        complete(command)
        assertState(CaptureState.WaitingForTextSelection)
        val navigation = effect<CaptureEffect.OpenTextSelection>()
        val payload = navigation.effect as CaptureEffect.OpenTextSelection
        assertEquals(listOf("八邑酒楼", "永庆坊"), payload.lines)
        assertEquals((command.effect as CaptureEffect.TakePhoto).file, payload.file)
        acknowledgeAll()
        send(CaptureEvent.EffectHandled(navigation.id))
        send(CaptureEvent.CameraSaved(checkNotNull(command.operationId), payload.file))
        destroyAndReattach()
        assertTrue(payload.file.exists())
        assertState(CaptureState.Idle)
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun rejectedSelectionNavigationDeletesUnreferencedImage() = runTest(dispatcher) {
        recognize = { outcome("甲乙", "丙丁") }
        val command = capture()
        complete(command)
        val navigation = effect<CaptureEffect.OpenTextSelection>()
        acknowledgeBefore(navigation.id)
        send(CaptureEvent.EffectFailed(navigation.id, "transaction rejected"))
        assertTrue(model.uiState.value.state is CaptureState.Error)
        assertFalse((command.effect as CaptureEffect.TakePhoto).file.exists())
    }

    @Test fun effectsRequireOrderedAcknowledgementAndOldViewCannotAcknowledgeNewEffects() = runTest(dispatcher) {
        requestPermission()
        val queue = model.uiState.value.effects
        send(CaptureEvent.EffectHandled(queue.last().id))
        assertEquals(queue, model.uiState.value.effects)
        acknowledgeAll()
        assertTrue(model.uiState.value.effects.isEmpty())
        val oldGeneration = generation
        destroyAndReattach()
        gallery()
        val next = model.uiState.value.effects.single()
        model.onEvent(oldGeneration, CaptureEvent.EffectHandled(next.id))
        dispatcher.scheduler.runCurrent()
        assertEquals(next, model.uiState.value.effects.single())
    }

    @Test fun cameraSetupFailureAndPickerLaunchFailureAreRecoverable() = runTest(dispatcher) {
        send(CaptureEvent.CameraFailed("setup failed"))
        assertTrue(model.uiState.value.controlsEnabled)
        assertFalse(model.uiState.value.cameraReady)
        gallery()
        val picker = model.uiState.value.effects.single()
        send(CaptureEvent.EffectFailed(picker.id, "unavailable"))
        assertTrue(model.uiState.value.state is CaptureState.Error)
        assertTrue(model.uiState.value.controlsEnabled)
    }

    @Test fun cancelledRecognizerReturnsIdleAndReleasesImage() = runTest(dispatcher) {
        recognize = { throw CancellationException("cancelled") }
        val command = capture()
        complete(command)
        assertState(CaptureState.Idle)
        assertFalse((command.effect as CaptureEffect.TakePhoto).file.exists())
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun cancelledSaveReturnsIdleButRetainsImage() = runTest(dispatcher) {
        save = { throw CancellationException("uncertain commit") }
        val command = capture()
        complete(command)
        assertState(CaptureState.Idle)
        assertTrue((command.effect as CaptureEffect.TakePhoto).file.exists())
    }

    @Test fun switchingCameraUpdatesFacingWithoutBeginningCapture() = runTest(dispatcher) {
        send(CaptureEvent.SwitchCameraTapped)
        assertFalse(model.uiState.value.cameraReady)
        assertTrue(model.uiState.value.effects.any { it.effect == CaptureEffect.SwitchCamera })
        acknowledgeAll()
        send(CaptureEvent.CameraReady(false))
        assertFalse(model.uiState.value.isBackCamera)
        assertState(CaptureState.Idle)
        assertTrue(model.uiState.value.effects.isEmpty())
    }

    @Test fun unacknowledgedSelectionIsReleasedOnViewDestruction() = runTest(dispatcher) {
        recognize = { outcome("甲乙", "丙丁") }
        val command = capture()
        complete(command)
        assertState(CaptureState.WaitingForTextSelection)
        send(CaptureEvent.ViewDestroyed)
        assertFalse((command.effect as CaptureEffect.TakePhoto).file.exists())
    }

    @Test fun previewRebindingFailureDoesNotCancelInFlightRecognition() = runTest(dispatcher) {
        val gate = CompletableDeferred<OcrOutcome>()
        recognize = { gate.await() }
        val command = capture()
        complete(command)
        send(CaptureEvent.Paused)
        send(CaptureEvent.Resumed)
        send(CaptureEvent.CameraFailed("binding failed"))
        assertState(CaptureState.RecognizingText)
        gate.complete(outcome("甲乙", "丙丁"))
        dispatcher.scheduler.runCurrent()
        assertState(CaptureState.WaitingForTextSelection)
        assertTrue((command.effect as CaptureEffect.TakePhoto).file.exists())
    }

    private fun send(event: CaptureEvent) {
        model.onEvent(generation, event)
        dispatcher.scheduler.runCurrent()
    }
    private fun assertState(expected: CaptureState) = assertEquals(expected, model.uiState.value.state)
    private inline fun <reified T : CaptureEffect> effect() =
        model.uiState.value.effects.single { it.effect is T }
    private fun requestPermission(): Long {
        send(CaptureEvent.CaptureTapped(false))
        assertState(CaptureState.RequestingLocationPermission)
        return checkNotNull(effect<CaptureEffect.RequestLocationPermission>().operationId)
    }
    private fun gallery(): Long {
        send(CaptureEvent.GalleryTapped)
        assertState(CaptureState.SelectingGalleryImage)
        return checkNotNull(effect<CaptureEffect.OpenGallery>().operationId)
    }
    private fun capture(): CaptureEffectEnvelope {
        send(CaptureEvent.CaptureTapped(true))
        assertState(CaptureState.Capturing)
        return effect<CaptureEffect.TakePhoto>()
    }
    private fun complete(command: CaptureEffectEnvelope) {
        acknowledgeAll()
        val file = (command.effect as CaptureEffect.TakePhoto).file
        file.writeText("original")
        send(CaptureEvent.CameraSaved(checkNotNull(command.operationId), file))
    }
    private fun acknowledgeAll() {
        while (model.uiState.value.effects.isNotEmpty()) {
            send(CaptureEvent.EffectHandled(model.uiState.value.effects.first().id))
        }
    }
    private fun acknowledgeBefore(id: Long) {
        while (model.uiState.value.effects.first().id != id) {
            send(CaptureEvent.EffectHandled(model.uiState.value.effects.first().id))
        }
    }
    private fun destroyAndReattach() {
        send(CaptureEvent.ViewDestroyed)
        generation = model.attachView()
        send(CaptureEvent.Resumed)
        send(CaptureEvent.CameraReady(true))
    }
    private fun outcome(vararg lines: String): OcrOutcome {
        val pass = OcrPass(lines.map { OcrLine(it, 0.95f) })
        return OcrOutcome(pass, pass.confidence, false)
    }
}
