package com.karen_yao.chinesetravel.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.karen_yao.chinesetravel.core.database.dao.PlaceSnapDao
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.TravelRepository
import com.karen_yao.chinesetravel.shared.utils.TestDataUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceSnapDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PlaceSnapDao

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.placeSnapDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun snapsWithLocationIncludesBoundariesAndExcludesMissingOrInvalidCoordinates() = runBlocking {
        listOf(
            snap("valid", 49.2827, -123.1207),
            snap("minimum-boundary", -90.0, -180.0),
            snap("maximum-boundary", 90.0, 180.0),
            snap("missing-latitude", null, 10.0),
            snap("missing-longitude", 10.0, null),
            snap("invalid-latitude", 90.1, 10.0),
            snap("invalid-longitude", 10.0, 180.1)
        ).forEach { dao.insert(it) }

        val ids = dao.snapsWithLocation().first().map { it.id }.toSet()

        assertEquals(setOf("valid", "minimum-boundary", "maximum-boundary"), ids)
    }

    @Test
    fun reseedingMapFixturesIsIdempotentAndPreservesUnrelatedRecords() = runBlocking {
        val repository = TravelRepository(database)
        val unrelated = snap("unrelated", 48.8566, 2.3522)
        dao.insert(unrelated)

        TestDataUtils.seedMapDemoData(repository)
        TestDataUtils.seedMapDemoData(repository)

        val allSnaps = dao.allSnaps().first()
        val mapSnaps = dao.snapsWithLocation().first()

        assertEquals(5, allSnaps.size)
        assertEquals(5, allSnaps.map { it.id }.distinct().size)
        assertEquals(4, mapSnaps.size)
        assertTrue(allSnaps.any { it.id == unrelated.id })
    }

    private fun snap(id: String, lat: Double?, longitude: Double?) = PlaceSnap(
        id = id,
        imagePath = "",
        nameCn = id,
        namePinyin = id,
        lat = lat,
        longitude = longitude,
        address = null,
        translation = id,
        googleMapsLink = null,
        createdAt = 1L
    )
}
