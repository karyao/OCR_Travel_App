package com.karen_yao.chinesetravel.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PlaceSnap table.
 * Room generates the implementation from these annotations.
 */
@Dao
interface PlaceSnapDao {

    @Query("SELECT * FROM PlaceSnap ORDER BY createdAt DESC")
    fun allSnaps(): Flow<List<PlaceSnap>>

    @Insert
    suspend fun insert(snap: PlaceSnap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snaps: List<PlaceSnap>)
    
    @Delete
    suspend fun delete(snap: PlaceSnap)

    /** Debug/utility: count total rows in the table. */
    @Query("SELECT COUNT(*) FROM PlaceSnap")
    suspend fun count(): Int
    
    /** Clear all snaps from the database. */
    @Query("DELETE FROM PlaceSnap")
    suspend fun clearAll()

    @Query("DELETE FROM PlaceSnap WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Get all snaps that have valid GPS coordinates for map display. */
    @Query(
        """
        SELECT * FROM PlaceSnap
        WHERE lat BETWEEN -90.0 AND 90.0
          AND longitude BETWEEN -180.0 AND 180.0
        ORDER BY createdAt DESC
        """
    )
    fun snapsWithLocation(): Flow<List<PlaceSnap>>
}
