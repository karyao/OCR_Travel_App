package com.karen_yao.chinesetravel.core.workflow

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared preparation and persistence boundary for capture and text selection. */
internal fun interface CapturedPlaceSaver {
    suspend fun save(chineseText: String, file: File): Int
}

/** Coordinates platform-independent work; Android services are supplied by the app. */
internal class DefaultCapturedPlaceSaver(
    private val persistence: CapturePersistence,
    private val toPinyin: (String) -> String,
    private val readLocation: suspend (File) -> Pair<Double, Double>?,
    private val reverseGeocode: suspend (Double, Double) -> String?
) : CapturedPlaceSaver {
    override suspend fun save(chineseText: String, file: File): Int {
        val pinyin = if (chineseText.isNotBlank()) toPinyin(chineseText) else ""
        val location = readLocation(file)
        currentCoroutineContext().ensureActive()
        val address = location?.let { (latitude, longitude) -> reverseGeocode(latitude, longitude) }
        currentCoroutineContext().ensureActive()
        return persistence.saveAndCount(
            chineseText, pinyin, location?.first, location?.second, address, file.absolutePath
        )
    }
}
