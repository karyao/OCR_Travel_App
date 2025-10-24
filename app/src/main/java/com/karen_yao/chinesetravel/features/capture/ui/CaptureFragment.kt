
package com.karen_yao.chinesetravel.features.capture.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
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
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import com.karen_yao.chinesetravel.shared.utils.TranslationUtils
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) processGalleryUri(uri)
        else showMessage("No image selected")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader(view)
        askPermissions { startCamera(view.findViewById(R.id.previewView)) }
        setupButtons(view)
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure camera is running when fragment resumes
        if (!cameraManager.isCameraReady()) {
            val previewView = view?.findViewById<PreviewView>(R.id.previewView)
            if (previewView != null) {
                Log.d("CaptureFragment", "Camera not ready, restarting...")
                startCamera(previewView)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop camera when fragment is paused to save resources
        cameraManager.stopCamera()
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

    private fun askPermissions(onGranted: () -> Unit) {
        val neededPermissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (neededPermissions.isEmpty()) {
            onGranted()
        } else {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                if (results.values.all { it }) onGranted()
                else Toast.makeText(requireContext(), "Permissions required", Toast.LENGTH_SHORT).show()
            }.launch(neededPermissions.toTypedArray())
        }
    }

    private fun startCamera(previewView: PreviewView) {
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
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    
    private fun isCameraAvailable(): Boolean {
        return requireContext().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }
    
    private fun restartCamera() {
        val previewView = view?.findViewById<PreviewView>(R.id.previewView)
        if (previewView != null) {
            startCamera(previewView)
        }
    }
    
    private fun switchCamera() {
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
        if (!cameraManager.isCameraReady()) {
            showMessage("Camera not ready. Please wait...")
            return
        }
        
        val file = File(requireContext().cacheDir, "snap_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        
        showMessage("Capturing photo...")
        
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    showMessage("Capture failed: ${exception.message}")
                    if (exception.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED) {
                        restartCamera()
                    }
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    showMessage("Photo captured! Processing...")
                    processCapturedFile(file)
                }
            }
        )
    }

    private fun processCapturedFile(file: File) {
        // Preprocess image for better OCR accuracy
        val preprocessedFile = imageProcessor.preprocessImageForOCR(file)
        
        val bitmap = BitmapFactory.decodeFile(preprocessedFile.absolutePath)
        val image = InputImage.fromBitmap(bitmap, 0)
        processImageWithOCR(image, file, "camera") // Use original file for database storage
    }

    private fun processGalleryUri(uri: Uri) {
        val file = File(requireContext().cacheDir, "gal_${System.currentTimeMillis()}.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            file.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
        }
        
        // Preprocess image for better OCR accuracy
        val preprocessedFile = imageProcessor.preprocessImageForOCR(file)
        
        val image = InputImage.fromFilePath(requireContext(), android.net.Uri.fromFile(preprocessedFile))
        processImageWithOCR(image, file, "gallery") // Use original file for database storage
    }

    private fun processImageWithOCR(image: InputImage, file: File, source: String) {
        showMessage("Running OCR ($source)...")
        
        // Use Google ML Kit for Chinese text recognition - SIMPLIFIED
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        
        lifecycleScope.launch {
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
                        }
                    }
                }
            } catch (exception: Exception) {
                showMessage("OCR failed: ${exception.message}")
            }
        }
    }

    // REMOVED: Complex Chinese text extraction functions that were causing issues
    // Now using simplified approach - let ML Kit do its job and trust the results

    private fun showTextSelectionScreen(chineseLines: List<String>, imagePath: String) {
        val textSelectionFragment = TextSelectionFragment.newInstance(
            detectedTexts = chineseLines,
            imagePath = imagePath,
            selectedText = ""
        )
        
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, textSelectionFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun processSelectedText(chineseText: String, file: File) {
        val pinyin = if (chineseText.isNotBlank()) PinyinUtils.toPinyin(chineseText) else ""

        lifecycleScope.launchWhenStarted {
            val location = imageProcessor.extractLocationFromFile(file)
            val address = location?.let { (lat, lng) ->
                reverseGeocode(lat, lng)
            } ?: "Unknown location"

            val totalCount = viewModel.saveAndCount(
                chineseText, pinyin, location?.first, location?.second,
                address, file.absolutePath
            )

            Toast.makeText(requireContext(), "Saved. Total rows: $totalCount", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (exception: Exception) { 
            null 
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
                // Go back to capture screen to try again
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton("📁 Choose from Gallery") { _, _ ->
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
                // Go back to capture screen to try again
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton("📁 Choose from Gallery") { _, _ ->
                // Open gallery picker
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .setNeutralButton("🏠 Back to Home") { _, _ ->
                // Navigate directly to home to avoid loop
                parentFragmentManager.beginTransaction()
                    .replace(R.id.container, com.karen_yao.chinesetravel.features.home.ui.HomeFragment())
                    .commit()
            }
            .create()
        
        dialog.show()
    }
}
