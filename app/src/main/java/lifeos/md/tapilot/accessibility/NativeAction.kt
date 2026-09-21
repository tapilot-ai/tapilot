package lifeos.md.tapilot.accessibility

sealed interface NativeAction {
    data class System(val action: String) : NativeAction
    data class Point(val x: Float, val y: Float)
    data class MultiGesture(val fingers: List<List<Point>>, val duration: Long, val holdMs: Long) : NativeAction
    data class EditText(val operation: String, val start: Int?, val end: Int?, val text: String?) : NativeAction
    data class Tap(val x: Float, val y: Float, val duration: Long?) : NativeAction
    data class DoubleTap(val x: Float, val y: Float) : NativeAction
    data class LongPress(val x: Float, val y: Float, val duration: Long?) : NativeAction
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val duration: Long?,
    ) : NativeAction
    data class Pinch(
        val centerX: Float,
        val centerY: Float,
        val startDistance: Float,
        val endDistance: Float,
        val duration: Long,
    ) : NativeAction
    data class Input(
        val text: String,
        val x: Float?,
        val y: Float?,
        val replace: Boolean,
        val autoDismissKeyboard: Boolean,
    ) : NativeAction
    data class ClearInput(val x: Float?, val y: Float?) : NativeAction
    data class Key(val keyName: String) : NativeAction
    data class CursorMove(val direction: String, val times: Int) : NativeAction
    data object Back : NativeAction
    data object Home : NativeAction
    data object RecentApps : NativeAction
    data class AppDetails(val packageOrName: String) : NativeAction
    data class Launch(val uri: String) : NativeAction
}
