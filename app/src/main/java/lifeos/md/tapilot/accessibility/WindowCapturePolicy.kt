package lifeos.md.tapilot.accessibility

internal data class CaptureWindow(
    val id: Int, val application: Boolean, val assistantOverlay: Boolean,
    val active: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int,
)

/** Conservative: never drop a keyboard, dialog, system bar, or split-screen pane. */
internal fun windowCaptureCandidate(windows: List<CaptureWindow>, width: Int, height: Int): Int? {
    val visible = windows.filter {
        !it.assistantOverlay && it.right > it.left && it.bottom > it.top &&
            it.right > 0 && it.bottom > 0 && it.left < width && it.top < height
    }
    val candidate = visible.singleOrNull() ?: return null
    return candidate.id.takeIf {
        candidate.application && candidate.active && candidate.left == 0 && candidate.top == 0 &&
            candidate.right == width && candidate.bottom == height
    }
}
