package com.karen_yao.chinesetravel.features.capture.ui

import com.karen_yao.chinesetravel.features.capture.camera.OcrOutcome
import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Application-scoped services only. No Android UI objects cross this boundary. */
internal data class CaptureWorkflowDependencies(
    val filesRoot: File,
    val location: CaptureLocationSource,
    val recognizer: CaptureTextRecognizer,
    val galleryImporter: CaptureGallerySource,
    val saver: CaptureSaver,
    val cleanupDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val createCameraFile: () -> File = {
        val directory = File(filesRoot, "captures")
        check(directory.exists() || directory.mkdirs()) { "Could not create capture directory" }
        File.createTempFile("snap_", ".jpg", directory)
    }
)

internal data class CaptureCameraError(
    val message: String?,
    val cameraClosed: Boolean
)

internal sealed interface CaptureLocationResult {
    data class Success(val location: CaptureLocation) : CaptureLocationResult
    data class Unavailable(val reason: DeviceLocationProvider.FailureReason) : CaptureLocationResult
}

internal fun interface CaptureLocationSource {
    suspend fun currentLocation(): CaptureLocationResult
}

internal fun interface CaptureGallerySource {
    suspend fun importImage(uri: String): File
}

internal interface CaptureTextRecognizer : Closeable {
    suspend fun recognize(originalFile: File): OcrOutcome
}

internal typealias CaptureSaver = com.karen_yao.chinesetravel.core.workflow.CapturedPlaceSaver
