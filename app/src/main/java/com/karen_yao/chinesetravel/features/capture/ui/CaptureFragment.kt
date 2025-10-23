
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
import com.karen_yao.chinesetravel.features.capture.ocr.PaddleOCRService
import com.karen_yao.chinesetravel.features.capture.ocr.PaddleOCRServiceImpl
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.capture.camera.CameraManager
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment
import com.karen_yao.chinesetravel.shared.extensions.repo
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import com.karen_yao.chinesetravel.shared.utils.TranslationUtils
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
    private val paddleOCRService: PaddleOCRService = PaddleOCRServiceImpl()

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
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val image = InputImage.fromBitmap(bitmap, 0)
        processImageWithOCR(image, file, "camera")
    }

    private fun processGalleryUri(uri: Uri) {
        val file = File(requireContext().cacheDir, "gal_${System.currentTimeMillis()}.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
            file.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
        }
        val image = InputImage.fromFilePath(requireContext(), uri)
        processImageWithOCR(image, file, "gallery")
    }

    private fun processImageWithOCR(image: InputImage, file: File, source: String) {
        Toast.makeText(requireContext(), "Running PaddleOCR ($source)...", Toast.LENGTH_SHORT).show()

        // Log image details for debugging
        Log.d("CaptureFragment", "📸 Processing image: ${file.name}, size: ${file.length()} bytes")
        
        // Use PaddleOCR for Chinese text recognition
        lifecycleScope.launch {
            try {
                paddleOCRService.recognizeText(image).collect { rawText ->
                    Log.d("CaptureFragment", "🔍 PaddleOCR text: $rawText")
                    
                    // Get ALL lines from OCR (not just Chinese ones)
                    val allLines = rawText.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    
                    Log.d("CaptureFragment", "📝 All OCR lines found: $allLines")
                    Log.d("CaptureFragment", "📊 Total lines: ${allLines.size}")
                    
                    // Enhanced Chinese text extraction
                    val chineseLines = extractAllChineseLinesEnhanced(rawText)
                    Log.d("CaptureFragment", "🔤 Chinese lines found: $chineseLines")
                    
                    if (chineseLines.isEmpty()) {
                        // No Chinese text detected - check if we have any text at all
                        if (allLines.isNotEmpty()) {
                            Log.d("CaptureFragment", "⚠️ No Chinese characters found, showing all lines for selection")
                            showTextSelectionScreen(allLines, file.absolutePath)
                        } else {
                            // No text detected at all - show crop suggestion
                            Log.w("CaptureFragment", "⚠️ No text found in: $rawText")
                            showNoTextDetectedDialog(file.absolutePath)
                        }
                    } else if (chineseLines.size > 1) {
                        // Multiple Chinese lines detected - show selection screen
                        showTextSelectionScreen(chineseLines, file.absolutePath)
                    } else if (allLines.size > 1) {
                        // Multiple lines detected (including non-Chinese) - show ALL lines for selection
                        Log.d("CaptureFragment", "📋 Showing all ${allLines.size} lines for selection")
                        showTextSelectionScreen(allLines, file.absolutePath)
                    } else {
                        // Single line - check if it's good quality Chinese text
                        val textToProcess = chineseLines.firstOrNull() ?: allLines.firstOrNull() ?: ""
                        if (textToProcess.isNotEmpty()) {
                            // Check if the Chinese text quality is good
                            if (isGoodQualityChineseText(textToProcess)) {
                                processSelectedText(textToProcess, file)
                            } else {
                                // Poor quality Chinese text - suggest retry
                                showPoorQualityTextDialog(textToProcess, file.absolutePath)
                            }
                        } else {
                            Toast.makeText(requireContext(), "No text to process", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.e("CaptureFragment", "❌ PaddleOCR failed: ${exception.message}")
                Toast.makeText(requireContext(), "PaddleOCR API failed: ${exception.message}", Toast.LENGTH_LONG).show()
                
                // Show detailed error information
                val errorDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("🚨 PaddleOCR API Error")
                    .setMessage("PaddleOCR API is not available:\n\n${exception.message}\n\nPlease check:\n• Internet connection\n• API endpoint configuration\n• API key (if required)")
                    .setPositiveButton("Retry") { _, _ ->
                        // Retry the same image
                        processImageWithOCR(image, file, source)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        // Go back to capture screen
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                    .create()
                errorDialog.show()
            }
        }
    }

    private fun extractAllChineseLines(allText: String): List<String> {
        return allText.lines()
            .filter { line -> 
                line.trim().isNotEmpty() &&
                line.any { it.toString().matches(Regex("[\\p{IsHan}]")) }
            }
            .map { it.trim() }
    }
    
    /**
     * Enhanced Chinese text extraction with better detection patterns.
     * Handles various Chinese text formats and mixed content.
     */
    private fun extractAllChineseLinesEnhanced(allText: String): List<String> {
        return allText.lines()
            .map { line -> line.trim() }
            .filter { line -> 
                line.isNotEmpty() && hasChineseCharacters(line)
            }
            .map { line -> cleanChineseText(line) }
            .filter { line -> line.isNotEmpty() }
    }
    
    /**
     * Check if a line contains Chinese characters.
     * Uses multiple detection patterns for better accuracy.
     */
    private fun hasChineseCharacters(text: String): Boolean {
        // Pattern 1: Standard Han characters
        if (text.any { it.toString().matches(Regex("[\\p{IsHan}]")) }) {
            return true
        }
        
        // Pattern 2: CJK Unified Ideographs
        if (text.any { it.toString().matches(Regex("[\\u4e00-\\u9fff]")) }) {
            return true
        }
        
        // Pattern 3: Common Chinese punctuation and symbols
        if (text.any { it.toString().matches(Regex("[\\u3000-\\u303f\\u3100-\\u312f]")) }) {
            return true
        }
        
        return false
    }
    
    /**
     * Clean and normalize Chinese text.
     * Removes common OCR artifacts and normalizes spacing.
     */
    private fun cleanChineseText(text: String): String {
        return text
            // Remove common OCR artifacts
            .replace(Regex("[\\u200b-\\u200d\\ufeff]"), "") // Zero-width characters
            .replace(Regex("[\\u00a0]"), " ") // Non-breaking spaces
            // Normalize multiple spaces
            .replace(Regex("\\s+"), " ")
            // Remove leading/trailing whitespace
            .trim()
            // Remove lines that are too short (likely noise)
            .takeIf { it.length >= 1 }
            ?: ""
    }

    private fun extractBestChineseLine(allText: String): String =
        extractAllChineseLines(allText).firstOrNull() ?: ""

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
    
    /**
     * Checks if the detected Chinese text is of good quality.
     * Returns true if the text appears to be clear and well-formed.
     */
    private fun isGoodQualityChineseText(text: String): Boolean {
        // Check for common OCR artifacts that indicate poor quality
        val hasArtifacts = text.contains(Regex("[\\u200b-\\u200d\\ufeff]")) || // Zero-width characters
                          text.contains(Regex("[\\u00a0]")) || // Non-breaking spaces
                          text.contains(Regex("\\s{2,}")) || // Multiple spaces
                          text.length < 2 || // Too short
                          text.contains(Regex("[^\\p{IsHan}\\p{IsPunctuation}\\s]")) // Non-Chinese characters mixed in
        
        // Check if text has reasonable Chinese character density
        val chineseCharCount = text.count { it.toString().matches(Regex("[\\p{IsHan}]")) }
        val chineseRatio = if (text.isNotEmpty()) chineseCharCount.toFloat() / text.length else 0f
        
        return !hasArtifacts && chineseRatio > 0.5f && text.length >= 2
    }
    
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
