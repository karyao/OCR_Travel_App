package com.karen_yao.chinesetravel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.karen_yao.chinesetravel.data.model.PlaceSnap

@Database(entities = [PlaceSnap::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeSnapDao(): PlaceSnapDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "travelsnap.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

