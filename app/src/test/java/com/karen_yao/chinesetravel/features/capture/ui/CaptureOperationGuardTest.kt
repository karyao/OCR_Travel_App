package com.karen_yao.chinesetravel.features.capture.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureOperationGuardTest {
    @Test
    fun startingCaptureWhileIdleSucceedsAndSecondAttemptIsRejected() {
        val guard = CaptureOperationGuard()

        assertNotNull(guard.beginCapture())
        assertNull(guard.beginCapture())
        assertTrue(guard.isBusy())
    }

    @Test
    fun galleryCancellationReturnsGuardToIdle() {
        val guard = CaptureOperationGuard()

        assertTrue(guard.beginGallery())
        assertFalse(guard.beginGallery())
        guard.finishGallery()

        assertFalse(guard.isBusy())
        assertTrue(guard.beginGallery())
    }
}
