
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
    private var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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
                android.util.Log.e("CameraManager", "Camera setup failed: ${exception.message}", exception)
                android.widget.Toast.makeText(context, "Camera setup failed: ${exception.message}", android.widget.Toast.LENGTH_LONG).show()
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

        // Use the current camera selector
        val cameraSelector = currentCameraSelector

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
            android.util.Log.e("CameraManager", "Camera binding failed: ${exception.message}", exception)
            android.widget.Toast.makeText(context, "Camera binding failed: ${exception.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Switch between front and back camera.
     */
    fun switchCamera(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onImageCaptureReady: (ImageCapture) -> Unit
    ) {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // Switch camera
        
        // Restart camera with new selector
        startCamera(context, previewView, lifecycleOwner, onImageCaptureReady)
    }
    
    /**
     * Check if camera is ready for capture.
     */
    fun isCameraReady(): Boolean {
        return imageCapture != null && cameraProvider != null
    }
    
    /**
     * Get current camera facing direction.
     */
    fun isBackCamera(): Boolean {
        return currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA
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
