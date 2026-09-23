
package com.karen_yao.chinesetravel.features.capture.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import android.util.Log

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
    private var activeCaptureId: Long? = null
    private var nextCaptureId = 0L
    private var pendingLocationPermissionCaptureId: Long? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) processGalleryUri(uri)
        else showMessage("No image selected")
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
        view.findViewById<Button>(R.id.btnGallery).setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        view.findViewById<Button>(R.id.btnSwitchCamera).setOnClickListener { switchCamera() }
        
        // Update switch button text based on current camera
        updateSwitchButtonText(view)
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
        if (activeCaptureId != null) return
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
        if (activeCaptureId != null) return
        if (!cameraManager.isCameraReady()) {
            showMessage("Camera not ready. Please wait...")
            return
        }
        
        val captureId = ++nextCaptureId
        activeCaptureId = captureId
        setCaptureControlsEnabled(false)

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
                    finishCapture(captureId)
                    if (location != null) {
                        showMessage("Photo captured with location. Processing…")
                    } else {
                        showMessage("Photo captured without location. ${locationFailure.orEmpty()}")
                    }
                    processCapturedFile(file)
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
        setCaptureControlsEnabled(true)
    }

    private fun cancelPendingCapture() {
        captureJob?.cancel()
        captureJob = null
        activeCaptureId = null
        pendingLocationPermissionCaptureId = null
        setCaptureControlsEnabled(true)
    }

    private fun setCaptureControlsEnabled(enabled: Boolean) {
        view?.findViewById<Button>(R.id.btnShoot)?.isEnabled = enabled
        view?.findViewById<Button>(R.id.btnGallery)?.isEnabled = enabled
        view?.findViewById<Button>(R.id.btnSwitchCamera)?.isEnabled = enabled
    }

    private fun locationFailureMessage(reason: DeviceLocationProvider.FailureReason): String =
        when (reason) {
            DeviceLocationProvider.FailureReason.PERMISSION_DENIED -> "Location permission was not granted."
            DeviceLocationProvider.FailureReason.LOCATION_DISABLED -> "Location services are disabled."
            DeviceLocationProvider.FailureReason.TIMED_OUT -> "The location request timed out."
            DeviceLocationProvider.FailureReason.INVALID_OR_STALE -> "No recent valid location was available."
            DeviceLocationProvider.FailureReason.REQUEST_FAILED -> "The location request failed."
        }

    private fun processCapturedFile(file: File) {
        prepareAndProcessImage(file, "camera")
    }

    private fun processGalleryUri(uri: Uri) {
        val context = context ?: return
        val importDirectory = File(context.filesDir, IMPORT_DIRECTORY).apply { mkdirs() }
        val file = File(importDirectory, "gal_${System.currentTimeMillis()}.jpg")

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("The selected image could not be opened")
            inputStream.use { source ->
                file.outputStream().use { destination -> source.copyTo(destination) }
            }
            if (file.length() == 0L) throw IllegalStateException("The selected image was empty")
            prepareAndProcessImage(file, "gallery")
        } catch (exception: Exception) {
            file.delete()
            showMessage("Could not import image: ${exception.message}")
        }
    }

    private fun prepareAndProcessImage(file: File, source: String) {
        val context = context ?: run {
            file.delete()
            return
        }
        var temporaryFile: File? = null
        try {
            temporaryFile = imageProcessor.preprocessImageForOCR(file, context.cacheDir)
            val image = InputImage.fromFilePath(context, Uri.fromFile(temporaryFile))
            processImageWithOCR(image, file, source, temporaryFile)
        } catch (exception: Exception) {
            if (temporaryFile != file) temporaryFile?.delete()
            deleteUnreferencedImage(file.absolutePath)
            showMessage("Could not process image: ${exception.message}")
        }
    }

    private fun processImageWithOCR(
        image: InputImage,
        file: File,
        source: String,
        temporaryFile: File
    ) {
        showMessage("Running OCR ($source)...")
        
        // Use Google ML Kit for Chinese text recognition - SIMPLIFIED
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = recognizer.process(image).await()
                val rawText = result.text
                
                val allLines = rawText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                when {
                    allLines.isEmpty() -> showNoTextDetectedDialog(file.absolutePath)
                    allLines.size > 1 -> showTextSelectionScreen(allLines, file.absolutePath)
                    else -> {
                        val textToProcess = allLines.first()
                        if (textToProcess.length >= 2) {
                            processSelectedText(textToProcess, file)
                        } else {
                            showMessage("Text too short, please try again")
                            deleteUnreferencedImage(file.absolutePath)
                        }
                    }
                }
            } catch (exception: CancellationException) {
                deleteUnreferencedImage(file.absolutePath)
                throw exception
            } catch (exception: Exception) {
                showMessage("OCR failed: ${exception.message}")
                deleteUnreferencedImage(file.absolutePath)
            } finally {
                recognizer.close()
                if (temporaryFile != file) temporaryFile.delete()
            }
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

    private fun processSelectedText(chineseText: String, file: File) {
        val pinyin = if (chineseText.isNotBlank()) PinyinUtils.toPinyin(chineseText) else ""

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val location = imageProcessor.extractLocationFromFile(file)
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
                    Toast.makeText(requireContext(), "Saved. Total rows: $totalCount", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            } catch (exception: CancellationException) {
                deleteUnreferencedImage(file.absolutePath)
                throw exception
            } catch (exception: Exception) {
                deleteUnreferencedImage(file.absolutePath)
                showMessage("Save failed: ${exception.message}")
            }
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
                // Open gallery picker
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .setNeutralButton("✅ Use This Text") { _, _ ->
                // Process the text anyway
                val file = File(imagePath)
                processSelectedText(detectedText, file)
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
                // Open gallery picker
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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

    private fun deleteUnreferencedImage(imagePath: String) {
        val file = File(imagePath)
        val filesRoot = requireContext().filesDir.canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (candidate.path.startsWith(filesRoot.path + File.separator)) {
            candidate.delete()
        }
    }

    private companion object {
        const val CAPTURE_DIRECTORY = "captures"
        const val IMPORT_DIRECTORY = "imports"
    }
}
