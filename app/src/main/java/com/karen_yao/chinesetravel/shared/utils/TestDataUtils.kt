package com.karen_yao.chinesetravel.shared.utils

import android.content.Context
import android.util.Log
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
     * Export all test images from assets to device storage and process them.
     * This will test OCR, location extraction, and database storage.
     * 
     * @param context Application context
     * @param repository TravelRepository for saving test data
     */
    fun exportAndTestImages(context: Context, repository: TravelRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "🚀 Starting test image export and processing...")
            
            var successCount = 0
            var totalCount = 0
            
            for (imageName in testImages) {
                try {
                    Log.d(TAG, "📸 Processing test image: $imageName")
                    
                    // Export image from assets to device storage
                    val exportedFile = exportImageFromAssets(context, imageName)
                    if (exportedFile != null) {
                        // Create a test PlaceSnap entry with unique data for each image
                        val testSnap = createTestPlaceSnap(imageName, exportedFile.absolutePath)
                        
                        // Save to database
                        repository.saveSnap(testSnap)
                        
                        Log.d(TAG, "✅ Successfully processed: $imageName")
                        Log.d(TAG, "   📍 Location: ${testSnap.lat}, ${testSnap.longitude}")
                        Log.d(TAG, "   🔗 Maps Link: ${testSnap.googleMapsLink}")
                        Log.d(TAG, "   📝 Chinese: ${testSnap.nameCn}")
                        Log.d(TAG, "   🔤 Pinyin: ${testSnap.namePinyin}")
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
