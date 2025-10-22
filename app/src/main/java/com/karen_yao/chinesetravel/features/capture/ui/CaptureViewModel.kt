package com.karen_yao.chinesetravel.features.capture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.shared.utils.TranslationUtils

/**
 * ViewModel for the Capture feature.
 * Handles saving captured place snaps to the database.
 */
class CaptureViewModel(private val repository: TravelRepository) : ViewModel() {

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
        address: String,
        imagePath: String
    ): Int {
        // Get real translation using ML Kit Translate
        val realTranslation = TranslationUtils.translateChineseToEnglish(chineseText)
        
        val googleMapsLink = if (latitude != null && longitude != null) {
            "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        } else "No location found"
        
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
        
        repository.saveSnap(placeSnap)
        return repository.getSnapCount()
    }
}

/**
 * Factory for creating CaptureViewModel instances.
 */
class CaptureViewModelFactory(private val repository: TravelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CaptureViewModel(repository) as T
    }
}
