package com.karen_yao.chinesetravel.data.repo

import com.karen_yao.chinesetravel.data.db.AppDatabase
import com.karen_yao.chinesetravel.data.model.PlaceSnap

/**
 * Repository = simple middle layer between ViewModels and DAOs.
 */
class TravelRepository(private val db: AppDatabase) {
    fun snaps() = db.placeSnapDao().allSnaps()
    suspend fun saveSnap(s: PlaceSnap) = db.placeSnapDao().insert(s)
    suspend fun count() = db.placeSnapDao().count()
}
