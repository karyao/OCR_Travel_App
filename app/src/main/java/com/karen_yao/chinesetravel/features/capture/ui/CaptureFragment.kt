
package com.karen_yao.chinesetravel.features.capture.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.capture.camera.CameraManager
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment
import com.karen_yao.chinesetravel.shared.extensions.repo
import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Camera fragment for capturing and processing Chinese text.
 * Features: Camera preview, photo capture, gallery selection, OCR processing.
 */
class CaptureFragment : Fragment(R.layout.fragment_capture) {

    private var imageCapture: ImageCapture? = null
    private val viewModel by lazy { 
        ViewModelProvider(this, CaptureViewModelFactory(repo()))[CaptureViewModel::class.java] 
    }
    private val cameraManager = CameraManager()
    private val imageProcessor = ImageProcessor()
    private var isBackCamera = true
    private var captureJob: Job? = null
    private var imageProcessingJob: Job? = null
    private var isGallerySelectionPending = false
    private var managedFilesRoot: File? = null
    private var activeCaptureId: Long? = null
    private var nextCaptureId = 0L
    private var pendingLocationPermissionCaptureId: Long? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        isGallerySelectionPending = false
        if (uri != null && view != null) {
            processGalleryUri(uri)
        } else {
            updateCaptureControls()
            if (uri == null) showMessage("No image selected")
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                view?.findViewById<PreviewView>(R.id.previewView)?.let(::startCamera)
            } else {
                showMessage("Camera permission is required to take photos")
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val captureId = pendingLocationPermissionCaptureId
            pendingLocationPermissionCaptureId = null
            if (captureId == null || activeCaptureId != captureId || !isAdded || view == null) {
                return@registerForActivityResult
            }

            if (results.values.any { it }) {
                acquireLocationAndCapture(captureId)
            } else {
                capturePhoto(captureId, null, "Location permission was not granted")
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        managedFilesRoot = requireContext().filesDir
        setupHeader(view)
        setupButtons(view)
        requestCameraPermissionAndStart(view.findViewById(R.id.previewView))
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure camera is running when fragment resumes
        if (hasCameraPermission() && !cameraManager.isCameraReady()) {
            val previewView = view?.findViewById<PreviewView>(R.id.previewView)
            if (previewView != null) {
                Log.d("CaptureFragment", "Camera not ready, restarting...")
                startCamera(previewView)
            }
        }
    }
    
    override fun onPause() {
        cancelPendingCapture()
        cameraManager.stopCamera()
        imageCapture = null
        super.onPause()
    }

    override fun onDestroyView() {
        cancelPendingCapture()
        isGallerySelectionPending = false
        imageProcessingJob?.cancel()
        super.onDestroyView()
    }
    
    private fun setupHeader(view: View) {
        val headerLayout = view.findViewById<View>(R.id.headerLayout)
        val backButton = headerLayout.findViewById<Button>(R.id.btnBack)
        val titleText = headerLayout.findViewById<android.widget.TextView>(R.id.tvHeaderTitle)
        val rightText = headerLayout.findViewById<android.widget.TextView>(R.id.tvHeaderRight)
        
        // Set title and hide right text
        titleText.text = "📸 Capture Chinese Text"
        rightText.visibility = android.view.View.GONE
        
        // Set up back button
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btnShoot).setOnClickListener { takePhoto() }
        view.findViewById<Button>(R.id.btnGallery).setOnClickListener { launchGalleryPicker() }
        view.findViewById<Button>(R.id.btnSwitchCamera).setOnClickListener { switchCamera() }
        
        // Update switch button text based on current camera
        updateSwitchButtonText(view)
    }

