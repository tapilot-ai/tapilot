package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotFailurePolicyTest {
    @Test
    fun `rate limit failures back off three times`() {
        val code = AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT

        assertEquals(350L, ScreenshotFailurePolicy.retryDelayMillis(code, 0))
        assertEquals(700L, ScreenshotFailurePolicy.retryDelayMillis(code, 1))
        assertEquals(1_050L, ScreenshotFailurePolicy.retryDelayMillis(code, 2))
        assertNull(ScreenshotFailurePolicy.retryDelayMillis(code, 3))
    }

    @Test
    fun `non rate limit failures are not retried`() {
        assertNull(ScreenshotFailurePolicy.retryDelayMillis(
            AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS,
            0,
        ))
    }

    @Test
    fun `rate limit message does not blame accessibility permission`() {
        val message = ScreenshotFailurePolicy.message(
            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT,
            disconnected = false,
        )

        assertTrue(message.contains("too frequent"))
        assertTrue(!message.contains("Accessibility settings"))
    }

    @Test
    fun `secure window has a specific explanation`() {
        val message = ScreenshotFailurePolicy.message(
            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW,
            disconnected = false,
        )

        assertTrue(message.contains("privacy or security"))
    }
}
