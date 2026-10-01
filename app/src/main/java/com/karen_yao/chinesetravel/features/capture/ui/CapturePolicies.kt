package com.karen_yao.chinesetravel.features.capture.ui

import com.karen_yao.chinesetravel.shared.location.DeviceLocationProvider

internal sealed interface CaptureLocationDecision {
    data object AcquireLocation : CaptureLocationDecision
    data class CaptureWithoutLocation(val message: String) : CaptureLocationDecision
}

internal fun decideLocationAfterPermission(granted: Boolean): CaptureLocationDecision =
    if (granted) {
        CaptureLocationDecision.AcquireLocation
    } else {
        CaptureLocationDecision.CaptureWithoutLocation("Location permission was not granted")
    }

internal fun decideLocationAfterUnavailable(
    reason: DeviceLocationProvider.FailureReason
): CaptureLocationDecision.CaptureWithoutLocation =
    CaptureLocationDecision.CaptureWithoutLocation(
        when (reason) {
            DeviceLocationProvider.FailureReason.PERMISSION_DENIED ->
                "Location permission was not granted."
            DeviceLocationProvider.FailureReason.LOCATION_DISABLED ->
                "Location services are disabled."
            DeviceLocationProvider.FailureReason.TIMED_OUT ->
                "The location request timed out."
            DeviceLocationProvider.FailureReason.INVALID_OR_STALE ->
                "No recent valid location was available."
            DeviceLocationProvider.FailureReason.REQUEST_FAILED ->
                "The location request failed."
        }
    )

internal sealed interface RecognizedTextDecision {
    data object NoText : RecognizedTextDecision
    data class ChooseText(val lines: List<String>) : RecognizedTextDecision
    data class SaveText(val text: String) : RecognizedTextDecision
    data object TextTooShort : RecognizedTextDecision
}

internal fun classifyRecognizedText(lines: List<String>): RecognizedTextDecision {
    return when {
        lines.isEmpty() -> RecognizedTextDecision.NoText
        lines.size > 1 -> RecognizedTextDecision.ChooseText(lines)
        lines.first().length >= 2 -> RecognizedTextDecision.SaveText(lines.first())
        else -> RecognizedTextDecision.TextTooShort
    }
}
