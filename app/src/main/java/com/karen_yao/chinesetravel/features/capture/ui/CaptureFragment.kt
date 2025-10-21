
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
import com.karen_yao.chinesetravel.shared.extensions.repo
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import com.karen_yao.chinesetravel.shared.utils.TranslationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
        
        askPermissions { startCamera(view.findViewById(R.id.previewView)) }
        setupButtons(view)
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
        Toast.makeText(requireContext(), "Running OCR ($source)...", Toast.LENGTH_SHORT).show()
        
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text ?: ""
                val chineseText = extractBestChineseLine(rawText)
                val pinyin = if (chineseText.isNotBlank()) PinyinUtils.toPinyin(chineseText) else ""
                val translation = if (chineseText.isNotBlank()) TranslationUtils.translateChineseToEnglish(chineseText) else "No translation"

                lifecycleScope.launchWhenStarted {
                    val location = imageProcessor.extractLocationFromFile(file)
                    val address = location?.let { (lat, lng) -> 
                        reverseGeocode(lat, lng) 
                    } ?: "Unknown location"

                    val totalCount = viewModel.saveAndCount(
                        chineseText, pinyin, location?.first, location?.second, 
                        address, file.absolutePath, translation
                    )

                    Toast.makeText(requireContext(), "Saved. Total rows: $totalCount", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "OCR failed: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractBestChineseLine(allText: String): String =
        allText.lines().firstOrNull { line -> 
            line.any { it.toString().matches(Regex("[\\p{IsHan}]")) } 
        }?.trim().orEmpty()

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (exception: Exception) { 
            null 
        }
    }
}
