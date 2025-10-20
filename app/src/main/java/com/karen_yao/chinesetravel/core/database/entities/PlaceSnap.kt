package com.karen_yao.chinesetravel.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a captured place with Chinese text recognition.
 * Stores image path, recognized Chinese text, pinyin, location data, and translation.
 */
@Entity
data class PlaceSnap(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val nameCn: String,
    val namePinyin: String,
    val lat: Double?,
    val longitude: Double?,
    val address: String?,
    val translation: String,
    val googleMapsLink: String?,
    val createdAt: Long = System.currentTimeMillis()
)
