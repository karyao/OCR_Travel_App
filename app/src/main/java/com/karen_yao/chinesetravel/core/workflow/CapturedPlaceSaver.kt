package com.karen_yao.chinesetravel.core.workflow

import android.content.Context
import android.location.Geocoder
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.features.capture.camera.ImageProcessor
import com.karen_yao.chinesetravel.shared.utils.PinyinUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Shared preparation and persistence boundary for both capture and text selection. */
internal fun interface CapturedPlaceSaver {
    suspend fun save(chineseText: String, file: File): Int
}

internal fun productionCapturedPlaceSaver(
    context: Context,
    repository: TravelRepository,
    imageProcessor: ImageProcessor = ImageProcessor()
): CapturedPlaceSaver = AndroidCapturedPlaceSaver(
    context.applicationContext, imageProcessor, CapturePersistence(repository)
)

private class AndroidCapturedPlaceSaver(
    private val context: Context,
    private val imageProcessor: ImageProcessor,
    private val persistence: CapturePersistence
) : CapturedPlaceSaver {
    override suspend fun save(
        chineseText: String,
        file: File
    ): Int {
        val pinyin = if (chineseText.isNotBlank()) PinyinUtils.toPinyin(chineseText) else ""
        val location = withContext(Dispatchers.IO) {
            imageProcessor.extractLocationFromFile(file)
        }
        val address = location?.let { (latitude, longitude) ->
            reverseGeocode(context.applicationContext, latitude, longitude)
        }
        return persistence.saveAndCount(
            chineseText = chineseText,
            pinyinText = pinyin,
            latitude = location?.first,
            longitude = location?.second,
            address = address,
            imagePath = file.absolutePath
        )
    }

    private suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
    }
}

