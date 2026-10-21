package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/** On-device operations; never assumes shell or device-owner privileges. */
internal class ExtendedDeviceActions(
    private val service: AccessibilityService,
    private val dispatchGesture: (GestureDescription, (Result<Unit>) -> Unit) -> Unit,
) {
    private val globalActions = mapOf(
        "notifications" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
        "quickSettings" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        "splitScreen" to AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN,
        "lockScreen" to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
        "powerDialog" to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
    )

    fun availableSystemActions(): List<String> {
        val available = service.systemActions.map { it.id }.toSet()
        return globalActions.filterValues { it in available }.keys.toList() + "saveScreenshot"
    }

    fun apps(): JSONArray {
        val started = android.os.SystemClock.elapsedRealtime()
        val pm = service.packageManager
        val entries = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        // Loading labels may involve resource/binder work. Do it once per app,
        // not on both sides of every sorting comparison. Query fresh each time.
        return JSONArray(entries.distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }.map {
                JSONObject().put("label", it.loadLabel(pm).toString())
                    .put("packageName", it.activityInfo.packageName)
                    .put("component", "${it.activityInfo.packageName}/${it.activityInfo.name}")
            }.sortedBy { it.getString("label") }).also {
                android.util.Log.i("TapilotPerformance", JSONObject().put("phase", "device.appCatalog")
                    .put("durationMs", android.os.SystemClock.elapsedRealtime() - started).toString())
            }
    }

    fun system(name: String) {
        val id = globalActions[name] ?: throw UnsupportedOperationException("Unknown system action: $name")
        if (name !in availableSystemActions()) throw UnsupportedOperationException("System action unavailable on this device: $name")
        check(service.performGlobalAction(id)) { "Android rejected system action: $name" }
    }

    fun gesture(action: NativeAction.MultiGesture, scaleX: Float, scaleY: Float,
                cancelled: () -> Boolean, callback: (Result<Unit>) -> Unit) {
        // Screenshots and gesture scaling include system bars, unlike app resources.
        val metrics = service.deviceDisplayMetrics()
        validateMultiGesture(action, metrics.widthPixels, metrics.heightPixels)
        fun path(points: List<NativeAction.Point>) = Path().apply {
            moveTo(points.first().x * scaleX, points.first().y * scaleY)
            points.drop(1).forEach { lineTo(it.x * scaleX, it.y * scaleY) }
        }
        fun dispatch(strokes: List<GestureDescription.StrokeDescription>, done: (Result<Unit>) -> Unit) {
            val builder = GestureDescription.Builder()
            strokes.forEach { builder.addStroke(it) }
            dispatchGesture(builder.build(), done)
        }
        // Each phase continues the same pointers. A stationary stroke can be
        // acknowledged early, so wait for its wall-clock duration before continuing.
        val phases = buildList {
            if (action.holdMs > 0) add(action.fingers.map { listOf(it.first()) } to action.holdMs)
            add(action.fingers to action.duration)
            if (action.endHoldMs > 0) add(action.fingers.map { listOf(it.last()) } to action.endHoldMs)
        }
        fun runPhase(index: Int, previous: List<GestureDescription.StrokeDescription>?) {
            val (points, duration) = phases[index]
            // The final stationary phase must include UP. A stationary continued
            // stroke with neither DOWN nor UP can produce no MotionEvents and
            // Android cancels it instead of waiting.
            val continued = index < phases.lastIndex
            val strokes = points.mapIndexed { finger, positions ->
                previous?.get(finger)?.continueStroke(path(positions), 0, duration, continued)
                    ?: GestureDescription.StrokeDescription(path(positions), 0, duration, continued)
            }
            val wait = GestureHold(duration, SystemClock::uptimeMillis) { delay, next ->
                Handler(Looper.getMainLooper()).postDelayed(next, delay)
            }
            dispatch(strokes) { result ->
                if (result.isFailure) callback(Result.failure(IllegalStateException("Gesture phase ${index + 1}/${phases.size} failed: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())))
                else if (!continued) callback(result)
                else wait.continueWhenReady {
                    if (cancelled()) {
                        dispatch(strokes.mapIndexed { finger, stroke ->
                            stroke.continueStroke(path(listOf(points[finger].last())), 0, 1, false)
                        }) {
                            callback(Result.failure(MidsceneAccessibilityService.SessionCancelledException()))
                        }
                    } else runCatching { runPhase(index + 1, strokes) }
                        .onFailure { callback(Result.failure(it)) }
                }
            }
        }
        runPhase(0, null)
    }

    @Suppress("DEPRECATION")
    fun edit(action: NativeAction.EditText) {
        val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: error("Focus an editable text field first")
        try {
            require(node.isEditable) { "Focused field is not editable" }
            val text = node.text?.toString() ?: ""
            val success = when (action.operation) {
                "selectAll" -> select(node, 0, text.length)
                "select" -> {
                    val start = requireNotNull(action.start) { "start is required (UTF-16 offset)" }
                    val end = requireNotNull(action.end) { "end is required (UTF-16 offset)" }
                    require(start in 0..text.length && end in start..text.length) { "Selection is outside text bounds" }
                    select(node, start, end)
                }
                "replaceSelection" -> {
                    val start = node.textSelectionStart
                    val end = node.textSelectionEnd
                    require(start in 0..text.length && end in start..text.length) { "No valid text selection" }
                    val replacement = requireNotNull(action.text) { "Replacement text is required" }
                    val changed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.replaceRange(start, end, replacement))
                    })
                    if (changed) select(node, start + replacement.length, start + replacement.length)
                    changed
                }
                "copy" -> node.performAction(AccessibilityNodeInfo.ACTION_COPY)
                "cut" -> node.performAction(AccessibilityNodeInfo.ACTION_CUT)
                "paste" -> node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                "submit" -> node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                else -> throw UnsupportedOperationException("Unknown text operation: ${action.operation}")
            }
            if (!success) throw UnsupportedOperationException("Focused app does not support text operation: ${action.operation}")
        } finally { node.recycle() }
    }

    private fun select(node: AccessibilityNodeInfo, start: Int, end: Int) = node.performAction(
        AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        })
}

internal fun validateMultiGesture(action: NativeAction.MultiGesture, width: Int, height: Int) {
    require(action.fingers.size in 1..10) { "Gesture requires 1 to 10 fingers" }
    require(action.duration in 1..10000 && action.holdMs in 0..5000 && action.endHoldMs in 0..3000) { "Invalid gesture timing" }
    action.fingers.forEach { points ->
        require(points.size in 2..128) { "Each finger requires 2 to 128 path points" }
        points.forEach {
            require(it.x.isFinite() && it.y.isFinite() && it.x >= 0 && it.x < width && it.y >= 0 && it.y < height) { "Gesture point outside screen bounds" }
        }
    }
}