    private fun launchGalleryPicker() {
        if (isCaptureBusy()) return

        isGallerySelectionPending = true
        updateCaptureControls()
        try {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (exception: Exception) {
            isGallerySelectionPending = false
            updateCaptureControls()
            showMessage("Could not open the photo picker: ${exception.message}")
        }
    }
    
    private fun updateSwitchButtonText(view: View) {
        val switchButton = view.findViewById<Button>(R.id.btnSwitchCamera)
        val cameraIcon = if (isBackCamera) "📷" else "🤳"
        switchButton.text = cameraIcon
    }

    private fun requestCameraPermissionAndStart(previewView: PreviewView) {
        if (hasCameraPermission()) {
            startCamera(previewView)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera(previewView: PreviewView) {
        if (!hasCameraPermission()) return
        if (!isCameraAvailable()) {
            showMessage("Camera not available on this device")
            return
        }
        
        cameraManager.startCamera(requireContext(), previewView, viewLifecycleOwner) { imageCapture ->
            this.imageCapture = imageCapture
            showMessage("Camera ready! Point at Chinese text")
        }
        
        setupCameraGestures(previewView)
    }
    
    private fun setupCameraGestures(previewView: PreviewView) {
        previewView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 300) { // Double tap within 300ms
                    switchCamera()
                }
                lastTapTime = currentTime
            }
            false
        }
    }
    
    private var lastTapTime = 0L
    
    // Helper methods
    private fun showMessage(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }
    
    private fun isCameraAvailable(): Boolean {
        return requireContext().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }
    
    private fun restartCamera() {
        if (!hasCameraPermission()) return
        val previewView = view?.findViewById<PreviewView>(R.id.previewView)
        if (previewView != null) {
            startCamera(previewView)
        }
    }
    
    private fun switchCamera() {
        if (isCaptureBusy()) return
        val previewView = view?.findViewById<PreviewView>(R.id.previewView)
        if (previewView != null) {
            showMessage("Switching camera...")
            
            cameraManager.switchCamera(
                requireContext(),
                previewView,
                viewLifecycleOwner
            ) { imageCapture ->
                this.imageCapture = imageCapture
                isBackCamera = cameraManager.isBackCamera()
                val cameraType = if (isBackCamera) "back" else "front"
                showMessage("Switched to $cameraType camera")
                view?.let { updateSwitchButtonText(it) }
            }
        }
    }

