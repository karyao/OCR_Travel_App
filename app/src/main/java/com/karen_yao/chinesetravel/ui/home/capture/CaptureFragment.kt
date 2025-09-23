package com.karen_yao.chinesetravel.ui.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.repo
import com.karen_yao.chinesetravel.util.toPinyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class CaptureFragment : Fragment(R.layout.fragment_capture) {

    private val TAG = "CaptureFragment"

    private var imageCapture: ImageCapture? = null
    private val vm by lazy { ViewModelProvider(this, CaptureVMFactory(repo()))[CaptureViewModel::class.java] }

    // --- Gallery launchers ---
    // New Photo Picker (preferred)
    private val pickImagePhotoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        Log.d(TAG, "PhotoPicker result: $uri")
        if (uri != null) {
            Toast.makeText(requireContext(), "Picked image", Toast.LENGTH_SHORT).show()
            processGalleryUri(uri)
        } else {
            // Fallback to GetContent if user canceled OR Picker not available
            pickImageGetContent.launch("image/*")
        }
    }

    // Fallback for older devices or picker not available
    private val pickImageGetContent = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        Log.d(TAG, "GetContent result: $uri")
        if (uri != null) {
            Toast.makeText(requireContext(), "Picked image (fallback)", Toast.LENGTH_SHORT).show()
            processGalleryUri(uri)
        } else {
            Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Runtime permissions
    private lateinit var onAllGranted: () -> Unit
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val granted = res.values.all { it }
        Log.d(TAG, "Permissions result: $res")
        if (granted) onAllGranted()
        else Toast.makeText(requireContext(), "Permissions required", Toast.LENGTH_SHORT).show()
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        askPermissions {
            startCamera(v.findViewById(R.id.previewView))
        }

        v.findViewById<Button>(R.id.btnShoot).setOnClickListener { takePhoto() }
        v.findViewById<Button>(R.id.btnGallery).setOnClickListener {
            // Try Photo Picker; on older devices it routes to a doc picker automatically,
            // but we also keep a manual fallback to GetContent.
            pickImagePhotoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
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
            onAllGranted = onGranted
            requestPermissionsLauncher.launch(need.toTypedArray())
        }
    }

    private fun startCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
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
                    Log.e(TAG, "Capture failed", exc)
                    Toast.makeText(requireContext(), "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Captured file: ${file.absolutePath}")
                    processCapturedFile(file)
                }
            })
    }

    // --- Camera path ---
    private fun processCapturedFile(file: File) {
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        val image = InputImage.fromBitmap(bmp, 0)
        ocrAndSave(image, file, from = "camera")
    }

    // --- Gallery path ---
    private fun processGalleryUri(uri: Uri) {
        Log.d(TAG, "Processing gallery Uri: $uri")

        // Copy to cache so we have a stable file path & EXIF access
        val file = File(requireContext().cacheDir, "gal_${System.currentTimeMillis()}.jpg")
        try {
            requireContext().contentResolver.openInputStream(uri).use { inS ->
                file.outputStream().use { outS -> inS?.copyTo(outS) }
            }
            Log.d(TAG, "Copied to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed", e)
            Toast.makeText(requireContext(), "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        // Create ML Kit InputImage from the Uri (so EXIF rotation is respected)
        val image = try {
            InputImage.fromFilePath(requireContext(), uri)
        } catch (e: Exception) {
            Log.e(TAG, "InputImage.fromFilePath failed", e)
            Toast.makeText(requireContext(), "Invalid image: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        ocrAndSave(image, file, from = "gallery")
    }

    // --- Shared OCR + save ---
    private fun ocrAndSave(image: InputImage, fileForExif: File, from: String) {
        Toast.makeText(requireContext(), "Running OCR ($from)...", Toast.LENGTH_SHORT).show()
        val recognizer = TextRecognition.getClient(
            com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
        )
        recognizer.process(image)
            .addOnSuccessListener { vt ->
                val raw = vt.text ?: ""
                Log.d(TAG, "OCR raw text:\n$raw")

                val chinese = bestChineseLine(raw)
                if (chinese.isBlank()) {
                    Toast.makeText(requireContext(), "No Chinese text found", Toast.LENGTH_SHORT).show()
                }
                val pinyin = if (chinese.isNotBlank()) toPinyin(chinese) else ""

                viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                    val pos = getLatLng(fileForExif)
                    val addr = pos?.let { reverseGeocode(it.first, it.second) }
                    vm.save(chinese, pinyin, pos?.first, pos?.second, addr, fileForExif.absolutePath)
                    Toast.makeText(requireContext(), "Saved ($from)", Toast.LENGTH_SHORT).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failure", e)
                Toast.makeText(requireContext(), "OCR error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Heuristic: pick the first non-empty line containing Han characters. */
    private fun bestChineseLine(all: String): String =
        all.lines().firstOrNull { line -> line.any { it.toString().matches(Regex("[\\p{IsHan}]")) } }
            ?.trim().orEmpty()

    private fun exifLatLng(file: File): Pair<Double, Double>? = try {
        ExifInterface(file.absolutePath).latLong?.let { Pair(it[0], it[1]) }
    } catch (_: Exception) { null }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLatLng(): Pair<Double, Double>? = withContext(Dispatchers.Main) {
        val fused = LocationServices.getFusedLocationProviderClient(requireContext())
        suspendCancellableCoroutine { c ->
            fused.lastLocation
                .addOnSuccessListener { loc -> c.resume(if (loc != null) Pair(loc.latitude, loc.longitude) else null, null) }
                .addOnFailureListener { c.resume(null, null) }
        }
    }

    private suspend fun getLatLng(file: File) = exifLatLng(file) ?: lastKnownLatLng()

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val g = Geocoder(requireContext(), Locale.getDefault())
            val list = g.getFromLocation(lat, lng, 1)
            list?.firstOrNull()?.let { a ->
                listOfNotNull(
                    a.featureName, a.thoroughfare, a.subLocality,
                    a.locality, a.adminArea, a.postalCode, a.countryName
                ).filter { it.isNotBlank() }.joinToString(", ")
            }
        } catch (_: Exception) { null }
    }
}


