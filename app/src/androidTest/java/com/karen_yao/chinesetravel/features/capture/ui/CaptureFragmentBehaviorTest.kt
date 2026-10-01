package com.karen_yao.chinesetravel.features.capture.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.view.View
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.debug.CaptureTestHostActivity
import com.karen_yao.chinesetravel.features.capture.camera.OcrLine
import com.karen_yao.chinesetravel.features.capture.camera.OcrOutcome
import com.karen_yao.chinesetravel.features.capture.camera.OcrPass
import com.karen_yao.chinesetravel.features.textselection.ui.TextSelectionFragment
import com.karen_yao.chinesetravel.features.welcome.ui.WelcomeFragment
import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureFragmentBehaviorTest {
    private lateinit var camera: FakeCaptureCamera
    private lateinit var permissions: FakeCapturePermissionChecker
    private lateinit var recognizer: FakeCaptureTextRecognizer
    private lateinit var saver: FakeCaptureSaver
    private lateinit var testFiles: File
    private var locationPermissionChanged = false
    private var locationCalls = 0
    private var importCalls = 0
    private var locationSource: suspend () -> CaptureLocationResult = { locationResult }
    private var locationResult: CaptureLocationResult =
        CaptureLocationResult.Unavailable(
            DeviceLocationProvider.FailureReason.LOCATION_DISABLED
        )

    @Before
    fun setUp() {
        camera = FakeCaptureCamera()
        permissions = FakeCapturePermissionChecker()
        recognizer = FakeCaptureTextRecognizer(
            outcome = outcome("测试文本")
        )
        saver = FakeCaptureSaver()
        val context = ApplicationProvider.getApplicationContext<Context>()
        testFiles = Files.createTempDirectory(context.filesDir.toPath(), "capture-behavior-").toFile()
        CaptureTestHostActivity.dependenciesFactory = { _, _ ->
            dependencies()
        }
    }

    @After
    fun tearDown() {
        CaptureTestHostActivity.dependenciesFactory = null
        if (locationPermissionChanged) resetLocationPermission()
        testFiles.deleteRecursively()
    }

    @Test
    fun secondCaptureTapIsIgnoredWhileFirstCaptureIsPending() {
        recognizer.outcome = outcome("字")
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val capture = activity.findViewById<View>(R.id.btnShoot)
                capture.performClick()
                capture.performClick()

                assertEquals(1, camera.captureRequests)
                assertFalse(capture.isEnabled)
            }
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.btnGallery).isEnabled)
                assertFalse(activity.findViewById<View>(R.id.btnSwitchCamera).isEnabled)
                camera.complete()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertTrue(it.findViewById<View>(R.id.btnShoot).isEnabled)
                assertTrue(it.findViewById<View>(R.id.btnGallery).isEnabled)
            }
        }
    }

    @Test
    fun unavailableLocationStillCapturesWithoutCoordinates() {
        locationResult = CaptureLocationResult.Unavailable(
            DeviceLocationProvider.FailureReason.TIMED_OUT
        )

        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btnShoot).performClick()

                assertEquals(1, camera.captureRequests)
                assertNull(camera.lastLocation)
            }
        }
    }

    @Test
    fun deniedLocationPermissionSurvivesPauseResumeAndCapturesOnce() =
        permissionRoundTrip(destroyBeforeReady = false)

    @Test
    fun destroyingViewWhileAwaitingCameraPreventsLaterCapture() =
        permissionRoundTrip(destroyBeforeReady = true)

    private fun permissionRoundTrip(destroyBeforeReady: Boolean) {
        locationPermissionChanged = true
        resetLocationPermission()
        permissions.readLocationPermissionFromSystem = true
        camera.delayAfterFirstStart = true
        val fragmentPaused = CountDownLatch(1)
        val fragmentResumedAfterPause = CountDownLatch(1)
        val sawPause = AtomicBoolean(false)

        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentPaused(
                            fragmentManager: FragmentManager,
                            fragment: Fragment
                        ) {
                            if (fragment is CaptureFragment) {
                                sawPause.set(true)
                                fragmentPaused.countDown()
                            }
                        }

                        override fun onFragmentResumed(
                            fragmentManager: FragmentManager,
                            fragment: Fragment
                        ) {
                            if (fragment is CaptureFragment && sawPause.get()) {
                                fragmentResumedAfterPause.countDown()
                            }
                        }
                    },
                    false
                )
                activity.findViewById<View>(R.id.btnShoot).performClick()
            }

            assertTrue(
                "Location permission dialog did not pause CaptureFragment",
                fragmentPaused.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            assertEquals(0, camera.captureRequests)

            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val denyButton = device.wait(
                Until.findObject(By.res(PERMISSION_DENY_BUTTON)),
                UI_TIMEOUT_MILLIS
            ) ?: device.wait(
                Until.findObject(By.res(PERMISSION_DENY_AND_DONT_ASK_BUTTON)),
                UI_TIMEOUT_MILLIS
            )
            assertTrue("Location permission deny button was not shown", denyButton != null)
            denyButton?.click()

            assertTrue(
                "CaptureFragment did not resume after permission denial",
                fragmentResumedAfterPause.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            scenario.onActivity { activity ->
                assertEquals(0, camera.captureRequests)
                assertFalse(activity.findViewById<View>(R.id.btnShoot).isEnabled)
                assertStatus(activity.findViewById(R.id.container), R.string.capture_waiting_for_camera)
                if (destroyBeforeReady) {
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.container, WelcomeFragment()).commitNow()
                }
                camera.releaseReadiness()
                camera.releaseReadiness()
            }
            if (destroyBeforeReady) {
                scenario.onActivity {
                    assertEquals(0, camera.captureRequests)
                    assertTrue(it.supportFragmentManager.findFragmentById(R.id.container) is WelcomeFragment)
                }
                return@use
            }
            assertTrue(
                "Camera capture did not continue after permission denial",
                camera.captureRequested.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )

            scenario.onActivity {
                assertEquals(1, camera.captureRequests)
                assertNull(camera.lastLocation)
                assertTrue(camera.stopCalls >= 1)
                assertTrue(camera.startCalls >= 2)
            }
        }
    }

    @Test
    fun multipleRecognizedLinesOpenTextSelectionWithOriginalImage() {
        val expectedLines = arrayOf("八邑酒楼", "永庆坊")
        recognizer.outcome = outcome(*expectedLines)
        camera.completeImmediately = true
        val selectionShown = CountDownLatch(1)

        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentResumed(
                            fragmentManager: FragmentManager,
                            fragment: Fragment
                        ) {
                            if (fragment is TextSelectionFragment) selectionShown.countDown()
                        }
                    },
                    false
                )
                activity.findViewById<View>(R.id.btnShoot).performClick()
            }

            assertTrue("Text selection was not shown", selectionShown.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.container)
                assertTrue(fragment is TextSelectionFragment)
                assertArrayEquals(
                    expectedLines,
                    fragment?.arguments?.getStringArray("detected_texts")
                )
                assertEquals(
                    camera.lastFile?.absolutePath,
                    fragment?.arguments?.getString("image_path")
                )
                assertTrue(checkNotNull(camera.lastFile).exists())
                activity.supportFragmentManager.popBackStackImmediate()
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.container) is CaptureFragment)
                assertTrue(activity.findViewById<View>(R.id.btnShoot).isEnabled)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.container) is CaptureFragment)
                assertEquals(1, camera.captureRequests)
            }
        }
    }

    @Test
    fun rotationCancelsSuspendedOcrAndDoesNotSaveOrNavigate() {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        recognizer = FakeCaptureTextRecognizer(outcome("甲乙", "丙丁"), gate, started, cancelled)
        camera.completeImmediately = true
        launch().use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnShoot).performClick() }
            assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            awaitStatus(R.string.capture_reading_text)
            scenario.recreate()
            assertTrue(cancelled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            gate.complete(Unit)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertEquals(0, saver.saveCalls)
                assertTrue(it.supportFragmentManager.findFragmentById(R.id.container) is CaptureFragment)
                assertTrue(it.findViewById<View>(R.id.btnShoot).isEnabled)
                assertStatus(it.findViewById(R.id.container), null)
            }
        }
    }

    @Test
    fun recognitionFinishedWhileStoppedNavigatesOnlyAfterResume() {
        val started = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        val selectionShown = CountDownLatch(1)
        recognizer = FakeCaptureTextRecognizer(outcome("甲乙", "丙丁"), gate, started)
        camera.completeImmediately = true
        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                    object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentResumed(manager: FragmentManager, fragment: Fragment) {
                            if (fragment is TextSelectionFragment) selectionShown.countDown()
                        }
                    }, false
                )
                activity.findViewById<View>(R.id.btnShoot).performClick()
            }
            assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            scenario.moveToState(Lifecycle.State.CREATED)
            gate.complete(Unit)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertTrue(it.supportFragmentManager.findFragmentById(R.id.container) is CaptureFragment)
                assertEquals(1L, selectionShown.count)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(selectionShown.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    @Test
    fun destroyingViewBeforeDelayedCameraBindingIgnoresLateReadiness() {
        camera.delayAfterFirstStart = true
        launch().use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.container, WelcomeFragment()).commitNow()
                camera.releaseReadiness()
                assertEquals(0, camera.captureRequests)
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.container) is WelcomeFragment)
            }
        }
    }

    @Test
    fun galleryCancellationRestoresIdleControls() {
        launch().use { scenario ->
            repeat(3) {
                cancelGallery(scenario)
                scenario.onActivity {
                    assertEquals(0, camera.captureRequests)
                    assertEquals(0, locationCalls)
                    assertEquals(0, importCalls)
                    assertEquals(0, recognizer.recognitionCalls)
                    assertEquals(0, saver.saveCalls)
                }
            }
        }
    }

    @Test
    fun galleryCancellationAfterCaptureDoesNotRestartServices() {
        recognizer.outcome = outcome("字")
        camera.completeImmediately = true
        launch().use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnShoot).performClick() }
            awaitReadyControls()
            scenario.onActivity {
                assertEquals(1, camera.captureRequests)
                assertEquals(1, locationCalls)
                assertEquals(1, recognizer.recognitionCalls)
            }
            cancelGallery(scenario)
            scenario.onActivity {
                assertEquals(1, camera.captureRequests)
                assertEquals(1, locationCalls)
                assertEquals(1, recognizer.recognitionCalls)
                assertEquals(0, importCalls)
                assertEquals(0, saver.saveCalls)
            }
        }
    }

    @Test
    fun locationAndCaptureStatusFollowActiveWorkAndClearOnPause() {
        val locationGate = CompletableDeferred<CaptureLocationResult>()
        locationSource = { locationGate.await() }
        launch().use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnShoot).performClick() }
            awaitStatus(R.string.capture_getting_location)
            val coordinates = CaptureLocation(49.2, -123.1)
            locationGate.complete(CaptureLocationResult.Success(coordinates))
            awaitStatus(R.string.capture_taking_photo)
            scenario.onActivity {
                assertEquals(coordinates.latitude, checkNotNull(camera.lastLocation).latitude, 0.0)
                assertEquals(coordinates.longitude, checkNotNull(camera.lastLocation).longitude, 0.0)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertStatus(it.findViewById(R.id.container), null) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertStatus(it.findViewById(R.id.container), null) }
        }
    }

    @Test
    fun readingStatusClearsWhenRecognitionFinishesOrFails() {
        for (fails in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            recognizer = FakeCaptureTextRecognizer(outcome("字"), gate).apply {
                failure = if (fails) "recognition failed" else null
            }
            camera.completeImmediately = true
            launch().use { scenario ->
                scenario.onActivity { it.findViewById<View>(R.id.btnShoot).performClick() }
                awaitStatus(R.string.capture_reading_text)
                gate.complete(Unit)
                awaitReadyControls()
                scenario.onActivity { assertStatus(it.findViewById(R.id.container), null) }
            }
        }
    }

    @Test
    fun savingStatusClearsWhenViewIsDestroyed() {
        saver.gate = CompletableDeferred()
        camera.completeImmediately = true
        launch().use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.btnShoot).performClick() }
            awaitStatus(R.string.capture_saving)
            scenario.onActivity { activity ->
                val oldRoot = checkNotNull(activity.supportFragmentManager.findFragmentById(R.id.container)?.view)
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.container, WelcomeFragment()).commitNow()
                assertStatus(oldRoot, null)
                assertEquals(1, saver.saveCalls)
            }
        }
    }

    private fun cancelGallery(scenario: ActivityScenario<CaptureTestHostActivity>) {
        scenario.onActivity { it.findViewById<View>(R.id.btnGallery).performClick() }
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Photo picker did not open", device.wait(
            Until.hasObject(By.textContains("Photos")), UI_TIMEOUT_MILLIS))
        device.pressBack()
        awaitReadyControls()
        scenario.onActivity {
            assertTrue(it.findViewById<View>(R.id.btnGallery).isEnabled)
            assertTrue(it.findViewById<View>(R.id.btnSwitchCamera).isEnabled)
            assertStatus(it.findViewById(R.id.container), null)
        }
        assertFalse(device.hasObject(By.text("No image selected")))
        assertFalse(device.hasObject(By.text("Text too short, please try again")))
        assertFalse(device.hasObject(By.text("Camera ready! Point at Chinese text")))
    }

    private fun awaitReadyControls() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Capture controls did not become ready", device.wait(
            Until.hasObject(By.res("com.karen_yao.chinesetravel:id/btnShoot").enabled(true)), UI_TIMEOUT_MILLIS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun awaitStatus(resource: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Capture status was not shown", device.wait(
            Until.hasObject(By.res("com.karen_yao.chinesetravel:id/tvCaptureStatus")
                .text(instrumentation.targetContext.getString(resource))), UI_TIMEOUT_MILLIS))
    }

    private fun assertStatus(root: View, resource: Int?) {
        val status = root.findViewById<TextView>(R.id.tvCaptureStatus)
        assertEquals(if (resource == null) View.GONE else View.VISIBLE, status.visibility)
        assertEquals(resource?.let { root.context.getString(it) }.orEmpty(), status.text.toString())
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, status.accessibilityLiveRegion)
    }

    @Test
    fun destroyedViewCancelsRecognitionBeforeSaveOrNavigation() {
        val recognitionStarted = CountDownLatch(1)
        val recognitionCancelled = CountDownLatch(1)
        recognizer = FakeCaptureTextRecognizer(
            outcome = outcome("测试文本"),
            gate = CompletableDeferred(),
            started = recognitionStarted,
            cancelled = recognitionCancelled
        )
        camera.completeImmediately = true

        launch().use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btnShoot).performClick()
            }
            assertTrue("Recognition did not start", recognitionStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.container, WelcomeFragment())
                    .commitNow()
            }

            assertTrue(
                "Recognition was not cancelled with the destroyed view",
                recognitionCancelled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            scenario.onActivity { activity ->
                assertEquals(0, saver.saveCalls)
                assertTrue(
                    activity.supportFragmentManager.findFragmentById(R.id.container) is
                        WelcomeFragment
                )
            }
        }
    }

    private fun launch(): ActivityScenario<CaptureTestHostActivity> =
        ActivityScenario.launch(CaptureTestHostActivity::class.java)

    private fun dependencies() = CaptureDependencies(
        permissions = permissions,
        camera = camera,
        createWorkflow = {
            CaptureWorkflowDependencies(
                filesRoot = testFiles,
                location = CaptureLocationSource { locationCalls++; locationSource() },
                recognizer = recognizer,
                galleryImporter = CaptureGallerySource { importCalls++; error("No import expected") },
                saver = saver
            )
        }
    )

    private fun outcome(vararg lines: String): OcrOutcome {
        val pass = OcrPass(lines.map { OcrLine(it, 0.95f) })
        return OcrOutcome(
            selectedPass = pass,
            originalConfidence = pass.confidence,
            usedEnhancedInput = false
        )
    }

    private fun resetLocationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val uiAutomation = instrumentation.uiAutomation
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).forEach { permission ->
            runCatching { uiAutomation.revokeRuntimePermission(packageName, permission) }
            listOf("user-set", "user-fixed").forEach { flag ->
                uiAutomation.executeShellCommand(
                    "pm clear-permission-flags $packageName $permission $flag"
                ).use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val PERMISSION_DENY_BUTTON =
            "com.android.permissioncontroller:id/permission_deny_button"
        const val PERMISSION_DENY_AND_DONT_ASK_BUTTON =
            "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button"
    }
}

