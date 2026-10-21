package lifeos.md.tapilot.bridge

import lifeos.md.tapilot.accessibility.NativeAction
import org.json.JSONObject

object NativeActionParser {
    fun parse(value: JSONObject): NativeAction = when (value.getString("type")) {
        "system" -> NativeAction.System(value.getString("action"))
        "editText" -> NativeAction.EditText(
            value.getString("operation"),
            if (value.has("start")) value.getInt("start") else null,
            if (value.has("end")) value.getInt("end") else null,
            if (value.has("text")) value.getString("text") else null,
        )
        "gesture" -> {
            val fingers = value.getJSONArray("fingers")
            NativeAction.MultiGesture((0 until fingers.length()).map { i ->
                val points = fingers.getJSONArray(i)
                (0 until points.length()).map { j ->
                    val point = points.getJSONObject(j)
                    NativeAction.Point(point.getDouble("x").toFloat(), point.getDouble("y").toFloat())
                }
            }, value.getLong("duration"), value.optLong("holdMs", 0), value.optLong("endHoldMs", 0))
        }
        "tap" -> NativeAction.Tap(
            value.getDouble("x").toFloat(),
            value.getDouble("y").toFloat(),
            value.optLongOrNull("duration"),
        )
        "doubleTap" -> NativeAction.DoubleTap(
            value.getDouble("x").toFloat(),
            value.getDouble("y").toFloat(),
        )
        "longPress" -> NativeAction.LongPress(
            value.getDouble("x").toFloat(),
            value.getDouble("y").toFloat(),
            value.optLongOrNull("duration"),
        )
        "swipe" -> NativeAction.Swipe(
            value.getDouble("startX").toFloat(),
            value.getDouble("startY").toFloat(),
            value.getDouble("endX").toFloat(),
            value.getDouble("endY").toFloat(),
            value.optLongOrNull("duration"),
        )
        "pinch" -> NativeAction.Pinch(
            value.getDouble("centerX").toFloat(),
            value.getDouble("centerY").toFloat(),
            value.getDouble("startDistance").toFloat(),
            value.getDouble("endDistance").toFloat(),
            value.getLong("duration"),
        )
        "input" -> NativeAction.Input(
            value.getString("text"),
            value.optFloatOrNull("x"),
            value.optFloatOrNull("y"),
            value.optBoolean("replace", true),
            value.optBoolean("autoDismissKeyboard", true),
        )
        "clearInput" -> NativeAction.ClearInput(
            value.optFloatOrNull("x"),
            value.optFloatOrNull("y"),
        )
        "key" -> NativeAction.Key(value.getString("keyName"))
        "cursorMove" -> NativeAction.CursorMove(
            value.getString("direction"),
            value.getInt("times"),
        )
        "back" -> NativeAction.Back
        "home" -> NativeAction.Home
        "recentApps" -> NativeAction.RecentApps
        "appDetails" -> NativeAction.AppDetails(value.getString("packageOrName"))
        "launch" -> NativeAction.Launch(value.getString("uri"))
        else -> throw UnsupportedOperationException(
            "Unsupported native action: ${value.getString("type")}",
        )
    }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null

    private fun JSONObject.optFloatOrNull(name: String): Float? =
        if (has(name) && !isNull(name)) getDouble(name).toFloat() else null
}
