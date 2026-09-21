package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/** On-device operations; never assumes shell or device-owner privileges. */
internal class ExtendedDeviceActions(private val service: AccessibilityService) {
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
        val pm = service.packageManager
        val entries = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return JSONArray(entries.distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
            .sortedBy { it.loadLabel(pm).toString() }.map {
                JSONObject().put("label", it.loadLabel(pm).toString())
                    .put("packageName", it.activityInfo.packageName)
                    .put("component", "${it.activityInfo.packageName}/${it.activityInfo.name}")
            })
    }

    fun system(name: String) {
        val id = globalActions[name] ?: throw UnsupportedOperationException("Unknown system action: $name")
        if (name !in availableSystemActions()) throw UnsupportedOperationException("System action unavailable on this device: $name")
        check(service.performGlobalAction(id)) { "Android rejected system action: $name" }
    }

    fun gesture(action: NativeAction.MultiGesture, scaleX: Float, scaleY: Float,
                cancelled: () -> Boolean, callback: (Result<Unit>) -> Unit) {
        val metrics = service.resources.displayMetrics
        validateMultiGesture(action, metrics.widthPixels, metrics.heightPixels)
        fun path(points: List<NativeAction.Point>) = Path().apply {
            moveTo(points.first().x * scaleX, points.first().y * scaleY)
            points.drop(1).forEach { lineTo(it.x * scaleX, it.y * scaleY) }
        }
        fun dispatch(strokes: List<GestureDescription.StrokeDescription>, done: (Result<Unit>) -> Unit) {
            val builder = GestureDescription.Builder()
            strokes.forEach { builder.addStroke(it) }
            val accepted = service.dispatchGesture(builder.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = done(Result.success(Unit))
                override fun onCancelled(gestureDescription: GestureDescription?) = done(Result.failure(IllegalStateException("Gesture cancelled by Android or user input")))
            }, null)
            if (!accepted) done(Result.failure(IllegalStateException("Android rejected gesture")))
        }
        if (action.holdMs == 0L) {
            dispatch(action.fingers.map { GestureDescription.StrokeDescription(path(it), 0, action.duration) }, callback)
        } else {
            val held = action.fingers.map { GestureDescription.StrokeDescription(path(listOf(it.first())), 0, action.holdMs, true) }
            dispatch(held) { result ->
                if (result.isFailure) callback(result)
                else if (cancelled()) {
                    // Release held pointers without dragging or introducing a new tap.
                    dispatch(held.mapIndexed { index, stroke ->
                        stroke.continueStroke(path(listOf(action.fingers[index].first())), 0, 1, false)
                    }) { callback(Result.failure(MidsceneAccessibilityService.SessionCancelledException())) }
                }
                else runCatching {
                    dispatch(held.mapIndexed { index, stroke ->
                        stroke.continueStroke(path(action.fingers[index]), 0, action.duration, false)
                    }, callback)
                }.onFailure { callback(Result.failure(it)) }
            }
        }
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
    require(action.duration in 1..10000 && action.holdMs in 0..5000) { "Invalid gesture timing" }
    action.fingers.forEach { points ->
        require(points.size in 2..128) { "Each finger requires 2 to 128 path points" }
        points.forEach {
            require(it.x.isFinite() && it.y.isFinite() && it.x >= 0 && it.x < width && it.y >= 0 && it.y < height) { "Gesture point outside screen bounds" }
        }
    }
}
