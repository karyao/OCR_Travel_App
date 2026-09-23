package com.karen_yao.chinesetravel.shared.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDemoFixturesTest {

    @Test
    fun photoLocationRecordUsesOnlyProvidedExifCoordinates() {
        val located = TestDataUtils.createPhotoLocationRecord(
            imageName = "sample.JPG",
            imagePath = "/tmp/sample.JPG",
            location = 49.2827 to -123.1207,
            createdAt = 1_000L
        )
        val unlocated = TestDataUtils.createPhotoLocationRecord(
            imageName = "without-gps.JPG",
            imagePath = "/tmp/without-gps.JPG",
            location = null,
            createdAt = 1_000L
        )

        assertEquals(49.2827, located.lat!!, 0.0)
        assertEquals(-123.1207, located.longitude!!, 0.0)
        assertTrue(located.translation.contains("EXIF photo location"))
        assertNull(unlocated.lat)
        assertNull(unlocated.longitude)
        assertNull(unlocated.address)
        assertNull(unlocated.googleMapsLink)
    }

    @Test
    fun photoAndDeviceTestIdsAreStableAndSourceLabelsAreExplicit() {
        assertEquals(
            TestDataUtils.photoLocationTestId("IMG_3950.JPG"),
            TestDataUtils.photoLocationTestId("IMG_3950.JPG")
        )
        assertTrue(TestDataUtils.photoLocationTestId("IMG_3950.JPG").startsWith("test-photo-location-"))

        val device = TestDataUtils.createDeviceLocationTestRecord(
            latitude = 49.2827,
            longitude = -123.1207,
            isMock = true,
            createdAt = 1_000L
        )
        assertEquals(TestDataUtils.DEVICE_LOCATION_TEST_ID, device.id)
        assertTrue(device.translation.startsWith("Simulated device location"))
        assertTrue(device.lat!! in -90.0..90.0)
        assertTrue(device.longitude!! in -180.0..180.0)
    }
}
