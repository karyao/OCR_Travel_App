package com.karen_yao.chinesetravel.shared.utils

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.text.DateFormat
import java.util.Date

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

    internal data class OcrSampleResult(
        val imageName: String,
        val imagePath: String,
        val detectedLines: List<String>
    )

    internal val LEGACY_MAP_FIXTURE_IDS = listOf(
        "test-map-fixture-beijing",
        "test-map-fixture-shanghai",
        "test-map-fixture-hong-kong",
        "test-map-fixture-unlocated"
    )
    internal const val DEVICE_LOCATION_TEST_ID = "test-device-location-current"
    private const val PHOTO_LOCATION_ID_PREFIX = "test-photo-location-"
    private const val REAL_PIPELINE_ID_PREFIX = "test-real-pipeline-"
    private const val OCR_SAMPLE_ID_PREFIX = "test-ocr-sample-"

    internal val photoLocationTestIds: List<String>
        get() = testImages.map(::photoLocationTestId)

    internal fun photoLocationTestId(imageName: String): String =
        PHOTO_LOCATION_ID_PREFIX + imageName.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun realPipelineTestId(imageName: String): String =
        REAL_PIPELINE_ID_PREFIX + imageName.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    internal fun createPhotoLocationRecord(
        imageName: String,
        imagePath: String,
        location: Pair<Double, Double>?,
        createdAt: Long = System.currentTimeMillis()
    ): PlaceSnap {
        val latitude = location?.first
        val longitude = location?.second
        return PlaceSnap(
            id = photoLocationTestId(imageName),
            imagePath = imagePath,
            nameCn = "照片位置测试",
            namePinyin = "Zhàopiàn wèizhì cèshì",
            lat = latitude,
            longitude = longitude,
            address = null,
            translation = if (location != null) {
                "$imageName • EXIF photo location"
            } else {
                "$imageName • No EXIF location"
            },
            googleMapsLink = if (latitude != null && longitude != null) {
                "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
            } else {
                null
            },
            createdAt = createdAt
        )
    }

    internal fun createDeviceLocationTestRecord(
        latitude: Double,
        longitude: Double,
        isMock: Boolean,
        createdAt: Long = System.currentTimeMillis()
    ): PlaceSnap {
        val timestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(createdAt))
        return PlaceSnap(
            id = DEVICE_LOCATION_TEST_ID,
            imagePath = "",
            nameCn = "设备位置测试",
            namePinyin = "Shèbèi wèizhì cèshì",
            lat = latitude,
            longitude = longitude,
            address = null,
            translation = if (isMock) {
                "Simulated device location • $timestamp"
            } else {
                "Current device location • $timestamp"
            },
            googleMapsLink =
                "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude",
            createdAt = createdAt
        )
    }

    /** Reads only EXIF metadata; this test performs no OCR, translation, or geocoding. */
    internal suspend fun testPhotoLocations(
        context: Context,
        repository: TravelRepository
    ): TestRunSummary = withContext(Dispatchers.IO) {
        val records = mutableListOf<PlaceSnap>()
        var failed = 0

        testImages.forEachIndexed { index, imageName ->
            try {
                val file = exportImageFromAssets(context, imageName)
                if (file == null) {
                    failed++
                } else {
                    val location = ImageProcessor().extractLocationFromFile(file)
                    records += createPhotoLocationRecord(
                        imageName = imageName,
                        imagePath = file.absolutePath,
                        location = location,
                        createdAt = System.currentTimeMillis() - index
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                failed++
                Log.e(TAG, "Photo-location test failed for $imageName", exception)
            }
        }

        repository.replaceSnapsByIds(
            idsToReplace = photoLocationTestIds + LEGACY_MAP_FIXTURE_IDS,
            replacements = records
        )
        TestRunSummary(
            processed = testImages.size,
            inserted = records.size,
            located = records.count { it.lat != null && it.longitude != null },
            unlocated = records.count { it.lat == null || it.longitude == null },
            failed = failed
        )
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
                if (e is CancellationException) throw e
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
        val records = mutableListOf<PlaceSnap>()

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
                        id = realPipelineTestId(imageName),
                        imagePath = exportedFile.absolutePath,
                        realLocation = realLocation,
                        context = context
                    )

                    records += testSnap
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "💥 Error processing $imageName", e)
            }
        }

        repository.replaceSnapsByIds(
            idsToReplace = testImages.map(::realPipelineTestId) + LEGACY_MAP_FIXTURE_IDS,
            replacements = records
        )

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

    internal suspend fun recognizeOcrSample(
        context: Context,
        imageName: String
    ): OcrSampleResult = withContext(Dispatchers.IO) {
        val sourceFile = exportImageFromAssets(context, imageName)
            ?: error("Could not export $imageName")
        val processedFile = ImageProcessor().preprocessImageForOCR(sourceFile, context.cacheDir)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
        )

        try {
            val image = com.google.mlkit.vision.common.InputImage.fromFilePath(
                context,
                android.net.Uri.fromFile(processedFile)
            )
            val lines = recognizer.process(image).await().text
                .lines()
                .map(String::trim)
                .filter(String::isNotEmpty)
            OcrSampleResult(imageName, sourceFile.absolutePath, lines)
        } finally {
            recognizer.close()
            if (processedFile != sourceFile) processedFile.delete()
        }
    }

    internal suspend fun saveOcrSampleSelection(
        context: Context,
        repository: TravelRepository,
        result: OcrSampleResult,
        selectedText: String
    ) = withContext(Dispatchers.IO) {
        val file = File(result.imagePath)
        val location = ImageProcessor().extractLocationFromFile(file)
        val address = location?.let { (latitude, longitude) ->
            getRealAddress(context, latitude, longitude)
        }
        val translation = TranslationUtils.translateChineseToEnglish(selectedText)
        val id = OCR_SAMPLE_ID_PREFIX + result.imageName.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        val record = PlaceSnap(
            id = id,
            imagePath = result.imagePath,
            nameCn = selectedText,
            namePinyin = PinyinUtils.toPinyin(selectedText),
            lat = location?.first,
            longitude = location?.second,
            address = address,
            translation = translation,
            googleMapsLink = location?.let { (latitude, longitude) ->
                "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
            }
        )
        repository.replaceSnapsByIds(listOf(id), listOf(record))
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
            // These files back database test rows, so keep them in durable app storage.
            val testDirectory = File(context.filesDir, "test-images").apply { mkdirs() }
            val outputFile = File(testDirectory, "test_$imageName")
            
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
        id: String,
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Translation failed: ${e.message}")
            "Translation failed"
        }
        Log.d(TAG, "✅ Translation result: $realTranslation")

        return PlaceSnap(
            id = id,
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
            
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
            )
            try {
                val detectedText = recognizer.process(image).await().text
                Log.d(TAG, "📝 OCR detected text: '$detectedText'")

                val chineseText = detectedText.filter {
                    it.toString().matches(Regex("[\\p{IsHan}]"))
                }
                Log.d(TAG, "🔤 Chinese characters found: '$chineseText'")
                Pair(chineseText, detectedText)
            } finally {
                recognizer.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ OCR failed: ${e.message}")
            Pair("OCR failed", "")
        }
    }
}
