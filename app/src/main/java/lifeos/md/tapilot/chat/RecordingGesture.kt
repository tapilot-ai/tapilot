package lifeos.md.tapilot.chat

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class RecordingGesture { Recording, Cancel, Send }

/** Geometry in logical pixels, used by both the rendered controls and finger hit testing. */
data class RecordingLayout(val width: Float, val height: Float) {
    val buttonTop = height - minOf(214f, height * .25f)
    val buttonWidth = width * .49f
    val buttonHeight = 72f
    val cancelLeft = -12f
    val sendLeft = width - buttonWidth + 12f
    val arcTop = height - minOf(124f, height * .145f)
    val bubbleTop = height * .3f
    val bubbleHeight = maxOf(100f, buttonTop - 56f - bubbleTop)

    fun actionAt(x: Float, y: Float): RecordingGesture {
        fun inside(left: Float, degrees: Double): Boolean {
            val angle = Math.toRadians(degrees)
            val dx = x - (left + buttonWidth / 2)
            val dy = y - (buttonTop + buttonHeight / 2)
            val localX = dx * cos(angle) + dy * sin(angle)
            val localY = -dx * sin(angle) + dy * cos(angle)
            return abs(localX) <= buttonWidth / 2 + 8 && abs(localY) <= buttonHeight / 2 + 8
        }
        return when {
            inside(cancelLeft, -10.0) -> RecordingGesture.Cancel
            inside(sendLeft, 10.0) -> RecordingGesture.Send
            else -> RecordingGesture.Recording
        }
    }
}

internal fun recordingGesture(x: Float, y: Float, width: Float, height: Float, density: Float): RecordingGesture =
    RecordingLayout(width / density, height / density).actionAt(x / density, y / density)

internal fun canHoldToSpeak(enabled: Boolean, focused: Boolean, draft: CharSequence): Boolean =
    enabled && !focused && draft.isEmpty()
