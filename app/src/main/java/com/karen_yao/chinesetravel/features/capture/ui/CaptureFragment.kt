
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
 * Fragment for capturing photos and processing Chinese text recognition.
 * Handles camera functionality, gallery selection, and OCR processing.
 */
class CaptureFragment : Fragment(R.layout.fragment_capture) {

    private var imageCapture: ImageCapture? = null
    private val viewModel by lazy { 
        ViewModelProvider(this, CaptureViewModelFactory(repo()))[CaptureViewModel::class.java] 
    }
    private val cameraManager = CameraManager()
    private val imageProcessor = ImageProcessor()

    // Photo picker for gallery selection
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) processGalleryUri(uri)
        else Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader(view)
        askPermissions { startCamera(view.findViewById(R.id.previewView)) }
        setupButtons(view)
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
        cameraManager.startCamera(requireContext(), previewView, viewLifecycleOwner) { imageCapture ->
            this.imageCapture = imageCapture
        }
    }

    private fun takePhoto() {
        val file = File(requireContext().cacheDir, "snap_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
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
        Toast.makeText(requireContext(), "Running OCR ($source)...", Toast.LENGTH_SHORT).show()

        // Log image details for debugging
        Log.d("CaptureFragment", "📸 Processing image: ${file.name}, size: ${file.length()} bytes")
        
        // Use Google ML Kit for Chinese text recognition - SIMPLIFIED
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        
        lifecycleScope.launch {
            try {
                val result = recognizer.process(image).await()
                val rawText = result.text
                Log.d("CaptureFragment", "🔍 OCR text: $rawText")
                
                // SIMPLIFIED: Just get all non-empty lines
                val allLines = rawText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                Log.d("CaptureFragment", "📝 All OCR lines found: $allLines")
                
                if (allLines.isEmpty()) {
                    // No text detected at all - show crop suggestion
                    Log.w("CaptureFragment", "⚠️ No text found in: $rawText")
                    showNoTextDetectedDialog(file.absolutePath)
                } else if (allLines.size > 1) {
                    // Multiple lines detected - show selection screen
                    Log.d("CaptureFragment", "📋 Showing ${allLines.size} lines for selection")
                    showTextSelectionScreen(allLines, file.absolutePath)
                } else {
                    // Single line - process it directly (simplified quality check)
                    val textToProcess = allLines.first()
                    if (textToProcess.length >= 2) {
                        processSelectedText(textToProcess, file)
                    } else {
                        Toast.makeText(requireContext(), "Text too short, please try again", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (exception: Exception) {
                Log.e("CaptureFragment", "❌ OCR failed: ${exception.message}")
                Toast.makeText(requireContext(), "OCR failed: ${exception.message}", Toast.LENGTH_LONG).show()
                
                // Show simple error dialog
                val errorDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("🚨 OCR Error")
                    .setMessage("Text recognition failed. Please try:\n• Taking a clearer photo\n• Better lighting\n• Retry with a different image")
                    .setPositiveButton("Retry") { _, _ ->
                        processImageWithOCR(image, file, source)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                    .create()
                errorDialog.show()
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