    private fun takePhoto() {
        if (isCaptureBusy()) return
        if (!cameraManager.isCameraReady()) {
            showMessage("Camera not ready. Please wait...")
            return
        }
        
        val captureId = ++nextCaptureId
        activeCaptureId = captureId
        updateCaptureControls()

        if (DeviceLocationProvider.hasLocationPermission(requireContext())) {
            acquireLocationAndCapture(captureId)
        } else {
            pendingLocationPermissionCaptureId = captureId
            showMessage("Location is optional. Choose whether to attach it to this photo.")
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun acquireLocationAndCapture(captureId: Long) {
        showMessage("Getting location…")
        captureJob = viewLifecycleOwner.lifecycleScope.launch {
            when (val result = DeviceLocationProvider.getCurrentLocation(requireContext())) {
                is DeviceLocationProvider.Result.Success -> {
                    capturePhoto(captureId, result.location, null)
                }
                is DeviceLocationProvider.Result.Unavailable -> {
                    capturePhoto(captureId, null, locationFailureMessage(result.reason))
                }
            }
        }
    }

    private fun capturePhoto(captureId: Long, location: Location?, locationFailure: String?) {
        if (!canContinueCapture(captureId)) {
            finishCapture(captureId)
            return
        }

        val capture = imageCapture ?: run {
            showMessage("Camera is no longer ready")
            finishCapture(captureId)
            return
        }
        val captureDirectory = File(requireContext().filesDir, CAPTURE_DIRECTORY).apply { mkdirs() }
        val file = File(captureDirectory, "snap_${System.currentTimeMillis()}.jpg")
        val metadata = ImageCapture.Metadata().apply { this.location = location }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(metadata)
            .build()

        showMessage("Capturing photo…")
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    if (activeCaptureId != captureId) return
                    showMessage("Capture failed: ${exception.message}")
                    finishCapture(captureId)
                    if (exception.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED) {
                        restartCamera()
                    }
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (!canContinueCapture(captureId)) {
                        file.delete()
                        finishCapture(captureId)
                        return
                    }
                    if (location != null) {
                        showMessage("Photo captured with location. Processing…")
                    } else {
                        showMessage("Photo captured without location. ${locationFailure.orEmpty()}")
                    }
                    processCapturedFile(file)
                    finishCapture(captureId)
                }
            }
        )
    }

    private fun canContinueCapture(captureId: Long): Boolean =
        activeCaptureId == captureId &&
            view != null &&
            viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            hasCameraPermission() &&
            cameraManager.isCameraReady()

    private fun finishCapture(captureId: Long) {
        if (activeCaptureId != captureId) return
        captureJob = null
        activeCaptureId = null
        pendingLocationPermissionCaptureId = null
        updateCaptureControls()
    }

    private fun cancelPendingCapture() {
        captureJob?.cancel()
        captureJob = null
        activeCaptureId = null
        pendingLocationPermissionCaptureId = null
        updateCaptureControls()
    }

    private fun updateCaptureControls() {
        val enabled = !isCaptureBusy()
        view?.findViewById<Button>(R.id.btnShoot)?.isEnabled = enabled
        view?.findViewById<Button>(R.id.btnGallery)?.isEnabled = enabled
        view?.findViewById<Button>(R.id.btnSwitchCamera)?.isEnabled = enabled
    }

    private fun isCaptureBusy(): Boolean =
        activeCaptureId != null || isGallerySelectionPending || imageProcessingJob?.isActive == true

    private fun locationFailureMessage(reason: DeviceLocationProvider.FailureReason): String =
        when (reason) {
            DeviceLocationProvider.FailureReason.PERMISSION_DENIED -> "Location permission was not granted."
            DeviceLocationProvider.FailureReason.LOCATION_DISABLED -> "Location services are disabled."
            DeviceLocationProvider.FailureReason.TIMED_OUT -> "The location request timed out."
            DeviceLocationProvider.FailureReason.INVALID_OR_STALE -> "No recent valid location was available."
            DeviceLocationProvider.FailureReason.REQUEST_FAILED -> "The location request failed."
        }

    private fun processCapturedFile(file: File) {
        startImageProcessing(file, "camera") { _ -> file }
    }

    private fun processGalleryUri(uri: Uri) {
        startImageProcessing(null, "gallery") { applicationContext ->
            importGalleryImage(applicationContext, uri)
        }
    }

    private fun startImageProcessing(
        initialFile: File?,
        source: String,
        obtainOriginalFile: suspend (Context) -> File
    ) {
        if (imageProcessingJob?.isActive == true) {
            initialFile?.delete()
            return
        }

        val applicationContext = context?.applicationContext ?: run {
            initialFile?.delete()
            return
        }
        val filesRoot = managedFilesRoot ?: applicationContext.filesDir
        val cacheDirectory = applicationContext.cacheDir

        imageProcessingJob = viewLifecycleOwner.lifecycleScope.launch {
            val runningJob = coroutineContext[Job]
            var originalFile = initialFile
            var temporaryFile: File? = null
            var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
            var stage = if (initialFile == null) {
                ImageProcessingStage.IMPORTING
            } else {
                ImageProcessingStage.PREPARING
            }

            updateCaptureControls()
            try {
                originalFile = obtainOriginalFile(applicationContext)
                stage = ImageProcessingStage.PREPARING
                val preprocessedFile = imageProcessor.preprocessImageForOCR(
                    checkNotNull(originalFile),
                    cacheDirectory
                )
                temporaryFile = preprocessedFile
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(applicationContext, Uri.fromFile(preprocessedFile))
                }

                stage = ImageProcessingStage.RECOGNIZING
                showMessage("Running OCR ($source)...")
                recognizer = TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build()
                )
                val result = recognizer.process(image).await()
                val allLines = result.text.lines()
                    .map(String::trim)
                    .filter(String::isNotEmpty)

                when {
                    allLines.isEmpty() -> showNoTextDetectedDialog(
                        checkNotNull(originalFile).absolutePath
                    )
                    allLines.size > 1 -> showTextSelectionScreen(
                        allLines,
                        checkNotNull(originalFile).absolutePath
                    )
                    allLines.first().length >= 2 -> {
                        // From this point onward the database may commit even if cancellation is
                        // delivered before the result reaches this fragment. Preserve the image;
                        // a failed save can leave an orphan, but never a row with a deleted file.
                        stage = ImageProcessingStage.SAVING
                        saveSelectedText(allLines.first(), checkNotNull(originalFile))
                    }
                    else -> {
                        showMessage("Text too short, please try again")
                        deleteManagedImage(checkNotNull(originalFile), filesRoot)
                    }
                }
            } catch (exception: CancellationException) {
                if (stage != ImageProcessingStage.SAVING) {
                    originalFile?.let { deleteManagedImage(it, filesRoot) }
                }
                throw exception
            } catch (exception: Exception) {
                if (stage != ImageProcessingStage.SAVING) {
                    originalFile?.let { deleteManagedImage(it, filesRoot) }
                }
                showMessage(stage.errorMessage(exception))
            } finally {
                recognizer?.close()
                temporaryFile
                    ?.takeIf { it != originalFile }
                    ?.let { deleteFile(it) }
                if (imageProcessingJob === runningJob) imageProcessingJob = null
                updateCaptureControls()
            }
        }
        updateCaptureControls()
    }

    private suspend fun importGalleryImage(
        applicationContext: Context,
        uri: Uri
    ): File = withContext(Dispatchers.IO) {
        val importDirectory = File(applicationContext.filesDir, IMPORT_DIRECTORY)
        check(importDirectory.exists() || importDirectory.mkdirs()) {
            "Could not create the gallery import directory"
        }
        val file = File.createTempFile("gal_", ".jpg", importDirectory)

        try {
            applicationContext.contentResolver.openInputStream(uri).use { sourceStream ->
                checkNotNull(sourceStream) { "The selected image could not be opened" }
                file.outputStream().use { destination ->
                    sourceStream.copyTo(destination)
                }
            }
            check(file.length() > 0L) { "The selected image was empty" }
            file
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
    }

    // REMOVED: Complex Chinese text extraction functions that were causing issues
    // Now using simplified approach - let ML Kit do its job and trust the results

    private fun showTextSelectionScreen(chineseLines: List<String>, imagePath: String) {
        val textSelectionFragment = TextSelectionFragment.newInstance(
            detectedTexts = chineseLines,
            imagePath = imagePath,
            selectedText = "",
            deleteImageIfUnsaved = true
        )
        
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, textSelectionFragment)
            .addToBackStack(null)
            .commit()
    }

    private suspend fun saveSelectedText(chineseText: String, file: File) {
        val pinyin = if (chineseText.isNotBlank()) PinyinUtils.toPinyin(chineseText) else ""

        val location = withContext(Dispatchers.IO) {
            imageProcessor.extractLocationFromFile(file)
        }
        val address = location?.let { (lat, lng) -> reverseGeocode(lat, lng) }
        val totalCount = viewModel.saveAndCount(
            chineseText,
            pinyin,
            location?.first,
            location?.second,
            address,
            file.absolutePath
        )

        if (view != null) {
            context?.let {
                Toast.makeText(it, "Saved. Total rows: $totalCount", Toast.LENGTH_SHORT).show()
            }
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        val applicationContext = context?.applicationContext ?: return null
        return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            null
        }
        }
    }
    
    // REMOVED: Overly strict quality check that was rejecting valid Chinese text
    // Now using simple length check (>= 2 characters) instead
    
    /**
     * Shows a dialog when poor quality Chinese text is detected, suggesting the user try a better image.
     */
    private fun showPoorQualityTextDialog(detectedText: String, imagePath: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Poor Text Quality")
            .setMessage("We detected some Chinese text, but it might not be clear enough:\n\n" +
                    "\"$detectedText\"\n\n" +
                    "This could be due to:\n" +
                    "• Blurry or unclear text\n" +
                    "• Poor lighting\n" +
                    "• Text too small or far away\n" +
                    "• Background interference\n\n" +
                    "Would you like to try a more focused photo?")
            .setPositiveButton("📸 Try Better Photo") { _, _ ->
                deleteUnreferencedImage(imagePath)
                // Go back to capture screen to try again
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton("📁 Choose from Gallery") { _, _ ->
                deleteUnreferencedImage(imagePath)
                launchGalleryPicker()
            }
            .setNeutralButton("✅ Use This Text") { _, _ ->
                startSelectedTextSave(detectedText, File(imagePath))
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Shows a dialog when no text is detected, suggesting the user try a more cropped image.
     */
    private fun showNoTextDetectedDialog(imagePath: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("🔍 No Text Detected")
            .setMessage("We couldn't find any text in this image. This often happens when:\n\n" +
                    "• The image is too wide or includes too much background\n" +
                    "• The text is too small or blurry\n" +
                    "• The lighting is poor\n\n" +
                    "Try taking a more focused photo with just the Chinese text visible.")
            .setPositiveButton("📸 Try Again") { _, _ ->
                deleteUnreferencedImage(imagePath)
                // Go back to capture screen to try again
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton("📁 Choose from Gallery") { _, _ ->
                deleteUnreferencedImage(imagePath)
                launchGalleryPicker()
            }
            .setNeutralButton("🏠 Back to Home") { _, _ ->
                deleteUnreferencedImage(imagePath)
                // Navigate directly to home to avoid loop
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, com.karen_yao.chinesetravel.features.home.ui.HomeFragment())
                    .commit()
            }
            .create()
        
        dialog.show()
    }

    private suspend fun deleteManagedImage(file: File, filesRoot: File) {
        withContext(NonCancellable + Dispatchers.IO) {
            if (isManagedImage(file, filesRoot)) file.delete()
        }
    }

    private suspend fun deleteFile(file: File) {
        withContext(NonCancellable + Dispatchers.IO) {
            file.delete()
        }
    }

    private fun deleteUnreferencedImage(imagePath: String) {
        val filesRoot = managedFilesRoot ?: return
        val file = File(imagePath)
        if (isManagedImage(file, filesRoot)) file.delete()
    }

    private fun isManagedImage(file: File, filesRoot: File): Boolean {
        val canonicalRoot = runCatching { filesRoot.canonicalFile }.getOrNull() ?: return false
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return candidate.path.startsWith(canonicalRoot.path + File.separator)
    }

    private fun startSelectedTextSave(chineseText: String, file: File) {
        if (imageProcessingJob?.isActive == true) return
        if (managedFilesRoot == null) return

        // Saving may commit before cancellation is observed, so this path deliberately retains
        // the image on failure rather than risk invalidating a persisted database reference.
        imageProcessingJob = viewLifecycleOwner.lifecycleScope.launch {
            val runningJob = coroutineContext[Job]
            updateCaptureControls()
            try {
                saveSelectedText(chineseText, file)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showMessage("Save failed: ${exception.message}")
            } finally {
                if (imageProcessingJob === runningJob) imageProcessingJob = null
                updateCaptureControls()
            }
        }
        updateCaptureControls()
    }

    private enum class ImageProcessingStage {
        IMPORTING,
        PREPARING,
        RECOGNIZING,
        SAVING;

        fun errorMessage(exception: Exception): String = when (this) {
            IMPORTING -> "Could not import image: ${exception.message}"
            PREPARING -> "Could not process image: ${exception.message}"
            RECOGNIZING -> "OCR failed: ${exception.message}"
            SAVING -> "Save failed: ${exception.message}"
        }
    }

    private companion object {
        const val CAPTURE_DIRECTORY = "captures"
        const val IMPORT_DIRECTORY = "imports"
    }
}
