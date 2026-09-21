package com.karen_yao.chinesetravel.shared.utils

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Utility class for testing OCR and location functionality with sample images.
 * Exports images from assets and processes them to test the app functionality.
 */
object TestDataUtils {
    
    private const val TAG = "TestDataUtils"
    
    /**
     * Test images available in assets folder.
     * Add your test images to the assets folder and update this list.
     */
    private val testImages = listOf(
        "chinese_character.jpg",
        "IMG_3849.JPG", 
        "IMG_3950.JPG"
    )

    internal data class TestRunSummary(
        val processed: Int,
        val inserted: Int,
        val located: Int,
        val unlocated: Int,
        val failed: Int
    )

    private const val MAP_FIXTURE_ID_PREFIX = "test-map-fixture-"

    /**
     * Deterministic records for exercising map behavior without OCR, geocoding, or network work.
     * The unlocated control record must remain absent from the map.
     */
    internal fun createMapDemoFixtures(createdAt: Long = System.currentTimeMillis()): List<PlaceSnap> =
        listOf(
            PlaceSnap(
                id = "${MAP_FIXTURE_ID_PREFIX}beijing",
                imagePath = "",
                nameCn = "北京",
                namePinyin = "Běijīng",
                lat = 39.9042,
                longitude = 116.4074,
                address = "Beijing, China",
                translation = "Beijing",
                googleMapsLink = "https://www.google.com/maps/search/?api=1&query=39.9042,116.4074",
                createdAt = createdAt
            ),
            PlaceSnap(
                id = "${MAP_FIXTURE_ID_PREFIX}shanghai",
                imagePath = "",
                nameCn = "上海",
                namePinyin = "Shànghǎi",
                lat = 31.2304,
                longitude = 121.4737,
                address = "Shanghai, China",
                translation = "Shanghai",
                googleMapsLink = "https://www.google.com/maps/search/?api=1&query=31.2304,121.4737",
                createdAt = createdAt - 1
            ),
            PlaceSnap(
                id = "${MAP_FIXTURE_ID_PREFIX}hong-kong",
                imagePath = "",
                nameCn = "香港",
                namePinyin = "Xiānggǎng",
                lat = 22.3193,
                longitude = 114.1694,
                address = "Hong Kong",
                translation = "Hong Kong",
                googleMapsLink = "https://www.google.com/maps/search/?api=1&query=22.3193,114.1694",
                createdAt = createdAt - 2
            ),
            PlaceSnap(
                id = "${MAP_FIXTURE_ID_PREFIX}unlocated",
                imagePath = "",
                nameCn = "无位置",
                namePinyin = "Wú wèizhì",
                lat = null,
                longitude = null,
                address = null,
                translation = "No location",
                googleMapsLink = null,
                createdAt = createdAt - 3
            )
        )

    /**
     * Replace only the stable map fixtures, preserving every non-fixture database record.
     */
    internal suspend fun seedMapDemoData(repository: TravelRepository): Int {
        val fixtures = createMapDemoFixtures()
        fixtures.forEach { fixture ->
            repository.deleteSnap(fixture)
            repository.saveSnap(fixture)
        }
        return fixtures.size
    }
    
