package lifeos.md.tapilot.shell

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService

/** Unicode editing is permitted only through a focused node belonging to the owned display. */
internal object BackgroundTextInput {
    fun set(display: Int, value: String, replace: Boolean): Boolean {
        require(display > 0 && value.length <= 2000)
        val windows = MidsceneAccessibilityService.current()?.windowsOnAllDisplays?.get(display) ?: return false
        for (window in windows) {
            val root = window.root ?: continue
            val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
            if (!node.isEditable || node.isPassword) continue
            val old = node.text?.toString().orEmpty()
            val start = node.textSelectionStart.coerceIn(0, old.length)
            val end = node.textSelectionEnd.coerceIn(start, old.length)
            val updated = if (replace) value else old.replaceRange(start, end, value)
            check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
            })) { "App refused background text input" }
            return true
        }
        return false
    }
}
