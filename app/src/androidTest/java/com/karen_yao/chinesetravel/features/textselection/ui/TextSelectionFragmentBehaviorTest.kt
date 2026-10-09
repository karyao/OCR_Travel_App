package com.karen_yao.chinesetravel.features.textselection.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.workflow.CapturedPlaceSaver
import com.karen_yao.chinesetravel.debug.CaptureTestHostActivity
import com.karen_yao.chinesetravel.navigation.TravelNavigator
import com.karen_yao.chinesetravel.features.home.ui.HomeFragment
import com.karen_yao.chinesetravel.shared.ui.awaitPreviewBitmap
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextSelectionFragmentBehaviorTest {
    private lateinit var root: File
    private lateinit var photo: File
    private val calls = AtomicInteger()
    private val started = CountDownLatch(1)
    private var save: suspend () -> Int = { 3 }
    private var owned = true

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = Files.createTempDirectory(context.filesDir.toPath(), "selection-test-").toFile()
        photo = File(root, "photo.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        CaptureTestHostActivity.initialFragmentFactory = { HomeFragment() }
        CaptureTestHostActivity.selectionDependenciesFactory = { _, _ ->
            TextSelectionDependencies(root, CapturedPlaceSaver { text, file ->
                assertEquals("永庆坊", text)
                assertEquals(photo, file)
                calls.incrementAndGet()
                started.countDown()
                save()
            })
        }
    }
    @After fun tearDown() {
        CaptureTestHostActivity.selectionDependenciesFactory = null
        CaptureTestHostActivity.initialFragmentFactory = null
        root.deleteRecursively()
    }

    @Test fun selectionAndHighlightSurviveRotation() {
        launch().use { scenario ->
            awaitPreviewBitmap(R.id.ivCapturedImage)
            selectSecond(scenario)
            awaitUi(scenario) { it.findViewById<Button>(R.id.btnConfirmSelection).isEnabled }
            scenario.recreate()
            awaitUi(scenario) {
                val row = it.findViewById<RecyclerView>(R.id.rvTextOptions).findViewHolderForAdapterPosition(1)
                it.findViewById<Button>(R.id.btnConfirmSelection).isEnabled &&
                    row?.itemView?.findViewById<View>(R.id.tvSelected)?.visibility == View.VISIBLE
            }
            awaitPreviewBitmap(R.id.ivCapturedImage)
            assertTrue(photo.exists())
            assertEquals(0, calls.get())
        }
    }

    @Test fun completedPreviewSurvivesBackgroundResumeWithoutChangingThePhoto() {
        val original = photo.readBytes()
        launch().use { scenario ->
            awaitPreviewBitmap(R.id.ivCapturedImage)
            var image: android.graphics.drawable.Drawable? = null
            scenario.onActivity { image = it.findViewById<ImageView>(R.id.ivCapturedImage).drawable }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitPreviewBitmap(R.id.ivCapturedImage)
            scenario.onActivity { assertSame(image, it.findViewById<ImageView>(R.id.ivCapturedImage).drawable) }
            assertArrayEquals(original, photo.readBytes())
        }
    }

    @Test fun saveContinuesThroughRotationAndClearsFinishedBackStack() {
        val gate = CompletableDeferred<Int>()
        save = { gate.await() }
        launch().use { scenario ->
            selectSecond(scenario)
            scenario.onActivity {
                val confirm = it.findViewById<Button>(R.id.btnConfirmSelection)
                confirm.performClick()
                confirm.performClick()
                it.onBackPressedDispatcher.onBackPressed()
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            awaitUi(scenario) {
                !it.findViewById<Button>(R.id.btnConfirmSelection).isEnabled &&
                    !it.findViewById<Button>(R.id.btnCancel).isEnabled
            }
            scenario.recreate()
            awaitUi(scenario) {
                it.supportFragmentManager.findFragmentById(R.id.container) is TextSelectionFragment &&
                    !it.findViewById<Button>(R.id.btnConfirmSelection).isEnabled &&
                    it.findViewById<Button>(R.id.btnConfirmSelection).text.toString() ==
                    it.getString(R.string.text_selection_saving)
            }
            gate.complete(3)
            awaitUi(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) is HomeFragment }
            scenario.onActivity {
                assertEquals(0, it.supportFragmentManager.backStackEntryCount)
                assertFalse(it.supportFragmentManager.popBackStackImmediate())
                assertTrue(it.supportFragmentManager.findFragmentById(R.id.container) is HomeFragment)
            }
            assertEquals(1, calls.get())
            assertTrue(photo.exists())
        }
    }

    @Test fun successfulNavigationWaitsUntilResumed() {
        val gate = CompletableDeferred<Int>()
        save = { gate.await() }
        launch().use { scenario ->
            selectSecond(scenario)
            scenario.onActivity { it.findViewById<Button>(R.id.btnConfirmSelection).performClick() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            scenario.moveToState(Lifecycle.State.CREATED)
            gate.complete(3)
            // Wait for the completion on main while the UI collector is stopped.
            awaitUi(scenario) {
                val selection = it.supportFragmentManager.findFragmentById(R.id.container) as? TextSelectionFragment
                selection != null && androidx.lifecycle.ViewModelProvider(selection)[TextSelectionViewModel::class.java]
                    .uiState.value.status == TextSelectionStatus.SAVED
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUi(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) is HomeFragment }
            assertEquals(1, calls.get())
        }
    }

    @Test fun cancelHeaderAndSystemBackReleaseOwnedPhotoOnce() {
        for (exit in 0..2) {
            if (!photo.exists()) photo.writeBytes(byteArrayOf(1, 2, 3))
            launch().use { scenario ->
                scenario.onActivity {
                    when (exit) {
                        0 -> {
                            val cancel = it.findViewById<View>(R.id.btnCancel)
                            repeat(2) { cancel.performClick() }
                        }
                        1 -> it.findViewById<View>(R.id.headerLayout).findViewById<View>(R.id.btnBack).performClick()
                        else -> it.onBackPressedDispatcher.onBackPressed()
                    }
                }
                awaitUi(scenario) {
                    it.supportFragmentManager.findFragmentById(R.id.container) !is TextSelectionFragment && !photo.exists()
                }
                scenario.onActivity { assertEquals(1, it.supportFragmentManager.backStackEntryCount) }
                assertEquals(0, calls.get())
            }
        }
    }

    @Test fun unownedManagedPhotoIsNotDeletedOnCancel() {
        owned = false
        launch().use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnCancel).performClick() }
            awaitUi(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) !is TextSelectionFragment }
            assertTrue(photo.exists())
        }
    }

    @Test fun failedSaveAllowsExitButRetainsPhotoAndPreventsRetry() {
        save = { error("uncertain commit") }
        launch().use { scenario ->
            selectSecond(scenario)
            scenario.onActivity { it.findViewById<Button>(R.id.btnConfirmSelection).performClick() }
            awaitUi(scenario) {
                it.findViewById<Button>(R.id.btnCancel).isEnabled &&
                    !it.findViewById<Button>(R.id.btnConfirmSelection).isEnabled
            }
            scenario.onActivity {
                it.findViewById<Button>(R.id.btnConfirmSelection).performClick()
                it.onBackPressedDispatcher.onBackPressed()
            }
            awaitUi(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) !is TextSelectionFragment }
            assertEquals(1, calls.get())
            assertTrue(photo.exists())
        }
    }

    @Test fun standaloneSuccessReturnsHomeWithoutLeavingSelectionOnBackStack() {
        launch(withHomeStack = false).use { scenario ->
            selectSecond(scenario)
            scenario.onActivity { it.findViewById<Button>(R.id.btnConfirmSelection).performClick() }
            awaitUi(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) is HomeFragment }
            scenario.onActivity { assertEquals(0, it.supportFragmentManager.backStackEntryCount) }
            assertEquals(1, calls.get())
            assertTrue(photo.exists())
        }
    }

    @Test fun olderUnnamedStackIsClearedAfterSuccessfulSave() {
        launch(namedCapture = false).use { scenario ->
            selectSecond(scenario)
            scenario.onActivity { it.findViewById<Button>(R.id.btnConfirmSelection).performClick() }
            awaitUi(scenario) { it.supportFragmentManager.findFragmentById(R.id.container) is HomeFragment }
            scenario.onActivity {
                assertEquals(0, it.supportFragmentManager.backStackEntryCount)
                assertFalse(it.supportFragmentManager.popBackStackImmediate())
            }
            assertEquals(1, calls.get())
        }
    }

    private fun launch(withHomeStack: Boolean = true, namedCapture: Boolean = true): ActivityScenario<CaptureTestHostActivity> {
        return ActivityScenario.launch(CaptureTestHostActivity::class.java).also { scenario ->
            scenario.onActivity {
                val manager = it.supportFragmentManager
                if (withHomeStack) manager.beginTransaction().setReorderingAllowed(true)
                    .replace(R.id.container, Fragment()).addToBackStack(if (namedCapture) TravelNavigator.CAPTURE_BACK_STACK_NAME else null).commit()
                manager.beginTransaction().setReorderingAllowed(true)
                    .replace(R.id.container, TextSelectionFragment.newInstance(
                        listOf("八邑酒楼", "永庆坊"), photo.absolutePath, "", owned
                    )).apply { if (withHomeStack) addToBackStack("selection") }.commit()
                manager.executePendingTransactions()
            }
            awaitUi(scenario) { it.findViewById<RecyclerView>(R.id.rvTextOptions)?.findViewHolderForAdapterPosition(1) != null }
        }
    }
    private fun selectSecond(scenario: ActivityScenario<CaptureTestHostActivity>) {
        scenario.onActivity {
            it.findViewById<RecyclerView>(R.id.rvTextOptions).findViewHolderForAdapterPosition(1)!!.itemView.performClick()
        }
    }
    private fun awaitUi(scenario: ActivityScenario<CaptureTestHostActivity>, condition: (CaptureTestHostActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { matched = condition(it) }
            if (!matched) SystemClock.sleep(50)
        }
        assertTrue("Text selection did not reach the expected state", matched)
    }
}
