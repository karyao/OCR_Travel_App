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
    fun replacingLocationTestsIsAtomicIdempotentAndPreservesUnrelatedRecords() = runBlocking {
        val repository = TravelRepository(database)
        val unrelated = snap("unrelated", 48.8566, 2.3522)
        dao.insert(unrelated)

        val first = TestDataUtils.createPhotoLocationRecord(
            imageName = "IMG_3950.JPG",
            imagePath = "",
            location = 49.2827 to -123.1207,
            createdAt = 1L
        )
        val second = first.copy(lat = null, longitude = null, googleMapsLink = null, createdAt = 2L)
        val ids = listOf(first.id) + TestDataUtils.LEGACY_MAP_FIXTURE_IDS

        repository.replaceSnapsByIds(ids, listOf(first))
        repository.replaceSnapsByIds(ids, listOf(second))

        val allSnaps = dao.allSnaps().first()
        val mapSnaps = dao.snapsWithLocation().first()

        assertEquals(2, allSnaps.size)
        assertEquals(2, allSnaps.map { it.id }.distinct().size)
        assertEquals(1, mapSnaps.size)
        assertTrue(allSnaps.any { it.id == unrelated.id })
        assertTrue(allSnaps.single { it.id == first.id }.lat == null)
    }

    @Test
    fun saveSnapAndCountPersistsAndReturnsCountInOneTransaction() = runBlocking {
        val repository = TravelRepository(database)
        dao.insert(snap("existing", null, null))

        val count = repository.saveSnapAndCount(snap("new", 49.2827, -123.1207))

        assertEquals(2, count)
        assertEquals(setOf("existing", "new"), dao.allSnaps().first().map { it.id }.toSet())
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
