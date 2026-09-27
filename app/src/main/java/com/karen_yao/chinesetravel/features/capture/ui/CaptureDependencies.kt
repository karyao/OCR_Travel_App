package com.karen_yao.chinesetravel.features.capture.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.capture.camera.CameraManager
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import com.karen_yao.chinesetravel.features.capture.camera.OcrOutcome
import com.karen_yao.chinesetravel.features.capture.camera.OcrPipeline
import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor

internal interface CaptureDependenciesOwner {
    fun createCaptureDependencies(): CaptureDependencies
}

internal data class CaptureDependencies(
    val repository: TravelRepository,
    val permissions: CapturePermissionChecker,
    val camera: CaptureCamera,
    val createWorkflow: () -> CaptureWorkflowDependencies
)

internal interface CapturePermissionChecker {
    fun hasCameraPermission(context: Context): Boolean
    fun hasLocationPermission(context: Context): Boolean
    fun isCameraAvailable(context: Context): Boolean
}

internal interface CaptureCamera {
    fun start(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onError: (String) -> Unit,
        onReady: () -> Unit
    )

    fun switch(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onError: (String) -> Unit,
        onReady: () -> Unit
    )

    fun takePhoto(
        file: File,
        location: Location?,
        executor: Executor,
        onSaved: () -> Unit,
        onError: (CaptureCameraError) -> Unit
    )

    fun stop()
    fun isReady(): Boolean
    fun isBackCamera(): Boolean
}

internal fun productionCaptureDependencies(
    context: Context,
    repository: TravelRepository
): CaptureDependencies {
    val applicationContext = context.applicationContext
    return CaptureDependencies(
        repository = repository,
        permissions = AndroidCapturePermissionChecker,
        camera = CameraManagerCaptureCamera(),
        createWorkflow = {
            val imageProcessor = ImageProcessor()
            CaptureWorkflowDependencies(
                filesRoot = applicationContext.filesDir,
                cacheDirectory = applicationContext.cacheDir,
                location = CaptureLocationSource {
                    when (val result = DeviceLocationProvider.getCurrentLocation(applicationContext)) {
                        is DeviceLocationProvider.Result.Success -> CaptureLocationResult.Success(
                            result.location.toCaptureLocation()
                        )
                        is DeviceLocationProvider.Result.Unavailable ->
                            CaptureLocationResult.Unavailable(result.reason)
                    }
                },
                recognizer = OcrPipelineCaptureTextRecognizer(OcrPipeline(applicationContext, imageProcessor)),
                galleryImporter = CaptureGallerySource { uri ->
                    GalleryImageImporter().importImage(File(applicationContext.filesDir, "imports")) {
                        applicationContext.contentResolver.openInputStream(Uri.parse(uri))
                    }
                },
                saver = AndroidCaptureSaver(applicationContext, imageProcessor, CapturePersistence(repository))
            )
        }
    )
}

private object AndroidCapturePermissionChecker : CapturePermissionChecker {
    override fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun hasLocationPermission(context: Context): Boolean =
        DeviceLocationProvider.hasLocationPermission(context)

    override fun isCameraAvailable(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}

private class CameraManagerCaptureCamera(
    private val manager: CameraManager = CameraManager()
) : CaptureCamera {
    private var imageCapture: ImageCapture? = null

    override fun start(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onError: (String) -> Unit,
        onReady: () -> Unit
    ) {
        manager.startCamera(context, previewView, lifecycleOwner, onError) {
            imageCapture = it
            onReady()
        }
    }

    override fun switch(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onError: (String) -> Unit,
        onReady: () -> Unit
    ) {
        manager.switchCamera(context, previewView, lifecycleOwner, onError) {
            imageCapture = it
            onReady()
        }
    }

    override fun takePhoto(
        file: File,
        location: Location?,
        executor: Executor,
        onSaved: () -> Unit,
        onError: (CaptureCameraError) -> Unit
    ) {
        val capture = checkNotNull(imageCapture) { "Camera is no longer ready" }
        val metadata = ImageCapture.Metadata().apply { this.location = location }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(metadata)
            .build()
        capture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved()

                override fun onError(exception: ImageCaptureException) {
                    onError(
                        CaptureCameraError(
                            message = exception.message,
                            cameraClosed = exception.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED
                        )
                    )
                }
            }
        )
    }

    override fun stop() {
        manager.stopCamera()
        imageCapture = null
    }

    override fun isReady(): Boolean = manager.isCameraReady() && imageCapture != null
    override fun isBackCamera(): Boolean = manager.isBackCamera()
}

private class OcrPipelineCaptureTextRecognizer(
    private val pipeline: OcrPipeline
) : CaptureTextRecognizer {
    override suspend fun recognize(originalFile: File, cacheDirectory: File): OcrOutcome =
        pipeline.recognize(originalFile, cacheDirectory)

    override fun close() = pipeline.close()
}

private class AndroidCaptureSaver(
    private val context: Context,
    private val imageProcessor: ImageProcessor,
    private val persistence: CapturePersistence
) : CaptureSaver {
    override suspend fun save(
        chineseText: String,
        file: File
    ): Int {
        val pinyin = if (chineseText.isNotBlank()) PinyinUtils.toPinyin(chineseText) else ""
        val location = withContext(Dispatchers.IO) {
            imageProcessor.extractLocationFromFile(file)
        }
        val address = location?.let { (latitude, longitude) ->
            reverseGeocode(context.applicationContext, latitude, longitude)
        }
        return persistence.saveAndCount(
            chineseText = chineseText,
            pinyinText = pinyin,
            latitude = location?.first,
            longitude = location?.second,
            address = address,
            imagePath = file.absolutePath
        )
    }

    private suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }
}

private fun Location.toCaptureLocation() = CaptureLocation(
    latitude, longitude, provider ?: "capture", time,
    altitude.takeIf { hasAltitude() }, speed.takeIf { hasSpeed() }, accuracy.takeIf { hasAccuracy() }
)

internal fun CaptureLocation.toAndroidLocation() = Location(provider).also {
    it.latitude = latitude
    it.longitude = longitude
    it.time = time
    altitude?.let { value -> it.altitude = value }
    speed?.let { value -> it.speed = value }
    accuracy?.let { value -> it.accuracy = value }
}
