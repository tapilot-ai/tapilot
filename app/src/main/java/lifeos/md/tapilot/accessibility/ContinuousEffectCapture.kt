package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo

/** Opportunistic window capture. Owns buffers until delivered, never hides overlays. */
internal class ContinuousEffectCapture(private val service: AccessibilityService) {
    private var retryAfter = 0L

    @Suppress("DEPRECATION")
    private fun candidate(): Int? {
        val metrics = service.resources.displayMetrics
        val windows = service.windows
        return try {
            windowCaptureCandidate(windows.filter { it.displayId == Display.DEFAULT_DISPLAY }.map { window ->
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                CaptureWindow(window.id, window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                    window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                    window.isActive, bounds.left, bounds.top, bounds.right, bounds.bottom)
            }, metrics.widthPixels, metrics.heightPixels)
        } finally { windows.forEach { it.recycle() } }
    }

    fun tryCapture(deliver: (AccessibilityService.ScreenshotResult) -> Unit, fallback: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < 34 || SystemClock.uptimeMillis() < retryAfter) return false
        val id = runCatching { candidate() }.getOrNull() ?: return false
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val rotation = service.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        fun failed() {
            // Avoid repeatedly testing a broken OEM implementation on every AI step.
            retryAfter = SystemClock.uptimeMillis() + 30_000
            fallback()
        }
        try {
            service.takeScreenshotOfWindow(id, service.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val stillValid = runCatching {
                        candidate() == id && result.hardwareBuffer.width == width &&
                            result.hardwareBuffer.height == height &&
                            service.getSystemService(android.hardware.display.DisplayManager::class.java)
                                .getDisplay(Display.DEFAULT_DISPLAY)?.rotation == rotation
                    }.getOrDefault(false)
                    if (stillValid) deliver(result)
                    else {
                        result.hardwareBuffer.close()
                        failed()
                    }
                }
                override fun onFailure(errorCode: Int) = failed()
            })
        } catch (_: RuntimeException) { failed() }
        return true
    }
}
