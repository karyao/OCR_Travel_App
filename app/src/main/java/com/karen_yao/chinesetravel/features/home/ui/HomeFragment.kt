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
import android.widget.Spinner
import java.io.File
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

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

    private var isSpinnerInitialized = false
    
    private fun setupTestButton(view: View) {
        val spinner = view.findViewById<Spinner>(R.id.spinnerTestFeatures) ?: return
        
        // Create test options
        val testOptions = listOf(
            "Select a test feature...",
            "🧪 Test Database Storage",
            "📸 Test OCR 1 (IMG_3950.JPG)",
            "📸 Test OCR 2 (TEST2.png)"
        )
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, testOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        // Handle selection - only trigger if user actually selects something
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Prevent automatic triggering on spinner initialization
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true
                    return
                }
                
                when (position) {
                    1 -> {
                        Log.d("HomeFragment", "Running database storage test...")
                        runTestExport()
                    }
                    2 -> {
                        Log.d("HomeFragment", "Running OCR test 1...")
                        testTextSelectionWithSample1()
                    }
                    3 -> {
                        Log.d("HomeFragment", "Running OCR test 2...")
                        testTextSelectionWithSample2()
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
        Log.d("HomeFragment", "Testing OCR and text selection with IMG_3950.JPG...")
        
        // Show immediate feedback
        android.widget.Toast.makeText(
            requireContext(),
            "📸 Testing OCR with sample image...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        try {
            // Copy IMG_3950.JPG from assets to cache directory
            val inputStream = requireContext().assets.open("IMG_3950.JPG")
            val testFile = File(requireContext().cacheDir, "test_IMG_3693_${System.currentTimeMillis()}.jpg")
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
                "Error loading IMG_3950.JPG: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun testTextSelectionWithSample2() {
        Log.d("HomeFragment", "Testing OCR and text selection with TEST2.png...")
        
        // Show immediate feedback
        android.widget.Toast.makeText(
            requireContext(),
            "📸 Testing OCR with sample image 2...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        try {
            // Copy TEST2.png from assets to cache directory
            val inputStream = requireContext().assets.open("TEST2.png")
            val testFile = File(requireContext().cacheDir, "test_TEST2_${System.currentTimeMillis()}.png")
            testFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            // Run REAL OCR on the image
            runRealOCROnSample2(testFile)
                
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error testing OCR: ${e.message}")
            android.widget.Toast.makeText(
                requireContext(),
                "Error loading TEST2.png: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun runRealOCROnSample1(file: File) {
        // Show loading message
        android.widget.Toast.makeText(
            requireContext(),
            "Running OCR on IMG_3950.JPG...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        // Move heavy operations to background thread
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Preprocess image for better OCR accuracy
                val imageProcessor = com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor()
                val preprocessedFile = imageProcessor.preprocessImageForOCR(file)
                
                // Import the necessary OCR classes
                val bitmap = android.graphics.BitmapFactory.decodeFile(preprocessedFile.absolutePath)
                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
                )
                
                // Process OCR in background
                val result = recognizer.process(image).await()
                val rawText = result.text ?: ""
                
                Log.d("HomeFragment", "=== OCR RESULTS ===")
                Log.d("HomeFragment", "Raw detected text: '$rawText'")
                Log.d("HomeFragment", "Text length: ${rawText.length}")
                Log.d("HomeFragment", "Contains Chinese: ${rawText.any { it.toString().matches(Regex("[\\p{IsHan}]")) }}")

                // Get ALL lines from OCR (not just Chinese ones)
                val allLines = rawText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                Log.d("HomeFragment", "📝 All OCR lines found: $allLines")
                Log.d("HomeFragment", "📊 Total lines: ${allLines.size}")
                
                // Log each line separately for debugging
                allLines.forEachIndexed { index, line ->
                    Log.d("HomeFragment", "Line $index: '$line'")
                }

                // Update UI on main thread
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    // Show debug info about what was detected
                    android.widget.Toast.makeText(
                        requireContext(),
                        "🔍 OCR detected: '$rawText' (${rawText.length} chars)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    if (allLines.isEmpty()) {
                        // No text detected at all - show crop suggestion
                        Log.w("HomeFragment", "⚠️ No text found in: $rawText")
                        showNoTextDetectedDialog(file.absolutePath)
                    } else if (allLines.size > 1) {
                        // Multiple lines detected - show selection screen
                        Log.d("HomeFragment", "📋 Showing ${allLines.size} lines for selection")
                        showTextSelectionScreen(allLines, file.absolutePath)
                    } else {
                        // Single line - process it directly
                        val textToProcess = allLines.first()
                        if (textToProcess.length >= 2) {
                            processSelectedTextForTest(textToProcess, file)
                        } else {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Text too short, please try again",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.e("HomeFragment", "OCR failed: ${exception.message}")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "OCR failed: ${exception.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun runRealOCROnSample2(file: File) {
        // Show loading message
        android.widget.Toast.makeText(
            requireContext(),
            "Running OCR on TEST2.png...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        // Move heavy operations to background thread
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Preprocess image for better OCR accuracy
                val imageProcessor = com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor()
                val preprocessedFile = imageProcessor.preprocessImageForOCR(file)
                
                // Import the necessary OCR classes
                val bitmap = android.graphics.BitmapFactory.decodeFile(preprocessedFile.absolutePath)
                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
                )
                
                // Process OCR in background
                val result = recognizer.process(image).await()
                val rawText = result.text ?: ""
                
                Log.d("HomeFragment", "=== OCR RESULTS (Sample 2) ===")
                Log.d("HomeFragment", "Raw detected text: '$rawText'")
                Log.d("HomeFragment", "Text length: ${rawText.length}")
                Log.d("HomeFragment", "Contains Chinese: ${rawText.any { it.toString().matches(Regex("[\\p{IsHan}]")) }}")
                
                // Get ALL lines from OCR (not just Chinese ones)
                val allLines = rawText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                Log.d("HomeFragment", "📝 All OCR lines found: $allLines")
                Log.d("HomeFragment", "📊 Total lines: ${allLines.size}")
                
                // Log each line separately for debugging
                allLines.forEachIndexed { index, line ->
                    Log.d("HomeFragment", "Line $index: '$line'")
                }
                
                // Update UI on main thread
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    // Show debug info about what was detected
                    android.widget.Toast.makeText(
                        requireContext(),
                        "🔍 OCR detected: '$rawText' (${rawText.length} chars)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    if (allLines.isEmpty()) {
                        // No text detected at all - show crop suggestion
                        Log.w("HomeFragment", "⚠️ No text found in: $rawText")
                        showNoTextDetectedDialog(file.absolutePath)
                    } else if (allLines.size > 1) {
                        // Multiple lines detected - show selection screen
                        Log.d("HomeFragment", "📋 Showing ${allLines.size} lines for selection")
                        showTextSelectionScreen(allLines, file.absolutePath)
                    } else {
                        // Single line - process it directly
                        val textToProcess = allLines.first()
                        if (textToProcess.length >= 2) {
                            processSelectedTextForTest(textToProcess, file)
                        } else {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Text too short, please try again",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.e("HomeFragment", "OCR failed: ${exception.message}")
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "OCR failed: ${exception.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    // REMOVED: Complex Chinese text extraction - using simplified approach
    
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
    
    // REMOVED: All complex Chinese text extraction and quality check functions
    // Now using simplified approach - let ML Kit do its job and trust the results
    
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
    
    /**
     * Process selected text for test mode and save to database.
     * Simple version without complex async operations.
     */
    private fun processSelectedTextForTest(chineseText: String, file: File) {
        // Show immediate feedback
        android.widget.Toast.makeText(
            requireContext(),
            "🔄 Processing test OCR: '$chineseText'...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        // Process in background to avoid ANR
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get pinyin
                val pinyin = if (chineseText.isNotBlank()) {
                    com.karen_yao.chinesetravel.shared.utils.PinyinUtils.toPinyin(chineseText)
                } else ""
                
                // Extract location from file
                val location = com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor().extractLocationFromFile(file)
                
                // Get address using reverse geocoding if location is available
                val address = if (location != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                            val addresses = geocoder.getFromLocation(location.first, location.second, 1)
                            addresses?.firstOrNull()?.getAddressLine(0) ?: "Test Location"
                        }
                    } catch (e: Exception) {
                        "Test Location"
                    }
                } else {
                    "Test Location (No GPS)"
                }
                
                // Get translation
                val translation = try {
                    com.karen_yao.chinesetravel.shared.utils.TranslationUtils.translateChineseToEnglish(chineseText)
                } catch (e: Exception) {
                    "Translation unavailable"
                }
                
                val googleMapsLink = if (location != null) {
                    "https://www.google.com/maps/search/?api=1&query=${location.first},${location.second}"
                } else "No location found"
                
                // Create PlaceSnap
                val placeSnap = PlaceSnap(
                    imagePath = file.absolutePath,
                    nameCn = chineseText,
                    namePinyin = pinyin,
                    lat = location?.first,
                    longitude = location?.second,
                    address = address,
                    translation = translation,
                    googleMapsLink = googleMapsLink
                )
                
                // Save to database
                repo().saveSnap(placeSnap)
                val totalCount = repo().getSnapCount()
                
                // Show success message on main thread
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "✅ Test OCR saved to database! Total snaps: $totalCount",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                
                Log.d("HomeFragment", "✅ Test OCR saved: '$chineseText' -> Database (Total: $totalCount)")
                
            } catch (e: Exception) {
                Log.e("HomeFragment", "❌ Error saving test OCR: ${e.message}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "❌ Error saving test data: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}