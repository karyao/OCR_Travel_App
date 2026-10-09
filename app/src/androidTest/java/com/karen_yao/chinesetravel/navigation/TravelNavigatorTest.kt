package com.karen_yao.chinesetravel.navigation

import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.activity.OnBackPressedDispatcher
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karen_yao.chinesetravel.MainActivity
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.repository.LocatedSnapSource
import com.karen_yao.chinesetravel.core.workflow.CapturedPlaceSaver
import com.karen_yao.chinesetravel.debug.CaptureTestHostActivity
import com.karen_yao.chinesetravel.features.capture.camera.OcrOutcome
import com.karen_yao.chinesetravel.features.capture.ui.CaptureCamera
import com.karen_yao.chinesetravel.features.capture.ui.CaptureCameraError
import com.karen_yao.chinesetravel.features.capture.ui.CaptureDependencies
import com.karen_yao.chinesetravel.features.capture.ui.CaptureFragment
import com.karen_yao.chinesetravel.features.capture.ui.CaptureGallerySource
import com.karen_yao.chinesetravel.features.capture.ui.CaptureLocationSource
import com.karen_yao.chinesetravel.features.capture.ui.CapturePermissionChecker
import com.karen_yao.chinesetravel.features.capture.ui.CaptureTextRecognizer
import com.karen_yao.chinesetravel.features.capture.ui.CaptureWorkflowDependencies
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.features.map.ui.MapFragment
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionDependencies
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionViewModel
import com.karen_yao.chinesetravel.features.tutorial.ui.TutorialFragment
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TravelNavigatorTest {
    @Before fun setUp() {
        CaptureTestHostActivity.initialFragmentFactory = { WelcomeFragment() }
        CaptureTestHostActivity.mapSourceFactory = { LocatedSnapSource { flowOf(emptyList()) } }
        CaptureTestHostActivity.dependenciesFactory = { context, _ -> navigationCaptureDependencies(context) }
        CaptureTestHostActivity.selectionDependenciesFactory = { context, _ ->
            TextSelectionDependencies(context.filesDir, CapturedPlaceSaver { _, _ -> error("Unexpected save") })
        }
    }

    @After fun tearDown() {
        CaptureTestHostActivity.initialFragmentFactory = null
        CaptureTestHostActivity.mapSourceFactory = null
        CaptureTestHostActivity.dependenciesFactory = null
        CaptureTestHostActivity.selectionDependenciesFactory = null
    }

    @Test fun welcomeTutorialAndHomeReplaceScreensWithoutChangingHistory() = withHost { activity ->
        val navigator = activity.travelNavigator
        navigator.openTutorial()
        activity.supportFragmentManager.executePendingTransactions()
        assertTrue(activity.currentFragment is TutorialFragment)
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        navigator.openHome()
        activity.supportFragmentManager.executePendingTransactions()
        assertTrue(activity.currentFragment is HomeFragment)
        activity.pushPlaceholder("older")
        for ((open, destination) in listOf(
            navigator::openHome to HomeFragment::class.java,
            navigator::openWelcome to WelcomeFragment::class.java,
            navigator::openTutorial to TutorialFragment::class.java
        )) {
            open()
            activity.supportFragmentManager.executePendingTransactions()
            assertTrue(destination.isInstance(activity.currentFragment))
            assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
            assertEquals("older", activity.supportFragmentManager.getBackStackEntryAt(0).name)
        }
    }

    @Test fun captureAndMapPushEntriesAndBackRestoresTheSameHome() = withHost { activity ->
        val navigator = activity.travelNavigator
        navigator.openHome()
        activity.supportFragmentManager.executePendingTransactions()
        val home = activity.currentFragment
        navigator.openCapture()
        assertSame("Navigation must not commit synchronously", home, activity.currentFragment)
        activity.supportFragmentManager.executePendingTransactions()
        assertTrue(activity.currentFragment is CaptureFragment)
        assertEquals(TravelNavigator.CAPTURE_BACK_STACK_NAME, activity.supportFragmentManager.getBackStackEntryAt(0).name)
        navigator.goBack()
        activity.supportFragmentManager.executePendingTransactions()
        assertSame(home, activity.currentFragment)
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
        navigator.openMap()
        activity.supportFragmentManager.executePendingTransactions()
        assertTrue(activity.currentFragment is MapFragment)
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        assertNull(activity.supportFragmentManager.getBackStackEntryAt(0).name)
        navigator.closeMap()
        activity.supportFragmentManager.executePendingTransactions()
        assertSame(home, activity.currentFragment)
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
    }

    @Test fun selectionForwardsArgumentsAndSystemBackReturnsToCaptureWithoutRecursion() {
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            lateinit var capture: Fragment
            scenario.onActivity { activity ->
                activity.travelNavigator.openHome()
                activity.supportFragmentManager.executePendingTransactions()
                activity.travelNavigator.openCapture()
                activity.supportFragmentManager.executePendingTransactions()
                capture = activity.currentFragment
                activity.travelNavigator.openTextSelection(listOf("甲乙", "丙丁"), "", "丙丁", true)
                activity.supportFragmentManager.executePendingTransactions()
                val selection = activity.currentFragment as TextSelectionFragment
                val arguments = selection.requireArguments()
                assertArrayEquals(arrayOf("甲乙", "丙丁"), arguments.getStringArray(TextSelectionViewModel.ARG_LINES))
                assertEquals("", arguments.getString(TextSelectionViewModel.ARG_IMAGE_PATH))
                assertEquals("丙丁", arguments.getString(TextSelectionViewModel.ARG_SELECTED_TEXT))
                assertTrue(arguments.getBoolean(TextSelectionViewModel.ARG_OWNED))
                assertEquals(2, activity.supportFragmentManager.backStackEntryCount)
                assertNull(activity.supportFragmentManager.getBackStackEntryAt(1).name)
                activity.onBackPressedDispatcher.onBackPressed()
            }
            awaitFragment<CaptureFragment>(scenario)
            scenario.onActivity {
                assertSame(capture, it.currentFragment)
                assertEquals(1, it.supportFragmentManager.backStackEntryCount)
            }
        }
    }

    @Test fun successfulReturnRestoresExistingHomeAndPreservesHistoryBelowCapture() = withHost { activity ->
        activity.travelNavigator.openHome()
        activity.supportFragmentManager.executePendingTransactions()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.container, HomeFragment()).addToBackStack("older").commit()
        activity.supportFragmentManager.executePendingTransactions()
        val home = activity.currentFragment
        activity.openCaptureAndSelection()
        activity.travelNavigator.returnHomeAfterSave()
        activity.supportFragmentManager.executePendingTransactions()
        assertSame(home, activity.currentFragment)
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        assertEquals("older", activity.supportFragmentManager.getBackStackEntryAt(0).name)
    }

    @Test fun successfulReturnUsesTheMostRecentCaptureMarker() = withHost { activity ->
        activity.travelNavigator.openHome()
        activity.supportFragmentManager.executePendingTransactions()
        activity.travelNavigator.openCapture()
        activity.supportFragmentManager.executePendingTransactions()
        val olderCaptureId = activity.supportFragmentManager.getBackStackEntryAt(0).id
        activity.travelNavigator.openHome()
        activity.supportFragmentManager.executePendingTransactions()
        val mostRecentHome = activity.currentFragment
        activity.openCaptureAndSelection()
        activity.travelNavigator.returnHomeAfterSave()
        activity.supportFragmentManager.executePendingTransactions()
        assertSame(mostRecentHome, activity.currentFragment)
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        assertEquals(olderCaptureId, activity.supportFragmentManager.getBackStackEntryAt(0).id)
    }

    @Test fun successfulStandaloneAndLegacyReturnsClearHistoryAndOpenHome() {
        for (legacyStack in listOf(false, true)) withHost { activity ->
            if (legacyStack) {
                activity.pushPlaceholder(null)
                activity.pushPlaceholder(null)
            }
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.container, TextSelectionFragment.newInstance(emptyList(), "", ""))
                .apply { if (legacyStack) addToBackStack(null) }.commit()
            activity.supportFragmentManager.executePendingTransactions()
            activity.travelNavigator.returnHomeAfterSave()
            activity.supportFragmentManager.executePendingTransactions()
            assertTrue(activity.currentFragment is HomeFragment)
            assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
            assertFalse(activity.supportFragmentManager.popBackStackImmediate())
        }
    }

    @Test fun mapCloseWithNoHistoryDoesNotInvokeBackDispatcher() = withHost { activity ->
        var backCalls = 0
        val navigator = TravelNavigator(activity.supportFragmentManager, OnBackPressedDispatcher { backCalls++ })
        navigator.openMap()
        activity.supportFragmentManager.executePendingTransactions()
        navigator.closeMap()
        activity.supportFragmentManager.executePendingTransactions()
        assertTrue(activity.currentFragment is MapFragment)
        assertEquals(0, backCalls)
        navigator.goBack()
        assertEquals(1, backCalls)
    }

    @Test fun recreationCreatesANewNavigatorAndRestoredCaptureMarkerStillReturnsHome() {
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            lateinit var originalNavigator: TravelNavigator
            scenario.onActivity {
                originalNavigator = it.travelNavigator
                it.travelNavigator.openHome()
                it.supportFragmentManager.executePendingTransactions()
                it.openCaptureAndSelection()
            }
            scenario.recreate()
            awaitFragment<TextSelectionFragment>(scenario)
            scenario.onActivity {
                assertNotSame(originalNavigator, it.travelNavigator)
                assertEquals("capture", it.supportFragmentManager.getBackStackEntryAt(0).name)
                it.travelNavigator.returnHomeAfterSave()
                it.supportFragmentManager.executePendingTransactions()
                assertTrue(it.currentFragment is HomeFragment)
                assertEquals(0, it.supportFragmentManager.backStackEntryCount)
            }
        }
    }

    @Test fun mainActivityRecreationDoesNotReplaceRestoredHomeWithWelcome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var originalNavigator: TravelNavigator
            scenario.onActivity {
                originalNavigator = it.travelNavigator
                it.travelNavigator.openHome()
                it.supportFragmentManager.executePendingTransactions()
            }
            scenario.recreate()
            scenario.onActivity {
                assertNotSame(originalNavigator, it.travelNavigator)
                assertTrue(it.supportFragmentManager.findFragmentById(R.id.container) is HomeFragment)
            }
        }
    }

    @Test fun savedStateIsExposedAndRejectedNavigationIsNotQueuedOrSilentlyAccepted() {
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity {
                assertTrue(it.travelNavigator.isStateSaved)
                assertThrows(IllegalStateException::class.java) { it.travelNavigator.openHome() }
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertFalse(it.travelNavigator.isStateSaved)
                it.supportFragmentManager.executePendingTransactions()
                assertTrue(it.currentFragment is WelcomeFragment)
                it.travelNavigator.openHome()
                it.supportFragmentManager.executePendingTransactions()
                assertTrue(it.currentFragment is HomeFragment)
            }
        }
    }

    private fun withHost(action: (CaptureTestHostActivity) -> Unit) {
        ActivityScenario.launch(CaptureTestHostActivity::class.java).use { it.onActivity(action) }
    }

    private val CaptureTestHostActivity.currentFragment: Fragment
        get() = requireNotNull(supportFragmentManager.findFragmentById(R.id.container))

    private fun CaptureTestHostActivity.pushPlaceholder(name: String?) {
        supportFragmentManager.beginTransaction().replace(R.id.container, Fragment()).addToBackStack(name).commit()
        supportFragmentManager.executePendingTransactions()
    }

    private fun CaptureTestHostActivity.openCaptureAndSelection() {
        travelNavigator.openCapture()
        supportFragmentManager.executePendingTransactions()
        travelNavigator.openTextSelection(listOf("甲乙", "丙丁"), "", "")
        supportFragmentManager.executePendingTransactions()
    }

    private inline fun <reified T : Fragment> awaitFragment(scenario: ActivityScenario<CaptureTestHostActivity>) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { matched = it.currentFragment is T }
            if (!matched) SystemClock.sleep(25L)
        }
        assertTrue("Navigation did not reach ${T::class.java.simpleName}", matched)
    }
}