    /**
     * Get real address from coordinates using reverse geocoding.
     * 
     * @param context Application context
     * @param latitude Latitude coordinate
     * @param longitude Longitude coordinate
     * @return Real address string or coordinates if geocoding fails
     */
    private suspend fun getRealAddress(context: Context, latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    val addressString = buildString {
                        address.getAddressLine(0)?.let { append(it) }
                        if (address.locality != null) {
                            if (isNotEmpty()) append(", ")
                            append(address.locality)
                        }
                        if (address.countryName != null) {
                            if (isNotEmpty()) append(", ")
                            append(address.countryName)
                        }
                    }
                    if (addressString.isNotEmpty()) {
                        Log.d(TAG, "📍 Real address found: $addressString")
                        addressString
                    } else {
                        Log.w(TAG, "📍 Geocoding returned empty address, using coordinates")
                        "$latitude, $longitude"
                    }
                } else {
                    Log.w(TAG, "📍 No address found, using coordinates")
                    "$latitude, $longitude"
                }
            } catch (e: Exception) {
                Log.e(TAG, "📍 Geocoding failed: ${e.message}, using coordinates")
                "$latitude, $longitude"
            }
        }
    }
    
    /**
     * Export all test images from assets to device storage and process them.
     * This will test OCR, location extraction, and database storage.
     * 
     * @param context Application context
     * @param repository TravelRepository for saving test data
     */
    internal suspend fun exportAndTestImages(
        context: Context,
        repository: TravelRepository
    ): TestRunSummary = withContext(Dispatchers.IO) {
        Log.d(TAG, "🚀 Starting test image export and processing...")
        Log.d(TAG, "📋 Test images to process: $testImages")

        var insertedCount = 0
        var locatedCount = 0
        var unlocatedCount = 0
        var failedCount = 0

        for (imageName in testImages) {
            try {
                Log.d(TAG, "📸 Processing test image: $imageName")

                val exportedFile = exportImageFromAssets(context, imageName)
                if (exportedFile != null) {
                    Log.d(TAG, "✅ Successfully exported: ${exportedFile.absolutePath}")
                    Log.d(TAG, "📏 File size: ${exportedFile.length()} bytes")
                    val realLocation = ImageProcessor().extractLocationFromFile(exportedFile)

                    Log.d(TAG, "🔍 Location check for $imageName: realLocation=$realLocation")

                    val testSnap = createPlaceSnapFromRealImage(
                        imagePath = exportedFile.absolutePath,
                        realLocation = realLocation,
                        context = context
                    )

                    repository.saveSnap(testSnap)
                    insertedCount++

                    if (realLocation != null) {
                        locatedCount++
                        Log.d(TAG, "✅ Successfully processed with REAL location: $imageName")
                        Log.d(TAG, "   📍 Real GPS: ${testSnap.lat}, ${testSnap.longitude}")
                    } else {
                        unlocatedCount++
                        Log.d(TAG, "⚠️ Processed with NO REAL LOCATION: $imageName")
                        Log.d(TAG, "   📍 Location fields stored as null")
                    }

                    Log.d(TAG, "   🔗 Maps Link: ${testSnap.googleMapsLink}")
                    Log.d(TAG, "   📝 Chinese: ${testSnap.nameCn}")
                    Log.d(TAG, "   🔤 Pinyin: ${testSnap.namePinyin}")
                    Log.d(TAG, "   🏠 Address: ${testSnap.address}")
                } else {
                    failedCount++
                    Log.w(TAG, "❌ Failed to export: $imageName")
                }
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "💥 Error processing $imageName", e)
            }
        }

        val summary = TestRunSummary(
            processed = testImages.size,
            inserted = insertedCount,
            located = locatedCount,
            unlocated = unlocatedCount,
            failed = failedCount
        )
        Log.d(TAG, "🎉 Test completed: ${summary.inserted}/${summary.processed} images inserted")
        Log.d(TAG, "📍 Images with REAL GPS location: ${summary.located}")
        Log.d(TAG, "⚠️ Images with NO REAL LOCATION: ${summary.unlocated}")
        Log.d(TAG, "❌ Failed images: ${summary.failed}")
        Log.d(TAG, "📱 Check your home screen - new test entries should appear in the list!")
        summary
    }
    
    /**
     * Export a single image from assets to device storage.
     * 
     * @param context Application context
     * @param imageName Name of the image file in assets
     * @return File object of the exported image, or null if export failed
     */
    private fun exportImageFromAssets(context: Context, imageName: String): File? {
        return try {
            // Create a file in the app's cache directory
            val outputFile = File(context.cacheDir, "test_$imageName")
            
            // Copy from assets to cache directory
            context.assets.open(imageName).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Log.d(TAG, "Exported $imageName to ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export $imageName", e)
            null
        }
    }
    
    private suspend fun createPlaceSnapFromRealImage(
        imagePath: String,
        realLocation: Pair<Double, Double>?,
        context: Context
    ): PlaceSnap {
        val lat = realLocation?.first
        val lng = realLocation?.second
        val address = realLocation?.let { (latitude, longitude) ->
            getRealAddress(context, latitude, longitude)
        }

        val ocrResult = runRealOCR(context, imagePath)
        val chineseText = ocrResult.first
        val pinyinText = if (chineseText.isNotEmpty()) {
            PinyinUtils.toPinyin(chineseText)
        } else {
            "No text detected"
        }

        Log.d(TAG, "🔄 Getting translation for: $chineseText")
        val realTranslation = try {
            TranslationUtils.translateChineseToEnglish(chineseText)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Translation failed: ${e.message}")
            "Translation failed"
        }
        Log.d(TAG, "✅ Translation result: $realTranslation")

        return PlaceSnap(
            imagePath = imagePath,
            nameCn = chineseText,
            namePinyin = pinyinText,
            lat = lat,
            longitude = lng,
            address = address,
            translation = realTranslation,
            googleMapsLink = if (lat != null && lng != null) {
                "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
            } else {
                null
            }
        )
    }
    
    /**
     * Run real OCR on an image file.
     * Returns the detected Chinese text.
     */
    private suspend fun runRealOCR(context: Context, imagePath: String): Pair<String, String> {
        return try {
            Log.d(TAG, "🔍 Running OCR on: $imagePath")
            
            // Create InputImage from file
            val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, android.net.Uri.fromFile(java.io.File(imagePath)))
            
            // Create Chinese text recognizer
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
            )
            
            // Run OCR
            val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        continuation.resume(text)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ OCR failed: ${exception.message}")
                        // Return empty result instead of creating Text object
                        continuation.resume(com.google.mlkit.vision.text.Text("", emptyList<com.google.mlkit.vision.text.Text.Element>()))
                    }
            }
            val detectedText = result.text ?: ""
            
            Log.d(TAG, "📝 OCR detected text: '$detectedText'")
            
            // Extract Chinese characters
            val chineseText = detectedText.filter { it.toString().matches(Regex("[\\p{IsHan}]")) }
            
            Log.d(TAG, "🔤 Chinese characters found: '$chineseText'")
            
            recognizer.close()
            
            Pair(chineseText, detectedText)
        } catch (e: Exception) {
            Log.e(TAG, "❌ OCR failed: ${e.message}")
            Pair("OCR failed", "")
        }
    }
    

    /**
     * Test a single image with full processing (OCR, location, database).
     * 
     * @param context Application context
     * @param repository TravelRepository for saving test data
     * @param imageName Name of the image in assets
     * @param onResult Callback with the test result
     */
    fun testSingleImage(
        context: Context, 
        repository: TravelRepository, 
        imageName: String, 
        onResult: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔍 Testing single image: $imageName")
                
                val exportedFile = exportImageFromAssets(context, imageName)
                if (exportedFile != null) {
                    // Check location data
                    val imageProcessor = ImageProcessor()
                    val realLocation = imageProcessor.extractLocationFromFile(exportedFile)
                    
                    // Create test snap
                    val testSnap = createPlaceSnapFromRealImage(
                        imagePath = exportedFile.absolutePath,
                        realLocation = realLocation,
                        context = context
                    )
                    
                    // Save to database
                    repository.saveSnap(testSnap)
                    
                    val result = buildString {
                        appendLine("✅ Image: $imageName")
                        if (realLocation != null) {
                            appendLine("📍 Location: REAL GPS FOUND")
                            appendLine("   Coordinates: ${realLocation.first}, ${realLocation.second}")
                        } else {
                            appendLine("⚠️ Location: NO REAL LOCATION FOUND")
                            appendLine("   Location fields stored as null")
                        }
                        appendLine("📝 Chinese: ${testSnap.nameCn}")
                        appendLine("🔤 Pinyin: ${testSnap.namePinyin}")
                        appendLine("🌐 Translation: ${testSnap.translation}")
                        appendLine("🏠 Address: ${testSnap.address}")
                    }
                    
                    onResult(result)
                } else {
                    onResult("❌ Failed to export image: $imageName")
                }
            } catch (e: Exception) {
                onResult("💥 Test failed for $imageName: ${e.message}")
            }
        }
    }

    /**
     * Test OCR functionality with a specific image.
     * 
     * @param context Application context
     * @param imageName Name of the image in assets
     * @param onResult Callback with the OCR result
     */
    fun testOCRWithImage(context: Context, imageName: String, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val exportedFile = exportImageFromAssets(context, imageName)
                if (exportedFile != null) {
                    val testResult = "OCR Test Result for $imageName"
                    onResult(testResult)
                } else {
                    onResult("Failed to export image")
                }
            } catch (e: Exception) {
                onResult("OCR test failed: ${e.message}")
            }
        }
    }
    
    /**
     * Test location extraction from image EXIF data.
     * 
     * @param context Application context
     * @param imageName Name of the image in assets
     * @param onResult Callback with location result
     */
    fun testLocationExtraction(context: Context, imageName: String, onResult: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val exportedFile = exportImageFromAssets(context, imageName)
                if (exportedFile != null) {
                    // Test location extraction using ImageProcessor instance
                    val imageProcessor = ImageProcessor()
                    val hasLocation = imageProcessor.hasLocationData(exportedFile)
                    val location = imageProcessor.extractLocationFromFile(exportedFile)
                    
                    val result = if (hasLocation && location != null) {
                        "Location found: ${location.first}, ${location.second}"
                    } else {
                        "No location data found in EXIF"
                    }
                    onResult(result)
                } else {
                    onResult("Failed to export image")
                }
            } catch (e: Exception) {
                onResult("Location test failed: ${e.message}")
            }
        }
    }
}
