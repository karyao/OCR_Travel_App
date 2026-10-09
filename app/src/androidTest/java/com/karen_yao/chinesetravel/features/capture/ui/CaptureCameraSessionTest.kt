package com.karen_yao.chinesetravel.features.capture.ui

import android.content.Context
import android.location.Location
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureCameraSessionTest {
    @Test fun resumeAcceptsSynchronousReadinessBeforeViewLifecycleIsResumed() = withSession { fixture ->
        fixture.camera.readySynchronously = true
        fixture.session.resume()
        assertEquals(Lifecycle.State.STARTED, fixture.owner.lifecycle.currentState)
        assertEquals(listOf(CaptureEvent.CameraReady(true)), fixture.events)
        assertSame(fixture.context, fixture.camera.lastContext)
        assertSame(fixture.preview, fixture.camera.lastPreview)
        assertSame(fixture.owner, fixture.camera.lastOwner)

        fixture.session.start()
        assertEquals(2, fixture.camera.startCalls)
        assertEquals(CaptureEvent.CameraReady(true), fixture.events.last())
        fixture.session.switchCamera()
        assertEquals(1, fixture.camera.switchCalls)
        assertEquals(CaptureEvent.CameraReady(false, "Switched to front camera"), fixture.events.last())
        fixture.session.switchCamera()
        assertEquals(CaptureEvent.CameraReady(true, "Switched to back camera"), fixture.events.last())
    }

    @Test fun permissionApprovalStartsAnAlreadyResumedSession() = withSession { fixture ->
        fixture.permissions.cameraGranted = false
        fixture.session.resume()
        assertEquals(0, fixture.camera.startCalls)
        assertTrue(fixture.events.isEmpty())

        fixture.permissions.cameraGranted = true
        fixture.session.start()
        assertEquals(1, fixture.camera.startCalls)
        fixture.camera.bindings.single().ready()
        assertEquals(listOf(CaptureEvent.CameraReady(true)), fixture.events)
    }

    @Test fun permissionApprovalWhilePausedWaitsForResume() = withSession { fixture ->
        fixture.permissions.cameraGranted = false
        fixture.session.resume()
        fixture.session.pause()
        fixture.permissions.cameraGranted = true
        fixture.session.start()
        fixture.session.switchCamera()
        assertEquals(0, fixture.camera.startCalls)
        assertEquals(0, fixture.camera.switchCalls)

        fixture.session.resume()
        assertEquals(1, fixture.camera.startCalls)
        fixture.camera.bindings.single().ready()
        assertEquals(listOf(CaptureEvent.CameraReady(true)), fixture.events)
    }

    @Test fun unavailableHardwareReportsExistingFailureWithoutBinding() = withSession { fixture ->
        fixture.permissions.available = false
        fixture.session.resume()
        fixture.session.switchCamera()
        assertEquals(0, fixture.camera.startCalls)
        assertEquals(0, fixture.camera.switchCalls)
        assertEquals(List(2) {
            CaptureEvent.CameraFailed("Camera not available on this device")
        }, fixture.events)
    }

    @Test fun supersededStartsAndSwitchesCannotReportReadinessOrErrors() = withSession { fixture ->
        fixture.session.resume()
        fixture.session.start()
        fixture.session.switchCamera()
        fixture.camera.bindings.take(2).forEach {
            it.ready()
            it.error("stale failure")
        }
        assertTrue(fixture.events.isEmpty())

        fixture.camera.bindings.last().ready()
        fixture.camera.bindings.last().error("current failure")
        assertEquals(listOf(
            CaptureEvent.CameraReady(false, "Switched to front camera"),
            CaptureEvent.CameraFailed("current failure")
        ), fixture.events)
    }

    @Test fun pauseInvalidatesBeforeHostCancellationAndCameraStop() = withSession { fixture ->
        fixture.session.resume()
        val oldBinding = fixture.camera.bindings.single()
        var hostPaused = false
        fixture.camera.onStop = {
            assertTrue(hostPaused)
            oldBinding.ready()
            oldBinding.error("camera stopped")
        }
        fixture.session.pause {
            oldBinding.ready()
            oldBinding.error("pause in progress")
            hostPaused = true
        }
        assertEquals(1, fixture.camera.stopCalls)
        assertTrue(fixture.events.isEmpty())
        fixture.session.start()
        fixture.session.switchCamera()
        assertEquals(1, fixture.camera.startCalls)
        assertEquals(0, fixture.camera.switchCalls)

        fixture.session.resume()
        oldBinding.ready()
        oldBinding.error("old binding after resume")
        assertTrue(fixture.events.isEmpty())
        fixture.camera.bindings.last().ready()
        assertEquals(listOf(CaptureEvent.CameraReady(true)), fixture.events)
    }

    @Test fun disposalStopsOnceAndRejectsAllLaterCommandsAndCallbacks() = withSession { fixture ->
        fixture.session.resume()
        val binding = fixture.camera.bindings.single()
        fixture.camera.onStop = { binding.ready(); binding.error("disposed") }
        fixture.session.dispose()
        fixture.session.dispose()
        fixture.session.resume()
        fixture.session.start()
        fixture.session.switchCamera()
        binding.ready()
        binding.error("late failure")
        assertEquals(1, fixture.camera.stopCalls)
        assertEquals(1, fixture.camera.startCalls)
        assertEquals(0, fixture.camera.switchCalls)
        assertTrue(fixture.events.isEmpty())
    }

    @Test fun disposalAfterPauseDoesNotStopAgain() = withSession { fixture ->
        fixture.session.resume()
        fixture.session.pause()
        fixture.session.dispose()
        fixture.session.dispose()
        assertEquals(1, fixture.camera.stopCalls)
    }

    @Test fun destroyedLifecycleCannotBindOrReceivePendingResults() = withSession { fixture ->
        fixture.session.resume()
        fixture.owner.registry.currentState = Lifecycle.State.DESTROYED
        fixture.camera.bindings.single().ready()
        fixture.camera.bindings.single().error("destroyed view")
        fixture.session.start()
        fixture.session.switchCamera()
        assertEquals(1, fixture.camera.startCalls)
        assertEquals(0, fixture.camera.switchCalls)
        assertTrue(fixture.events.isEmpty())
    }

    private fun withSession(block: (Fixture) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val fixture = Fixture()
            try {
                block(fixture)
            } finally {
                fixture.session.dispose()
            }
        }
    }

    private class Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preview = PreviewView(context)
        val owner = TestOwner()
        val camera = SessionCamera()
        val permissions = SessionPermissions()
        val events = mutableListOf<CaptureEvent>()
        val session = CaptureCameraSession(camera, permissions, context, preview, owner, events::add)
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class SessionPermissions : CapturePermissionChecker {
        var cameraGranted = true
        var available = true
        override fun hasCameraPermission(context: Context) = cameraGranted
        override fun hasLocationPermission(context: Context) = true
        override fun isCameraAvailable(context: Context) = available
    }

    private class SessionCamera : CaptureCamera {
        data class Binding(val error: (String) -> Unit, val ready: () -> Unit)
        val bindings = mutableListOf<Binding>()
        var startCalls = 0
        var switchCalls = 0
        var stopCalls = 0
        var readySynchronously = false
        var onStop: () -> Unit = {}
        var lastContext: Context? = null
        var lastPreview: PreviewView? = null
        var lastOwner: LifecycleOwner? = null
        private var back = true

        override fun start(
            context: Context, previewView: PreviewView, lifecycleOwner: LifecycleOwner,
            onError: (String) -> Unit, onReady: () -> Unit
        ) {
            startCalls++
            lastContext = context
            lastPreview = previewView
            lastOwner = lifecycleOwner
            bindings += Binding(onError, onReady)
            if (readySynchronously) onReady()
        }

        override fun switch(
            context: Context, previewView: PreviewView, lifecycleOwner: LifecycleOwner,
            onError: (String) -> Unit, onReady: () -> Unit
        ) {
            switchCalls++
            back = !back
            bindings += Binding(onError, onReady)
            if (readySynchronously) onReady()
        }

        override fun takePhoto(
            file: File, location: Location?, executor: Executor,
            onSaved: () -> Unit, onError: (CaptureCameraError) -> Unit
        ) = error("Photo operations belong to the host")

        override fun stop() {
            stopCalls++
            onStop()
        }

        override fun isReady() = false
        override fun isBackCamera() = back
    }
}
