package com.karen_yao.chinesetravel.features.capture.ui

import java.io.File

internal sealed interface CaptureState {
    data object Idle : CaptureState
    data object RequestingLocationPermission : CaptureState
    data object GettingLocation : CaptureState
    data object WaitingForCamera : CaptureState
    data object Capturing : CaptureState
    data object SelectingGalleryImage : CaptureState
    data object ImportingImage : CaptureState
    data object RecognizingText : CaptureState
    data object WaitingForTextSelection : CaptureState
    data object AwaitingNoTextChoice : CaptureState
    data object Saving : CaptureState
    data class Error(val stage: String, val message: String) : CaptureState
}

internal data class CaptureUiState(
    val state: CaptureState = CaptureState.Idle,
    val cameraReady: Boolean = false,
    val isBackCamera: Boolean = true,
    val effects: List<CaptureEffectEnvelope> = emptyList()
) {
    val controlsEnabled: Boolean
        get() = state == CaptureState.Idle || state is CaptureState.Error
}

internal sealed interface CaptureEvent {
    data object Resumed : CaptureEvent
    data object Paused : CaptureEvent
    data object ViewDestroyed : CaptureEvent
    data class CaptureTapped(val hasLocationPermission: Boolean) : CaptureEvent
    data object GalleryTapped : CaptureEvent
    data class PermissionResult(val operationId: Long, val granted: Boolean) : CaptureEvent
    data class GalleryResult(val operationId: Long, val uri: String?) : CaptureEvent
    data class CameraReady(val isBackCamera: Boolean, val message: String? = null) : CaptureEvent
    data object SwitchCameraTapped : CaptureEvent
    data class CameraFailed(val message: String) : CaptureEvent
    data class CameraSaved(val operationId: Long, val file: File) : CaptureEvent
    data class CameraCaptureFailed(val operationId: Long, val file: File, val error: CaptureCameraError) : CaptureEvent
    data class NoTextChoice(val operationId: Long, val choice: NoTextAction) : CaptureEvent
    data class EffectHandled(val effectId: Long) : CaptureEvent
    data class EffectFailed(val effectId: Long, val message: String) : CaptureEvent
}

internal enum class NoTextAction { TRY_AGAIN, GALLERY, HOME, DISMISS }

internal sealed interface CaptureEffect {
    data class Message(val text: String) : CaptureEffect
    data object RequestLocationPermission : CaptureEffect
    data object OpenGallery : CaptureEffect
    data object RestartCamera : CaptureEffect
    data object SwitchCamera : CaptureEffect
    data class TakePhoto(val file: File, val location: CaptureLocation?) : CaptureEffect
    data class OpenTextSelection(val lines: List<String>, val file: File) : CaptureEffect
    data object ShowNoTextDialog : CaptureEffect
    data object NavigateBack : CaptureEffect
    data object NavigateHome : CaptureEffect
}

internal data class CaptureEffectEnvelope(
    val id: Long,
    val operationId: Long?,
    val viewGeneration: Long,
    val effect: CaptureEffect
)

/** Platform-free snapshot of the values CameraX writes as location metadata. */
internal data class CaptureLocation(
    val latitude: Double,
    val longitude: Double,
    val provider: String = "capture",
    val time: Long = 0,
    val altitude: Double? = null,
    val speed: Float? = null,
    val accuracy: Float? = null
)
