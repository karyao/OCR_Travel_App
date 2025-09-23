package com.karen_yao.chinesetravel.ui.capture

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
import com.karen_yao.chinesetravel.repo
import com.karen_yao.chinesetravel.util.toPinyin
import com.karen_yao.chinesetravel.util.translateChineseToEnglish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class CaptureFragment : Fragment(R.layout.fragment_capture) {

    private var imageCapture: ImageCapture? = null
    private val vm by lazy { ViewModelProvider(this, CaptureVMFactory(repo()))[CaptureViewModel::class.java] }

    // Photo picker
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) processGalleryUri(uri)
        else Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        askPermissions { startCamera(v.findViewById(R.id.previewView)) }

        v.findViewById<Button>(R.id.btnShoot).setOnClickListener { takePhoto() }
        v.findViewById<Button>(R.id.btnGallery).setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun askPermissions(onGranted: () -> Unit) {
        val need = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isEmpty()) onGranted() else {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
                if (res.values.all { it }) onGranted()
                else Toast.makeText(requireContext(), "Permissions required", Toast.LENGTH_SHORT).show()
            }.launch(need.toTypedArray())
        }
    }

    private fun startCamera(previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            provider.unbindAll()
            provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val file = File(requireContext().cacheDir, "snap_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture?.takePicture(opts, ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processCapturedFile(file)
                }
            })
    }

    private fun processCapturedFile(file: File) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        val image = InputImage.fromBitmap(bmp, 0)
        ocrAndSave(image, file, from = "camera")
    }

    private fun processGalleryUri(uri: Uri) {
        val file = File(requireContext().cacheDir, "gal_${System.currentTimeMillis()}.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { inS ->
            file.outputStream().use { outS -> inS.copyTo(outS) }
        }
        val image = InputImage.fromFilePath(requireContext(), uri)
        ocrAndSave(image, file, from = "gallery")
    }

    private fun ocrAndSave(image: InputImage, file: File, from: String) {
        Toast.makeText(requireContext(), "Running OCR ($from)...", Toast.LENGTH_SHORT).show()
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { vt ->
                val raw = vt.text ?: ""
                val chinese = bestChineseLine(raw)
                val pinyin = if (chinese.isNotBlank()) toPinyin(chinese) else ""
                val translation = if (chinese.isNotBlank()) translateChineseToEnglish(chinese) else "No translation"

                lifecycleScope.launchWhenStarted {
                    val pos = getLatLng(file) // EXIF-only
                    val addr = pos?.let { reverseGeocode(it.first, it.second) } ?: "Unknown location"

                    val total = vm.saveAndCount(chinese, pinyin, pos?.first, pos?.second, addr, file.absolutePath, translation)

                    Toast.makeText(requireContext(), "Saved. Total rows: $total", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bestChineseLine(all: String): String =
        all.lines().firstOrNull { line -> line.any { it.toString().matches(Regex("[\\p{IsHan}]")) } }
            ?.trim().orEmpty()

    private fun exifLatLng(file: File): Pair<Double, Double>? = try {
        ExifInterface(file.absolutePath).latLong?.let { Pair(it[0], it[1]) }
    } catch (_: Exception) { null }

    private suspend fun getLatLng(file: File): Pair<Double, Double>? = exifLatLng(file)

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val g = Geocoder(requireContext(), Locale.getDefault())
            val list = g.getFromLocation(lat, lng, 1)
            list?.firstOrNull()?.getAddressLine(0)
        } catch (_: Exception) { null }
    }
}
