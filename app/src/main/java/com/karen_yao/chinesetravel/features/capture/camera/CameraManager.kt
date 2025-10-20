
package com.karen_yao.chinesetravel.features.capture.camera

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

/**
 * Manages camera functionality for photo capture.
 * Handles camera initialization, preview setup, and lifecycle management.
 */
class CameraManager {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null

    /**
     * Start the camera with preview.
     * 
     * @param context Application context
     * @param previewView The preview view to display camera feed
     * @param lifecycleOwner The lifecycle owner for camera binding
     * @param onImageCaptureReady Callback when image capture is ready
     */
    fun startCamera(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onImageCaptureReady: (ImageCapture) -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                setupCamera(context, previewView, lifecycleOwner, onImageCaptureReady)
            } catch (exception: Exception) {
                // Handle camera setup error
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupCamera(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onImageCaptureReady: (ImageCapture) -> Unit
    ) {
        val cameraProvider = this.cameraProvider ?: return

        // Create preview
        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // Create image capture
        imageCapture = ImageCapture.Builder().build()

        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )

            // Notify that image capture is ready
            imageCapture?.let { onImageCaptureReady(it) }
        } catch (exception: Exception) {
            // Handle camera binding error
        }
    }

    /**
     * Stop the camera and release resources.
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        imageCapture = null
    }
}
