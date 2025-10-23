package com.karen_yao.chinesetravel.features.home.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment
import com.karen_yao.chinesetravel.shared.extensions.repo
import com.karen_yao.chinesetravel.shared.utils.TestDataUtils
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import java.io.File
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.shared.utils.TranslationUtils

/**
 * Home fragment displaying the list of captured place snaps.
 * Provides navigation to capture functionality.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel by lazy { 
        ViewModelProvider(this, HomeViewModelFactory(repo()))[HomeViewModel::class.java] 
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView(view)
        setupFloatingActionButton(view)
        setupTestButton(view)
        setupBackButton(view)
        setupClearButton(view)
        testImageForLocation(requireContext())
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler)
        val adapter = SnapsAdapter { snap ->
            showDeleteConfirmation(snap)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())


        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snaps.collect { list ->
                Log.d("HomeFragment", "snaps size=${list.size}")
                adapter.submitList(list)
            }
        }
    }

    private fun setupFloatingActionButton(view: View) {
        view.findViewById<FloatingActionButton>(R.id.fabCapture).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, CaptureFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupTestButton(view: View) {
        val spinner = view.findViewById<Spinner>(R.id.spinnerTestFeatures) ?: return
        
        // Create test options
        val testOptions = listOf(
            "Select a test feature...",
            "🧪 Test Database Storage",
            "📸 Test OCR & Text Selection"
        )
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, testOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        // Handle selection
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    1 -> {
                        Log.d("HomeFragment", "Running database storage test...")
                        runTestExport()
                    }
                    2 -> {
                        Log.d("HomeFragment", "Running OCR test...")
                        testTextSelectionWithSample1()
                    }
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun runTestExport() {
        // Show immediate feedback
        Log.d("HomeFragment", "Starting database storage test...")

        // Show a toast to indicate test is running
        android.widget.Toast.makeText(
            requireContext(),
            "🧪 Testing database storage with 3 sample images...",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        // Run the test export
        TestDataUtils.exportAndTestImages(requireContext(), repo())

        // Show a follow-up toast after a delay
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(3000)
            android.widget.Toast.makeText(
                requireContext(),
                "✅ Database test completed! Check home screen for new entries.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    
    private fun testImageForLocation(context: Context) {
        val inputStream = context.assets.open("IMG_3849.JPG")
        val exif = ExifInterface(inputStream)

        val latLong = FloatArray(2)
        val hasLocation = exif.getLatLong(latLong)

        if (hasLocation) {
            val latitude = latLong[0]
            val longitude = latLong[1]
            Log.d("EXIF", "✅ Image has location: lat=$latitude, lon=$longitude")

            val mapsUrl = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
            Log.d("EXIF", "🌍 Google Maps link: $mapsUrl")
        } else {
            Log.d("EXIF", "❌ No location data found in EXIF.")
        }

        inputStream.close()
    }
    
    private fun testTextSelectionWithSample1() {
        Log.d("HomeFragment", "Testing OCR and text selection with sample1.jpg...")
        
        // Show immediate feedback
        android.widget.Toast.makeText(
            requireContext(),
            "📸 Testing OCR with sample image...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        try {
            // Copy sample1.jpg from assets to cache directory
            val inputStream = requireContext().assets.open("sample1.jpg")
            val testFile = File(requireContext().cacheDir, "test_sample1_${System.currentTimeMillis()}.jpg")
            testFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            // Run REAL OCR on the image
            runRealOCROnSample1(testFile)
                
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error testing OCR: ${e.message}")
            android.widget.Toast.makeText(
                requireContext(),
                "Error loading sample1.jpg: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun runRealOCROnSample1(file: File) {
        // Show loading message
        android.widget.Toast.makeText(
            requireContext(),
            "Running OCR on sample1.jpg...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        // Import the necessary OCR classes
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
        )
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text ?: ""
                Log.d("HomeFragment", "=== OCR RESULTS ===")
                Log.d("HomeFragment", "Raw detected text: '$rawText'")
                
                // Get ALL lines from OCR (not just Chinese ones)
                val allLines = rawText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                Log.d("HomeFragment", "📝 All OCR lines found: $allLines")
                Log.d("HomeFragment", "📊 Total lines: ${allLines.size}")
                
                // Enhanced Chinese text extraction
                val chineseLines = extractAllChineseLinesEnhanced(rawText)
                Log.d("HomeFragment", "🔤 Chinese lines found: $chineseLines")
                
                if (chineseLines.isEmpty()) {
                    // No Chinese text detected - check if we have any text at all
                    if (allLines.isNotEmpty()) {
                        Log.d("HomeFragment", "⚠️ No Chinese characters found, showing all lines for selection")
                        showTextSelectionScreen(allLines, file.absolutePath)
                    } else {
                        // No text detected at all - show crop suggestion
                        Log.w("HomeFragment", "⚠️ No text found in: $rawText")
                        showNoTextDetectedDialog(file.absolutePath)
                    }
                } else if (chineseLines.size > 1) {
                    // Multiple Chinese lines detected - show selection screen
                    showTextSelectionScreen(chineseLines, file.absolutePath)
                } else if (allLines.size > 1) {
                    // Multiple lines detected (including non-Chinese) - show ALL lines for selection
                    Log.d("HomeFragment", "📋 Showing all ${allLines.size} lines for selection")
                    showTextSelectionScreen(allLines, file.absolutePath)
                } else {
                    // Single line - check if it's good quality Chinese text
                    val textToProcess = chineseLines.firstOrNull() ?: allLines.firstOrNull() ?: ""
                    if (textToProcess.isNotEmpty()) {
                        // Check if the Chinese text quality is good
                        if (isGoodQualityChineseText(textToProcess)) {
                            // Process the text directly (in test mode, just show success)
                            android.widget.Toast.makeText(
                                requireContext(),
                                "✅ Good quality Chinese text detected: '$textToProcess'",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            // Poor quality Chinese text - suggest retry
                            showPoorQualityTextDialog(textToProcess, file.absolutePath)
                        }
                    } else {
                        android.widget.Toast.makeText(requireContext(), "No text to process", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("HomeFragment", "OCR failed: ${exception.message}")
                android.widget.Toast.makeText(
                    requireContext(),
                    "OCR failed: ${exception.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
    }
    
    private fun extractAllChineseLines(allText: String): List<String> {
        return allText.lines()
            .filter { line -> 
                val trimmed = line.trim()
                trimmed.isNotEmpty() && 
                // Check for Chinese characters (Han script)
                trimmed.any { char -> 
                    val charStr = char.toString()
                    charStr.matches(Regex("[\\p{IsHan}]")) || 
                    // Also include common Chinese punctuation and numbers
                    charStr.matches(Regex("[\\u3000-\\u303F\\uFF00-\\uFFEF]"))
                }
            }
            .map { it.trim() }
            .filter { it.length >= 2 } // Only include lines with at least 2 characters
    }
    
    private fun setupBackButton(view: View) {
        // Set up the common header
        val headerLayout = view.findViewById<View>(R.id.headerLayout)
        val backButton = headerLayout.findViewById<Button>(R.id.btnBack)
        val titleText = headerLayout.findViewById<android.widget.TextView>(R.id.tvHeaderTitle)
        val rightText = headerLayout.findViewById<android.widget.TextView>(R.id.tvHeaderRight)
        
        // Set title and show snap count
        titleText.text = "📚 Your Travel Collection"
        rightText.visibility = android.view.View.VISIBLE
        
        // Set up back button
        backButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, WelcomeFragment())
                .commit()
        }
        
        // Update snap count in the header
        val snapCountText = rightText
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.snaps.collect { list ->
                val count = list.size
                snapCountText.text = if (count == 1) "1 snap" else "$count snaps"
            }
        }
    }
    
    private fun setupClearButton(view: View) {
        // Set up the clear button that's already in the layout
        val clearButton = view.findViewById<Button>(R.id.btnClear)
        clearButton?.setOnClickListener {
            showClearConfirmation()
        }
    }
    
    private fun showClearConfirmation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear All Snaps")
            .setMessage("Are you sure you want to delete all captured snaps? This action cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                clearAllSnaps()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearAllSnaps() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo().clearAllSnaps()
                android.widget.Toast.makeText(
                    requireContext(),
                    "All snaps cleared!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Error clearing snaps: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showDeleteConfirmation(snap: PlaceSnap) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Snap")
            .setMessage("Are you sure you want to delete '${snap.nameCn}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSnap(snap)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteSnap(snap: PlaceSnap) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo().deleteSnap(snap)
                Toast.makeText(
                    requireContext(),
                    "Snap deleted!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error deleting snap: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Enhanced Chinese text extraction with better detection patterns.
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
     */
    private fun cleanChineseText(text: String): String {
        return text
            .replace(Regex("[\\u200b-\\u200d\\ufeff]"), "") // Zero-width characters
            .replace(Regex("[\\u00a0]"), " ") // Non-breaking spaces
            .replace(Regex("\\s+"), " ") // Multiple spaces
            .trim()
            .takeIf { it.length >= 1 }
            ?: ""
    }
    
    /**
     * Checks if the detected Chinese text is of good quality.
     */
    private fun isGoodQualityChineseText(text: String): Boolean {
        val hasArtifacts = text.contains(Regex("[\\u200b-\\u200d\\ufeff]")) ||
                          text.contains(Regex("[\\u00a0]")) ||
                          text.contains(Regex("\\s{2,}")) ||
                          text.length < 2 ||
                          text.contains(Regex("[^\\p{IsHan}\\p{IsPunctuation}\\s]"))
        
        val chineseCharCount = text.count { it.toString().matches(Regex("[\\p{IsHan}]")) }
        val chineseRatio = if (text.isNotEmpty()) chineseCharCount.toFloat() / text.length else 0f
        
        return !hasArtifacts && chineseRatio > 0.5f && text.length >= 2
    }
    
    /**
     * Shows text selection screen with detected texts.
     */
    private fun showTextSelectionScreen(texts: List<String>, imagePath: String) {
        val textSelectionFragment = com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment.newInstance(
            detectedTexts = texts,
            imagePath = imagePath,
            selectedText = ""
        )
        
        parentFragmentManager.beginTransaction()
            .replace(R.id.container, textSelectionFragment)
            .addToBackStack(null)
            .commit()
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
                // This would need to be implemented if needed
                android.widget.Toast.makeText(requireContext(), "Gallery selection not available in test mode", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("🏠 Back to Home") { _, _ ->
                // Stay on home screen (already here)
                android.widget.Toast.makeText(requireContext(), "Staying on home screen", android.widget.Toast.LENGTH_SHORT).show()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Shows a dialog when poor quality Chinese text is detected.
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
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setNegativeButton("📁 Choose from Gallery") { _, _ ->
                android.widget.Toast.makeText(requireContext(), "Gallery selection not available in test mode", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("✅ Use This Text") { _, _ ->
                // Process the text anyway - this would need to be implemented
                android.widget.Toast.makeText(requireContext(), "✅ Using detected text: '$detectedText'", android.widget.Toast.LENGTH_LONG).show()
            }
            .create()
        
        dialog.show()
    }
}