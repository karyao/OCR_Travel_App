package com.karen_yao.chinesetravel.features.capture.ui

import android.content.Context
import androidx.annotation.MainThread
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Owns preview binding and callback validity for one Fragment view, not photo operations. */
@MainThread
internal class CaptureCameraSession(
    private val camera: CaptureCamera,
    private val permissions: CapturePermissionChecker,
    private val context: Context,
    private val preview: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onEvent: (CaptureEvent) -> Unit
) {
    private var active = false
    private var disposed = false
    private var bindingGeneration = 0L

    fun resume() {
        if (disposed) return
        // The view lifecycle reaches RESUMED after Fragment.onResume returns. A camera
        // implementation may report readiness synchronously during that callback.
        active = true
        start()
    }

    fun start() = bind(switch = false)

    fun switchCamera() = bind(switch = true)

    /** Invalidate callbacks, let the host cancel its operation, then stop the camera. */
    fun pause(beforeStop: () -> Unit = {}) {
        val wasActive = active
        active = false
        bindingGeneration++
        beforeStop()
        if (wasActive) camera.stop()
    }

    /** Idempotent; a paused session has already stopped its camera. */
    fun dispose() {
        if (disposed) return
        disposed = true
        pause()
    }

    private fun bind(switch: Boolean) {
        if (!active || disposed || lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val bindingId = ++bindingGeneration
        if (!permissions.hasCameraPermission(context)) return
        if (!permissions.isCameraAvailable(context)) {
            onEvent(CaptureEvent.CameraFailed("Camera not available on this device"))
            return
        }
        fun isCurrent() = active && !disposed && bindingId == bindingGeneration &&
            lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED
        val onError: (String) -> Unit = {
            if (isCurrent()) onEvent(CaptureEvent.CameraFailed(it))
        }
        val onReady: () -> Unit = {
            if (isCurrent()) {
                val back = camera.isBackCamera()
                onEvent(CaptureEvent.CameraReady(back, if (switch)
                    "Switched to ${if (back) "back" else "front"} camera"
                    else null))
            }
        }
        if (switch) camera.switch(context, preview, lifecycleOwner, onError, onReady)
        else camera.start(context, preview, lifecycleOwner, onError, onReady)
    }
}
