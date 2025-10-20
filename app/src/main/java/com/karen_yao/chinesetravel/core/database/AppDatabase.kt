package com.karen_yao.chinesetravel.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.karen_yao.chinesetravel.core.database.dao.PlaceSnapDao
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap

/**
 * Room database for the Chinese Travel app.
 * Manages the local storage of captured place snaps.
 */
@Database(
    entities = [PlaceSnap::class], 
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeSnapDao(): PlaceSnapDao

    companion object {
        @Volatile 
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "travelsnap.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