/** Navigation tests must not request permissions or start camera, OCR, GPS, or persistence. */
private fun navigationCaptureDependencies(context: Context) = CaptureDependencies(
    permissions = object : CapturePermissionChecker {
        override fun hasCameraPermission(context: Context) = true
        override fun hasLocationPermission(context: Context) = true
        override fun isCameraAvailable(context: Context) = false
    },
    camera = object : CaptureCamera {
        override fun start(context: Context, previewView: PreviewView, lifecycleOwner: LifecycleOwner,
            onError: (String) -> Unit, onReady: () -> Unit): Unit = error("Unexpected camera binding")
        override fun switch(context: Context, previewView: PreviewView, lifecycleOwner: LifecycleOwner,
            onError: (String) -> Unit, onReady: () -> Unit): Unit = error("Unexpected camera switch")
        override fun takePhoto(file: File, location: Location?, executor: Executor,
            onSaved: () -> Unit, onError: (CaptureCameraError) -> Unit): Unit = error("Unexpected photo capture")
        override fun stop() = Unit
        override fun isReady() = false
        override fun isBackCamera() = true
    },
    createWorkflow = {
        CaptureWorkflowDependencies(
            filesRoot = context.filesDir,
            location = CaptureLocationSource { error("Unexpected location request") },
            recognizer = object : CaptureTextRecognizer {
                override suspend fun recognize(originalFile: File): OcrOutcome = error("Unexpected OCR")
                override fun close() = Unit
            },
            galleryImporter = CaptureGallerySource { error("Unexpected gallery import") },
            saver = CapturedPlaceSaver { _, _ -> error("Unexpected save") }
        )
    }
)
