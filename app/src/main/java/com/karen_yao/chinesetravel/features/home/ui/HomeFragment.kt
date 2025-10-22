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
import android.widget.Button
import android.widget.LinearLayout
import java.io.File
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap

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
        // Add a test button for development/testing purposes
        val testButton = view.findViewById<Button>(R.id.btnTest) ?: return
        testButton.setOnClickListener {
            Log.d("HomeFragment", "Running test data export...")
            runTestExport()
        }
        
        // Add a test button for text selection with sample1.jpg
        val testTextSelectionButton = Button(requireContext()).apply {
            text = "Test Text Selection"
            textSize = 12f
            setTextColor(resources.getColor(R.color.red_primary, null))
            background = resources.getDrawable(R.drawable.button_beige_outline, null)
            setPadding(12, 8, 12, 8)
            setOnClickListener {
                testTextSelectionWithSample1()
            }
        }
        
        // Add the test text selection button to the button container
        val buttonContainer = view.findViewById<LinearLayout>(R.id.buttonContainer)
        buttonContainer?.addView(testTextSelectionButton)
    }

    private fun runTestExport() {
        // Show immediate feedback
        Log.d("HomeFragment", "Starting test export...")

        // Run the test export
        TestDataUtils.exportAndTestImages(requireContext(), repo())

        // Show a toast to indicate test is running
        android.widget.Toast.makeText(
            requireContext(),
            "Test export started! Check logs for results.",
            android.widget.Toast.LENGTH_LONG
        ).show()

        // Refresh the RecyclerView to show any new test data
        viewLifecycleOwner.lifecycleScope.launch {
            // Wait a moment for the test to complete, then refresh
            kotlinx.coroutines.delay(2000)
            // The RecyclerView will automatically update via the ViewModel's Flow
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
        Log.d("HomeFragment", "Testing text selection with sample1.jpg...")
        
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
            Log.e("HomeFragment", "Error testing text selection: ${e.message}")
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
                
                // Show all detected text lines for debugging
                val allLines = rawText.lines().filter { it.trim().isNotEmpty() }
                Log.d("HomeFragment", "All detected lines: $allLines")
                
                // Extract Chinese lines from the real OCR result
                val chineseLines = extractAllChineseLines(rawText)
                Log.d("HomeFragment", "Chinese lines found: $chineseLines")
                
                if (chineseLines.isNotEmpty()) {
                    // Use enhanced ranking to prioritize restaurant names
                    val imageProcessor = com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor()
                    val rankedTexts = imageProcessor.rankRestaurantNames(chineseLines)
                    
                    Log.d("HomeFragment", "=== ENHANCED RANKING ===")
                    rankedTexts.forEach { ranked ->
                        Log.d("HomeFragment", "  '${ranked.text}' (score: ${ranked.score})")
                    }
                    
                    // Show success message with ranking info
                    val bestScore = rankedTexts.firstOrNull()?.score ?: 0f
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Found ${chineseLines.size} Chinese lines (best: ${rankedTexts.firstOrNull()?.text} - score: $bestScore)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    // Navigate to text selection fragment with ALL lines in ranked order
                    val allLinesInRankedOrder = rankedTexts.map { it.text } + 
                        chineseLines.filter { line -> !rankedTexts.any { it.text == line } }
                    val textSelectionFragment = com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment.newInstance(
                        detectedTexts = allLinesInRankedOrder, // Use all lines in ranked order
                        imagePath = file.absolutePath,
                        selectedText = ""
                    )
                    
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.container, textSelectionFragment)
                        .addToBackStack(null)
                        .commit()
                } else {
                    // Show what was actually detected
                    android.widget.Toast.makeText(
                        requireContext(),
                        "No Chinese text found. Detected: ${allLines.take(2).joinToString(", ")}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    Log.d("HomeFragment", "No Chinese characters detected in: $allLines")
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
}