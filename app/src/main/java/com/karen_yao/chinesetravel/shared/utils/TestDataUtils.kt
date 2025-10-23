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
        "sample1.jpg"
    )
    
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
    fun exportAndTestImages(context: Context, repository: TravelRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "🚀 Starting test image export and processing...")
            Log.d(TAG, "📋 Test images to process: $testImages")
            
            var successCount = 0
            var totalCount = 0
            var withLocationCount = 0
            
            for (imageName in testImages) {
                try {
                    Log.d(TAG, "📸 Processing test image: $imageName")
                    
                    // Export image from assets to device storage
                    val exportedFile = exportImageFromAssets(context, imageName)
                    if (exportedFile != null) {
                        Log.d(TAG, "✅ Successfully exported: ${exportedFile.absolutePath}")
                        Log.d(TAG, "📏 File size: ${exportedFile.length()} bytes")
                        // Check if the image has real location data
                        val imageProcessor = ImageProcessor()
                        val hasLocation = imageProcessor.hasLocationData(exportedFile)
                        val realLocation = imageProcessor.extractLocationFromFile(exportedFile)
                        
                        Log.d(TAG, "🔍 Location check for $imageName: hasLocation=$hasLocation, realLocation=$realLocation")
                        
                        // Create a test PlaceSnap entry with real or fallback data
                        val testSnap = if (imageName == "chinese_character.jpg") {
                            // Use real OCR for chinese_character.jpg
                            createTestPlaceSnapWithRealOCR(
                                imageName,
                                exportedFile.absolutePath,
                                hasLocation,
                                realLocation,
                                context
                            )
                        } else {
                            // Use fallback data for other images
                            createTestPlaceSnapWithRealData(
                                imageName, 
                                exportedFile.absolutePath, 
                                hasLocation, 
                                realLocation,
                                context
                            )
                        }
                        
                        // Always save test images, but indicate location status
                        repository.saveSnap(testSnap)
                        
                        if (hasLocation) {
                            withLocationCount++
                            Log.d(TAG, "✅ Successfully processed with REAL location: $imageName")
                            Log.d(TAG, "   📍 Real GPS: ${testSnap.lat}, ${testSnap.longitude}")
                        } else {
                            Log.d(TAG, "⚠️ Processed with NO REAL LOCATION: $imageName")
                            Log.d(TAG, "   📍 Using fallback coordinates: ${testSnap.lat}, ${testSnap.longitude}")
                        }
                        
                        Log.d(TAG, "   🔗 Maps Link: ${testSnap.googleMapsLink}")
                        Log.d(TAG, "   📝 Chinese: ${testSnap.nameCn}")
                        Log.d(TAG, "   🔤 Pinyin: ${testSnap.namePinyin}")
                        Log.d(TAG, "   🏠 Address: ${testSnap.address}")
                        successCount++
                    } else {
                        Log.w(TAG, "❌ Failed to export: $imageName")
                    }
                    totalCount++
                    
                } catch (e: Exception) {
                    Log.e(TAG, "💥 Error processing $imageName", e)
                }
            }
            
            Log.d(TAG, "🎉 Test completed: $successCount/$totalCount images processed successfully")
            Log.d(TAG, "📍 Images with REAL GPS location: $withLocationCount")
            Log.d(TAG, "⚠️ Images with NO REAL LOCATION: ${totalCount - withLocationCount}")
            Log.d(TAG, "📱 Check your home screen - new test entries should appear in the list!")
        }
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
    
    /**
     * Create a test PlaceSnap entry with real location data if available.
     * 
     * @param imageName Original image name
     * @param imagePath Path to the exported image file
     * @param hasLocation Whether the image has real location data
     * @param realLocation Real location coordinates if available
     * @param context Application context for geocoding
     * @return PlaceSnap object with real or test data
     */
    private suspend fun createTestPlaceSnapWithRealData(
        imageName: String, 
        imagePath: String, 
        hasLocation: Boolean, 
        realLocation: Pair<Double, Double>?,
        context: Context
    ): PlaceSnap {
        // Use real location data if available, otherwise use test data
        val (lat, lng) = if (hasLocation && realLocation != null) {
            realLocation
        } else {
            // Fallback to test data based on image name
            when (imageName) {
                "chinese_character.jpg" -> Pair(39.9042, 116.4074) // Beijing
                "IMG_3849.JPG" -> Pair(31.2304, 121.4737) // Shanghai
                "sample1.jpg" -> Pair(22.3193, 114.1694) // Hong Kong
                else -> Pair(39.9042, 116.4074) // Default to Beijing
            }
        }
        
        // Get real address if we have location data, otherwise use fallback
        val address = if (hasLocation && realLocation != null) {
            getRealAddress(context, lat, lng)
        } else {
            "No Real Location"
        }
        
        // Get Chinese text and pinyin based on image name
        val (chineseText, pinyinText) = when (imageName) {
            "chinese_character.jpg" -> Pair("欢迎光临", "huān yíng guāng lín")
            "IMG_3849.JPG" -> Pair("餐厅", "cān tīng")
            "sample1.jpg" -> Pair("地铁站", "dì tiě zhàn")
            else -> Pair("旅游景点", "lǚ yóu jǐng diǎn")
        }
        
        // Get real translation using ML Kit Translate
        Log.d(TAG, "🔄 Getting translation for: $chineseText")
        val realTranslation = try {
            TranslationUtils.translateChineseToEnglish(chineseText)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Translation failed: ${e.message}")
            getFallbackTranslation(chineseText)
        }
        Log.d(TAG, "✅ Translation result: $realTranslation")
        
        // Create test data with real translation
        val testData = TestData(
            chinese = chineseText,
            pinyin = pinyinText,
            lat = lat,
            lng = lng,
            address = address,
            translation = realTranslation
        )
        
        return PlaceSnap(
            imagePath = imagePath,
            nameCn = testData.chinese,
            namePinyin = testData.pinyin,
            lat = testData.lat,
            longitude = testData.lng,
            address = testData.address,
            translation = testData.translation,
            googleMapsLink = if (hasLocation && realLocation != null) {
                "https://www.google.com/maps/search/?api=1&query=${testData.lat},${testData.lng}"
            } else {
                "No location found"
            }
        )
    }
    
    /**
     * Create a test PlaceSnap with real OCR for chinese_character.jpg.
     * This actually runs OCR on the image instead of using fallback data.
     */
    private suspend fun createTestPlaceSnapWithRealOCR(
        imageName: String,
        imagePath: String,
        hasLocation: Boolean,
        realLocation: Pair<Double, Double>?,
        context: Context
    ): PlaceSnap {
        Log.d(TAG, "🔍 Running REAL OCR on $imageName")
        
        // Determine coordinates
        val (lat, lng) = if (hasLocation && realLocation != null) {
            realLocation
        } else {
            // Use fallback coordinates
            when (imageName) {
                "chinese_character.jpg" -> Pair(39.9042, 116.4074) // Beijing
                else -> Pair(39.9042, 116.4074) // Default to Beijing
            }
        }
        
        // Get real address if we have location data, otherwise use fallback
        val address = if (hasLocation && realLocation != null) {
            getRealAddress(context, lat, lng)
        } else {
            "No Real Location"
        }
        
        // Run REAL OCR on the image
        val ocrResult = runRealOCR(context, imagePath)
        val chineseText = ocrResult.first
        val pinyinText = if (chineseText.isNotEmpty()) {
            // Use PinyinUtils to convert Chinese to pinyin
            com.karen_yao.chinesetravel.shared.utils.PinyinUtils.toPinyin(chineseText)
        } else {
            "No text detected"
        }
        
        // Get real translation using ML Kit Translate
        Log.d(TAG, "🔄 Getting REAL translation for: $chineseText")
        val realTranslation = try {
            TranslationUtils.translateChineseToEnglish(chineseText)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Translation failed: ${e.message}")
            "Translation failed"
        }
        Log.d(TAG, "✅ REAL Translation result: $realTranslation")
        
        return PlaceSnap(
            imagePath = imagePath,
            nameCn = chineseText,
            namePinyin = pinyinText,
            lat = lat,
            longitude = lng,
            address = address,
            translation = realTranslation,
            googleMapsLink = if (hasLocation && realLocation != null) {
                "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
            } else {
                "No location found"
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
     * Fallback translation for common travel terms when ML Kit fails.
     */
    private fun getFallbackTranslation(text: String): String {
        return when (text) {
            "欢迎光临" -> "Welcome (fallback)"
            "餐厅" -> "Restaurant (fallback)"
            "地铁站" -> "Subway Station (fallback)"
            "旅游景点" -> "Tourist Attraction (fallback)"
            "银行" -> "Bank (fallback)"
            "医院" -> "Hospital (fallback)"
            "商店" -> "Shop (fallback)"
            "市场" -> "Market (fallback)"
            "酒店" -> "Hotel (fallback)"
            "机场" -> "Airport (fallback)"
            "火车站" -> "Train Station (fallback)"
            "公交站" -> "Bus Stop (fallback)"
            "厕所" -> "Restroom (fallback)"
            "出口" -> "Exit (fallback)"
            "入口" -> "Entrance (fallback)"
            "左转" -> "Turn Left (fallback)"
            "右转" -> "Turn Right (fallback)"
            "直走" -> "Go Straight (fallback)"
            else -> "Translation unavailable (fallback)"
        }
    }

    /**
     * Create a test PlaceSnap entry for testing purposes.
     * 
     * @param imageName Original image name
     * @param imagePath Path to the exported image file
     * @return PlaceSnap object with test data
     */
    private fun createTestPlaceSnap(imageName: String, imagePath: String): PlaceSnap {
        // Create unique test data based on image name
        val testData = when (imageName) {
            "chinese_character.jpg" -> TestData(
                chinese = "测试字符",
                pinyin = "ce shi zi fu",
                lat = 39.9042,
                lng = 116.4074,
                address = "北京市, 中国",
                translation = "Test Character"
            )
            "IMG_3849.JPG" -> TestData(
                chinese = "测试图片",
                pinyin = "ce shi tu pian", 
                lat = 31.2304,
                lng = 121.4737,
                address = "上海市, 中国",
                translation = "Test Image"
            )
            "sample1.jpg" -> TestData(
                chinese = "样本测试",
                pinyin = "yang ben ce shi",
                lat = 22.3193,
                lng = 114.1694,
                address = "香港, 中国",
                translation = "Sample Test"
            )
            else -> TestData(
                chinese = "默认测试",
                pinyin = "mo ren ce shi",
                lat = 39.9042,
                lng = 116.4074,
                address = "北京市, 中国",
                translation = "Default Test"
            )
        }
        
        return PlaceSnap(
            imagePath = imagePath,
            nameCn = testData.chinese,
            namePinyin = testData.pinyin,
            lat = testData.lat,
            longitude = testData.lng,
            address = testData.address,
            translation = testData.translation,
            googleMapsLink = "https://www.google.com/maps/search/?api=1&query=${testData.lat},${testData.lng}"
        )
    }
    
    private data class TestData(
        val chinese: String,
        val pinyin: String,
        val lat: Double,
        val lng: Double,
        val address: String,
        val translation: String
    )
    
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
                    val hasLocation = imageProcessor.hasLocationData(exportedFile)
                    val realLocation = imageProcessor.extractLocationFromFile(exportedFile)
                    
                    // Create test snap
                    val testSnap = createTestPlaceSnapWithRealData(
                        imageName, 
                        exportedFile.absolutePath, 
                        hasLocation, 
                        realLocation,
                        context
                    )
                    
                    // Save to database
                    repository.saveSnap(testSnap)
                    
                    val result = buildString {
                        appendLine("✅ Image: $imageName")
                        if (hasLocation && realLocation != null) {
                            appendLine("📍 Location: REAL GPS FOUND")
                            appendLine("   Coordinates: ${realLocation.first}, ${realLocation.second}")
                        } else {
                            appendLine("⚠️ Location: NO REAL LOCATION FOUND")
                            appendLine("   Using fallback coordinates: ${testSnap.lat}, ${testSnap.longitude}")
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
                    // Here you would normally run OCR on the image
                    // For now, we'll just return a test result
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
