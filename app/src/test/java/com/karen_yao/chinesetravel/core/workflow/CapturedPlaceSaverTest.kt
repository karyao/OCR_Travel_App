package com.karen_yao.chinesetravel.core.workflow

import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.CapturedSnapStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CapturedPlaceSaverTest {
    private var written: PlaceSnap? = null
    private var writes = 0
    private val store = CapturedSnapStore { written = it; writes++; 7 }
    private val image = File("/original/photo.jpg")

    @Test fun preparesOriginalImageDetailsAndReturnsAtomicStoreCount() = runTest {
        val persistence = CapturePersistence(store) { "Translation of $it" }
        val saver = DefaultCapturedPlaceSaver(
            persistence, { "Pinyin of $it" },
            { assertEquals(image, it); 49.2 to -123.1 },
            { lat, lng -> assertEquals(49.2, lat, 0.0); assertEquals(-123.1, lng, 0.0); "Address" }
        )
        assertEquals(7, saver.save("测试文本", image))
        val snap = checkNotNull(written)
        assertEquals(image.absolutePath, snap.imagePath)
        assertEquals("测试文本", snap.nameCn)
        assertEquals("Pinyin of 测试文本", snap.namePinyin)
        assertEquals("Translation of 测试文本", snap.translation)
        assertEquals("Address", snap.address)
        assertEquals("https://www.google.com/maps/search/?api=1&query=49.2,-123.1", snap.googleMapsLink)
        assertEquals(1, writes)
    }

    @Test fun missingLocationSkipsGeocodingAndStoresNoMapLink() = runTest {
        val saver = DefaultCapturedPlaceSaver(
            CapturePersistence(store) { "Translation" }, { "Pinyin" }, { null },
            { _, _ -> error("No coordinates to geocode") }
        )
        saver.save("测试文本", image)
        assertNull(written?.lat)
        assertNull(written?.longitude)
        assertNull(written?.address)
        assertNull(written?.googleMapsLink)
        assertEquals(1, writes)
    }

    @Test fun unavailableAddressDoesNotPreventSavingCoordinates() = runTest {
        val saver = DefaultCapturedPlaceSaver(
            CapturePersistence(store) { "Translation" }, { "Pinyin" }, { 49.2 to -123.1 }, { _, _ -> null }
        )
        saver.save("测试文本", image)
        assertNull(written?.address)
        assertEquals(49.2, checkNotNull(written?.lat), 0.0)
        assertEquals(1, writes)
    }

    @Test fun translationCancellationPropagatesWithoutWriting() = runTest {
        val persistence = CapturePersistence(store) { throw CancellationException("screen closed") }
        try {
            persistence.saveAndCount("测试文本", "", null, null, null, image.absolutePath)
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            assertEquals(0, writes)
        }
    }

    @Test fun cancellationReturningFromTranslationCannotStartPersistence() = runTest {
        val persistence = CapturePersistence(store) {
            currentCoroutineContext().cancel()
            "Late result"
        }
        val work = launch { persistence.saveAndCount("测试文本", "", null, null, null, image.absolutePath) }
        work.join()
        assertTrue(work.isCancelled)
        assertEquals(0, writes)
    }
}
