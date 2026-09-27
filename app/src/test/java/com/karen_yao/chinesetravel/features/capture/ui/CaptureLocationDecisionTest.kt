package com.karen_yao.chinesetravel.features.capture.ui

import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLocationDecisionTest {
    @Test
    fun deniedPermissionContinuesWithoutLocation() {
        val decision = decideLocationAfterPermission(granted = false)

        assertTrue(decision is CaptureLocationDecision.CaptureWithoutLocation)
        assertEquals(
            "Location permission was not granted",
            (decision as CaptureLocationDecision.CaptureWithoutLocation).message
        )
    }

    @Test
    fun grantedPermissionRequestsLocation() {
        assertEquals(
            CaptureLocationDecision.AcquireLocation,
            decideLocationAfterPermission(granted = true)
        )
    }

    @Test
    fun everyUnavailableReasonContinuesWithoutLocationWithFeedback() {
        val expectedMessages = mapOf(
            DeviceLocationProvider.FailureReason.PERMISSION_DENIED to
                "Location permission was not granted.",
            DeviceLocationProvider.FailureReason.LOCATION_DISABLED to
                "Location services are disabled.",
            DeviceLocationProvider.FailureReason.TIMED_OUT to
                "The location request timed out.",
            DeviceLocationProvider.FailureReason.INVALID_OR_STALE to
                "No recent valid location was available.",
            DeviceLocationProvider.FailureReason.REQUEST_FAILED to
                "The location request failed."
        )

        expectedMessages.forEach { (reason, message) ->
            assertEquals(message, decideLocationAfterUnavailable(reason).message)
        }
    }
}
