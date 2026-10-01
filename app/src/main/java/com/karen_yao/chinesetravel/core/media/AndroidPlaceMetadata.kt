package com.karen_yao.chinesetravel.core.media

import android.content.Context
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android adapters for optional image coordinates and address lookup. */
internal class AndroidPlaceMetadata(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun readLocation(file: File): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        readImageLocation(file)
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            Geocoder(applicationContext, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}

internal fun readImageLocation(file: File): Pair<Double, Double>? = try {
    ExifInterface(file.absolutePath).latLong?.let { it[0] to it[1] }
} catch (_: Exception) {
    null
}
