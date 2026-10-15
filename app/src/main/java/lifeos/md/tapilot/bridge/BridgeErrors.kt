package lifeos.md.tapilot.bridge

import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.device.AccessibilityDisabledException

object BridgeErrors {
    fun code(error: Throwable): String = when (error) {
        is AccessibilityDisabledException -> "ACCESSIBILITY_DISABLED"
        is MidsceneAccessibilityService.SessionCancelledException,
        is ModelTransport.RequestCancelledException,
        is NodeTestRunCoordinator.NodeRunStoppedException -> "SESSION_CANCELLED"
        is MidsceneAccessibilityService.StaleFrameException -> "STALE_FRAME"
        is UnsupportedOperationException -> "UNSUPPORTED_ACTION"
        is IllegalArgumentException -> "INVALID_REQUEST"
        else -> "INTERNAL_ERROR"
    }
}
