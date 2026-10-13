package lifeos.md.tapilot.accessibility

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

/** Device coordinates include system bars, independently of the host activity's window. */
@Suppress("DEPRECATION")
internal fun Context.deviceDisplayMetrics(): DisplayMetrics = DisplayMetrics().also { metrics ->
    val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        ?: error("Default display is unavailable")
    // Gesture scaling also uses this display. Keep capture geometry and its inverse
    // mapping in the same logical coordinate space, including resolution overrides.
    display.getRealMetrics(metrics)
}
