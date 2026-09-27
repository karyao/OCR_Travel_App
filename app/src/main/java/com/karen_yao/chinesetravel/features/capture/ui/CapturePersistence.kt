package com.karen_yao.chinesetravel.features.capture.ui

import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.shared.utils.TranslationUtils

/**
 * Shared persistence step used by capture and the separate text-selection flow.
 */
internal class CapturePersistence(private val repository: TravelRepository) {

    /**
     * Save a captured place snap and return the total count.
     * 
     * @param chineseText The recognized Chinese text
     * @param pinyinText The pinyin representation
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @param address The address string
     * @param imagePath The path to the captured image
     * @return Total number of saved snaps
     */
    suspend fun saveAndCount(
        chineseText: String,
        pinyinText: String,
        latitude: Double?,
        longitude: Double?,
        address: String?,
        imagePath: String
    ): Int {
        // Get real translation using ML Kit Translate
        val realTranslation = TranslationUtils.translateChineseToEnglish(chineseText)
        
        val googleMapsLink = if (latitude != null && longitude != null) {
            "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        } else null
        
        val placeSnap = PlaceSnap(
            imagePath = imagePath,
            nameCn = chineseText,
            namePinyin = pinyinText,
            lat = latitude,
            longitude = longitude,
            address = address,
            translation = realTranslation,
            googleMapsLink = googleMapsLink
        )
        
        return repository.saveSnapAndCount(placeSnap)
    }
}


