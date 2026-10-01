package com.karen_yao.chinesetravel.features.map.ui

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.repository.LocatedSnapSource
import com.karen_yao.chinesetravel.debug.CaptureTestHostActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
            }
            loaded.complete(Unit)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.mapLoadingIndicator).visibility)
                assertEquals(it.getString(R.string.map_empty_title), it.findViewById<TextView>(R.id.tvMapStateTitle).text)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.mapEmptyScroll).visibility)
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
            }
        }
    }
}
