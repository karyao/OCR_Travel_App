package com.karen_yao.chinesetravel.features.map.ui

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.core.repository.LocatedSnapSource
import com.karen_yao.chinesetravel.debug.CaptureTestHostActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView

@RunWith(AndroidJUnit4::class)
class MapFragmentBehaviorTest {
    @Before fun setUp() { CaptureTestHostActivity.initialFragmentFactory = { MapFragment() } }
    @After fun tearDown() {
        CaptureTestHostActivity.initialFragmentFactory = null
        CaptureTestHostActivity.mapSourceFactory = null
    }

    @Test fun loadingBecomesEmptyOnlyWhenRepositoryEmits() {
        val loaded = CompletableDeferred<Unit>()
        CaptureTestHostActivity.mapSourceFactory = {
            LocatedSnapSource { flow { loaded.await(); emit(emptyList()) } }
        }
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.mapLoadingIndicator).visibility)
                assertEquals(it.getString(R.string.map_loading), it.findViewById<TextView>(R.id.tvMapStateTitle).text)
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapView).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapZoomControls).visibility)
            }
            loaded.complete(Unit)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapLoadingIndicator).visibility)
                assertEquals(it.getString(R.string.map_empty_title), it.findViewById<TextView>(R.id.tvMapStateTitle).text)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.mapEmptyScroll).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapZoomControls).visibility)
            }
        }
    }

    @Test fun loadFailureShowsErrorInsteadOfAnEmptyMap() {
        CaptureTestHostActivity.mapSourceFactory = { LocatedSnapSource { flow { error("unavailable") } } }
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(it.getString(R.string.map_load_error_title), it.findViewById<TextView>(R.id.tvMapStateTitle).text)
                assertEquals(it.getString(R.string.map_load_error_message), it.findViewById<TextView>(R.id.tvMapStateMessage).text)
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapLoadingIndicator).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapView).visibility)
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapZoomControls).visibility)
            }
        }
    }

    @Test fun zoomControlsFollowPopulatedEmptyAndPopulatedStates() {
        val snaps = MutableStateFlow(listOf(locatedSnap()))
        CaptureTestHostActivity.mapSourceFactory = { LocatedSnapSource { snaps } }
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            awaitControls(scenario, View.VISIBLE)
            snaps.value = emptyList()
            awaitControls(scenario, View.GONE)
            snaps.value = listOf(locatedSnap())
            awaitControls(scenario, View.VISIBLE)
        }
    }

    @Test fun recreatingWithPendingFadeDisposesOldControlsAndNewControlsStillWork() {
        val snaps = MutableStateFlow(listOf(locatedSnap()))
        CaptureTestHostActivity.mapSourceFactory = { LocatedSnapSource { snaps } }
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            awaitControls(scenario, View.VISIBLE)
            lateinit var oldZoomIn: View
            lateinit var oldZoomOut: View
            scenario.onActivity {
                // Start a fresh fade deadline immediately before destroying this view.
                it.findViewById<MapView>(R.id.mapView).controller.setZoom(16.0)
                oldZoomIn = it.findViewById(R.id.btnZoomIn)
                oldZoomOut = it.findViewById(R.id.btnZoomOut)
                assertEquals(1f, oldZoomIn.alpha, 0f)
            }
            scenario.recreate()
            awaitControls(scenario, View.VISIBLE)
            val elapsed = CountDownLatch(1)
            val callback = Runnable { elapsed.countDown() }
            lateinit var root: View
            scenario.onActivity {
                root = it.window.decorView
                root.postDelayed(callback, 2_750L)
            }
            try {
                assertTrue("Old fade deadline did not elapse", elapsed.await(5, TimeUnit.SECONDS))
                scenario.onActivity {
                    assertFalse(oldZoomIn.hasOnClickListeners())
                    assertFalse(oldZoomOut.hasOnClickListeners())
                    assertEquals(1f, oldZoomIn.alpha, 0f)
                    assertEquals(1f, oldZoomOut.alpha, 0f)
                    val newZoomIn = it.findViewById<View>(R.id.btnZoomIn)
                    assertNotSame(oldZoomIn, newZoomIn)
                    newZoomIn.performClick()
                    assertEquals(1f, newZoomIn.alpha, 0f)
                }
            } finally {
                scenario.onActivity { root.removeCallbacks(callback) }
            }
        }
    }

    private fun awaitControls(scenario: ActivityScenario<CaptureTestHostActivity>, visibility: Int) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { matched = it.findViewById<View>(R.id.mapZoomControls).visibility == visibility }
            if (!matched) SystemClock.sleep(25L)
        }
        assertTrue("Map controls did not reach visibility $visibility", matched)
    }

    private fun locatedSnap() = PlaceSnap(
        imagePath = "", nameCn = "测试文本", namePinyin = "cè shì", translation = "Test text",
        lat = 49.2, longitude = -123.1, address = "Address", googleMapsLink = null
    )
}
