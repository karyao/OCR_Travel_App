package com.karen_yao.chinesetravel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.karen_yao.chinesetravel.data.model.PlaceSnap
import kotlinx.coroutines.flow.Flow

/**
 * DAO = Data Access Object for PlaceSnap table.
 * Room generates the implementation from these annotations.
 */
@Dao
interface PlaceSnapDao {

    @Query("SELECT * FROM PlaceSnap ORDER BY createdAt DESC")
    fun allSnaps(): Flow<List<PlaceSnap>>

    @Insert
    suspend fun insert(snap: PlaceSnap)

    /** Debug/utility: count total rows in the table. */
    @Query("SELECT COUNT(*) FROM PlaceSnap")
    suspend fun count(): Int
}