private class FakeCapturePermissionChecker : CapturePermissionChecker {
    var readLocationPermissionFromSystem = false

    override fun hasCameraPermission(context: Context) = true
    override fun hasLocationPermission(context: Context): Boolean =
        !readLocationPermissionFromSystem ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    override fun isCameraAvailable(context: Context) = true
}

private class FakeCaptureCamera : CaptureCamera {
    @Volatile var captureRequests = 0
    @Volatile var lastLocation: Location? = null
    var lastFile: File? = null
    var completeImmediately = false
    var startCalls = 0
    var stopCalls = 0
    var delayAfterFirstStart = false
    private var pendingReady: (() -> Unit)? = null
    private var pendingCapture: (() -> Unit)? = null
    val captureRequested = CountDownLatch(1)
    private var ready = false

    override fun start(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onError: (String) -> Unit,
        onReady: () -> Unit
    ) {
        startCalls++
        pendingReady = onReady
        if (delayAfterFirstStart && startCalls > 1) return
        ready = true
        onReady()
    }

    override fun switch(
        context: Context,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        onError: (String) -> Unit,
        onReady: () -> Unit
    ) = onReady()

    override fun takePhoto(
        file: File,
        location: Location?,
        executor: Executor,
        onSaved: () -> Unit,
        onError: (CaptureCameraError) -> Unit
    ) {
        captureRequests++
        lastLocation = location
        lastFile = file
        captureRequested.countDown()
        pendingCapture = {
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(1, 2, 3))
            executor.execute(onSaved)
        }
        if (completeImmediately) complete()
    }

    fun complete() {
        pendingCapture?.invoke()
        pendingCapture = null
    }

    fun releaseReadiness() {
        ready = true
        pendingReady?.invoke()
    }

    override fun stop() {
        stopCalls++
        ready = false
    }

    override fun isReady() = ready
    override fun isBackCamera() = true
}

private class FakeCaptureTextRecognizer(
    var outcome: OcrOutcome,
    private val gate: CompletableDeferred<Unit>? = null,
    private val started: CountDownLatch? = null,
    private val cancelled: CountDownLatch? = null
) : CaptureTextRecognizer {
    var recognitionCalls = 0
    var failure: String? = null

    override suspend fun recognize(originalFile: File): OcrOutcome {
        recognitionCalls++
        started?.countDown()
        try {
            gate?.await()
        } catch (exception: CancellationException) {
            cancelled?.countDown()
            throw exception
        }
        failure?.let { error(it) }
        return outcome
    }

    override fun close() = Unit
}

private class FakeCaptureSaver : CaptureSaver {
    var saveCalls = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun save(
        chineseText: String,
        file: File
    ): Int {
        saveCalls++
        gate?.await()
        return saveCalls
    }
}
