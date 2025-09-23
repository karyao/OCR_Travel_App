package com.karen_yao.chinesetravel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.karen_yao.chinesetravel.data.model.PlaceSnap
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceSnapDao {
    @Query("SELECT * FROM PlaceSnap ORDER BY createdAt DESC")
    fun allSnaps(): Flow<List<PlaceSnap>>

    @Insert
    suspend fun insert(snap: PlaceSnap)
}
