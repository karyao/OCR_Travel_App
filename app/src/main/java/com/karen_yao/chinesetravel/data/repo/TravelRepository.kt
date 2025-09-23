package com.karen_yao.chinesetravel.data.repo

import com.karen_yao.chinesetravel.data.db.AppDatabase
import com.karen_yao.chinesetravel.data.model.PlaceSnap

class TravelRepository(private val db: AppDatabase) {
    fun snaps() = db.placeSnapDao().allSnaps()
    suspend fun saveSnap(s: PlaceSnap) = db.placeSnapDao().insert(s)
}

