package com.karen_yao.chinesetravel.core.repository

import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap

/**
 * Repository pattern implementation for travel data.
 * Provides a clean interface between UI and data layer.
 */
class TravelRepository(private val database: AppDatabase) {
    
    /**
     * Get all captured snaps as a Flow for reactive UI updates.
     */
    fun getAllSnaps() = database.placeSnapDao().allSnaps()
    
    /**
     * Save a new place snap to the database.
     */
    suspend fun saveSnap(snap: PlaceSnap) = database.placeSnapDao().insert(snap)
    
    /**
     * Get the total count of saved snaps.
     */
    suspend fun getSnapCount() = database.placeSnapDao().count()
}
