package com.karen_yao.chinesetravel.shared.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDemoFixturesTest {

    @Test
    fun fixturesContainThreeMappedRecordsAndOneUnmappedControl() {
        val fixtures = TestDataUtils.createMapDemoFixtures(createdAt = 1_000L)

        assertEquals(4, fixtures.size)
        assertEquals(4, fixtures.map { it.id }.distinct().size)
        assertEquals(3, fixtures.count { it.lat != null && it.longitude != null })

        val unlocated = fixtures.single { it.lat == null && it.longitude == null }
        assertNull(unlocated.address)
        assertNull(unlocated.googleMapsLink)
    }

    @Test
    fun mappedFixturesHaveStableIdsAndValidCoordinates() {
        val firstRun = TestDataUtils.createMapDemoFixtures(createdAt = 1_000L)
        val secondRun = TestDataUtils.createMapDemoFixtures(createdAt = 2_000L)

        assertEquals(firstRun.map { it.id }, secondRun.map { it.id })
        assertTrue(firstRun.all { it.id.startsWith("test-map-fixture-") })

        firstRun.filter { it.lat != null && it.longitude != null }.forEach { fixture ->
            assertTrue(fixture.lat!! in -90.0..90.0)
            assertTrue(fixture.longitude!! in -180.0..180.0)
            assertTrue(!fixture.googleMapsLink.isNullOrBlank())
        }
    }
}
