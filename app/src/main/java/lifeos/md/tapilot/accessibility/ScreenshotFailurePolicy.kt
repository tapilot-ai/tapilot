package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService

internal object ScreenshotFailurePolicy {
    private const val MAX_RATE_LIMIT_RETRIES = 3
    private const val RATE_LIMIT_RETRY_BASE_MS = 350L

    fun retryDelayMillis(errorCode: Int, retryAttempt: Int): Long? {
        if (errorCode != AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) return null
        if (retryAttempt >= MAX_RATE_LIMIT_RETRIES) return null
        return RATE_LIMIT_RETRY_BASE_MS * (retryAttempt + 1)
    }

    fun message(errorCode: Int, disconnected: Boolean): String = when {
        disconnected ->
            "Tapilot device control disconnected. Turn it off and on again in Accessibility settings."
        errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            "Android limited screenshot requests because they were too frequent. Tapilot retried automatically; wait a moment and try again."
        errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "Tapilot does not have screenshot access. Enable Tapilot device control in Accessibility settings."
        errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
            "Android could not capture the current display because it is no longer available. Return to the main display and try again."
        errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW ->
            "Android could not capture the selected window because it changed. Try the task again."
        errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            "This screen blocks screenshots for privacy or security, so Tapilot cannot inspect it."
        else -> "Android screenshot failed (code $errorCode). Try again."
    }
}
