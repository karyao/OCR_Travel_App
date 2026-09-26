package com.karen_yao.chinesetravel.core.repository

import com.karen_yao.chinesetravel.core.database.AppDatabase
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * The collection operations required by the Home feature.
 *
 * Keeping this contract independent from Room lets HomeViewModel be tested with
 * an in-memory fake while TravelRepository remains the production implementation.
 */
interface SnapRepository {
    fun getAllSnaps(): Flow<List<PlaceSnap>>
    suspend fun clearAllSnaps()
    suspend fun deleteSnap(snap: PlaceSnap)
}

/**
 * Repository pattern implementation for travel data.
 * Provides a clean interface between UI and data layer.
 */
class TravelRepository(private val database: AppDatabase) : SnapRepository {
    
    /**
     * Get all captured snaps as a Flow for reactive UI updates.
     */
    override fun getAllSnaps() = database.placeSnapDao().allSnaps()
    
    /**
     * Save a new place snap to the database.
     */
    suspend fun saveSnap(snap: PlaceSnap) = database.placeSnapDao().insert(snap)

    /** Atomically saves a snap and returns the new total. */
    suspend fun saveSnapAndCount(snap: PlaceSnap): Int = database.withTransaction {
        database.placeSnapDao().insert(snap)
        database.placeSnapDao().count()
    }
    
    /**
     * Get the total count of saved snaps.
     */
    suspend fun getSnapCount() = database.placeSnapDao().count()
    
    /**
     * Clear all snaps from the database.
     */
    override suspend fun clearAllSnaps() = database.placeSnapDao().clearAll()
    
    /**
     * Delete a specific snap from the database.
     */
    override suspend fun deleteSnap(snap: PlaceSnap) = database.placeSnapDao().delete(snap)

    /** Atomically replaces only the explicitly named records. */
    suspend fun replaceSnapsByIds(idsToReplace: List<String>, replacements: List<PlaceSnap>) {
        database.withTransaction {
            database.placeSnapDao().deleteByIds(idsToReplace)
            database.placeSnapDao().insertAll(replacements)
        }
    }

    /**
     * Get all snaps with valid GPS coordinates for map display.
     */
    fun getSnapsWithLocation() = database.placeSnapDao().snapsWithLocation()
}
