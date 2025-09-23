package com.karen_yao.chinesetravel.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class PlaceSnap (
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val nameCn: String,
    val namePinyin: String,
    val lat: Double?,
    val longitude: Double?,
    val address: String?,
    val createdAt: Long = System.currentTimeMillis()
)

