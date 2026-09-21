package lifeos.md.tapilot.accessibility

internal enum class TextInputMethod {
    ACCESSIBILITY_NODE,
    INPUT_CONNECTION,
    CLIPBOARD_PASTE,
    UNAVAILABLE,
}

internal object TextInputFallbackPolicy {
    fun resolve(
        hasEditableNode: Boolean,
        hasInputConnection: Boolean,
        hasRecentTapPoint: Boolean,
    ): TextInputMethod = when {
        hasEditableNode -> TextInputMethod.ACCESSIBILITY_NODE
        hasInputConnection -> TextInputMethod.INPUT_CONNECTION
        hasRecentTapPoint -> TextInputMethod.CLIPBOARD_PASTE
        else -> TextInputMethod.UNAVAILABLE
    }

    fun isRecentTap(tappedAtMillis: Long, nowMillis: Long): Boolean =
        tappedAtMillis > 0L && nowMillis - tappedAtMillis in 0..RECENT_TAP_MAX_AGE_MILLIS

    private const val RECENT_TAP_MAX_AGE_MILLIS = 15_000L
}
