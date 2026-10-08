package com.karen_yao.chinesetravel.features.map.ui

import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.debug.CaptureTestHostActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView

@RunWith(AndroidJUnit4::class)
class MapZoomControlsTest {
    @Before fun setUp() {
        Configuration.getInstance().userAgentValue =
            InstrumentationRegistry.getInstrumentation().targetContext.packageName
        // A plain Fragment hosts real map widgets without attaching another controls helper.
        CaptureTestHostActivity.initialFragmentFactory = { Fragment(R.layout.fragment_map) }
    }

    @After fun tearDown() {
        CaptureTestHostActivity.initialFragmentFactory = null
    }

    @Test fun attachKeepsControlsHiddenAndProgrammaticZoomUpdatesButtonLimits() = withControls { fixture ->
        onMain {
            assertEquals(View.GONE, fixture.controls.visibility)
            fixture.helper.show()
            fixture.map.controller.setZoom(6.0)
            assertFalse(fixture.zoomIn.isEnabled)
            assertTrue(fixture.zoomOut.isEnabled)
            fixture.map.controller.setZoom(4.0)
            assertTrue(fixture.zoomIn.isEnabled)
            assertFalse(fixture.zoomOut.isEnabled)
        }
    }

    @Test fun clicksZoomAndOnlyButtonsFadeUntilMapActivityRevealsThem() = withControls { fixture ->
        onMain {
            fixture.helper.show()
            fixture.zoomIn.performClick()
        }
        awaitCondition { fixture.map.zoomLevelDouble == 6.0 && !fixture.map.isAnimating }
        onMain { fixture.zoomOut.performClick() }
        awaitCondition { fixture.map.zoomLevelDouble == 5.0 && !fixture.map.isAnimating }
        awaitCondition { fixture.zoomIn.alpha <= 0.41f && fixture.zoomOut.alpha <= 0.41f }
        onMain {
            assertEquals(1f, fixture.controls.alpha, 0f)
            fixture.map.controller.setZoom(5.5)
            assertEquals(1f, fixture.zoomIn.alpha, 0f)
            assertEquals(1f, fixture.zoomOut.alpha, 0f)
        }
    }

    @Test fun hidingCancelsPendingFadeAndShowingRestoresControls() = withControls { fixture ->
        onMain {
            fixture.helper.show()
            fixture.helper.hide()
        }
        afterFadeDeadline(fixture.root) {
            assertEquals(View.GONE, fixture.controls.visibility)
            assertEquals(1f, fixture.zoomIn.alpha, 0f)
            assertEquals(1f, fixture.zoomOut.alpha, 0f)
            fixture.helper.show()
            assertEquals(View.VISIBLE, fixture.controls.visibility)
            assertTrue(fixture.zoomIn.isEnabled)
            assertTrue(fixture.zoomOut.isEnabled)
        }
    }

    @Test fun disposalCancelsFadeAndRemovesOnlyItsOwnMapListener() = withControls { fixture ->
        var otherZoomEvents = 0
        val otherListener = object : MapListener {
            override fun onScroll(event: ScrollEvent) = false
            override fun onZoom(event: ZoomEvent): Boolean {
                otherZoomEvents++
                return false
            }
        }
        onMain {
            fixture.map.addMapListener(otherListener)
            fixture.helper.show()
            fixture.helper.dispose()
            fixture.helper.dispose()
            fixture.helper.hide()
            fixture.helper.show()
            assertEquals(View.VISIBLE, fixture.controls.visibility)
            assertFalse(fixture.zoomIn.hasOnClickListeners())
            assertFalse(fixture.zoomOut.hasOnClickListeners())
            fixture.map.controller.setZoom(6.0)
            assertTrue("Other map listeners must stay attached", otherZoomEvents > 0)
            assertTrue("Disposed controls must not process zoom events", fixture.zoomIn.isEnabled)
        }
        afterFadeDeadline(fixture.root) {
            assertEquals(1f, fixture.zoomIn.alpha, 0f)
            assertEquals(1f, fixture.zoomOut.alpha, 0f)
            fixture.map.removeMapListener(otherListener)
        }
    }

    private fun withControls(block: (Fixture) -> Unit) {
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentById(R.id.container)!!.requireView()
                val map = root.findViewById<MapView>(R.id.mapView)
                map.visibility = View.VISIBLE
                map.setUseDataConnection(false)
                map.setMinZoomLevel(4.0)
                map.setMaxZoomLevel(6.0)
                map.controller.setZoom(5.0)
                fixture = Fixture(
                    root, map, root.findViewById(R.id.mapZoomControls),
                    root.findViewById(R.id.btnZoomIn), root.findViewById(R.id.btnZoomOut),
                    MapZoomControls.attach(root, map)
                )
            }
            try {
                awaitCondition { fixture.map.zoomLevelDouble == 5.0 }
                block(fixture)
            } finally {
                onMain {
                    fixture.helper.dispose()
                    fixture.map.onDetach()
                }
            }
        }
    }

    private data class Fixture(
        val root: View,
        val map: MapView,
        val controls: View,
        val zoomIn: MaterialButton,
        val zoomOut: MaterialButton,
        val helper: MapZoomControls
    )

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            onMain { matched = condition() }
            if (!matched) SystemClock.sleep(25L)
        }
        assertTrue("Map controls did not reach the expected state", matched)
    }

    private fun afterFadeDeadline(root: View, assertion: () -> Unit) {
        val elapsed = CountDownLatch(1)
        val callback = Runnable { elapsed.countDown() }
        onMain { root.postDelayed(callback, 2_750L) }
        try {
            assertTrue("Fade deadline did not elapse", elapsed.await(5, TimeUnit.SECONDS))
            onMain(assertion)
        } finally {
            onMain { root.removeCallbacks(callback) }
        }
    }

    private fun onMain(action: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
}
