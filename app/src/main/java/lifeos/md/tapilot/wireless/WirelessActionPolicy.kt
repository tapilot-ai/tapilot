package lifeos.md.tapilot.wireless

import org.json.JSONObject

/** Coordinates on the wire refer to screenshot pixels, never the desktop preview's rendered size. */
internal class WirelessActionPolicy(
    private val isLauncherComponent: (String) -> Boolean = { false },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var frame: JSONObject? = null
    private var captured = 0L
    fun observe(geometry: JSONObject) { frame = JSONObject(geometry.toString()); captured = clock() }

    fun prepare(params: JSONObject): JSONObject {
        val result = JSONObject(params.toString())
        val action = result.getJSONObject("action")
        val type = action.getString("type")
        require(type in setOf("tap", "swipe", "input", "key", "home", "back", "recentApps", "launch")) { "Unsupported wireless action" }
        val fields = when (type) {
            "tap" -> setOf("type", "x", "y")
            "swipe" -> setOf("type", "startX", "startY", "endX", "endY", "duration")
            "input" -> setOf("type", "text", "replace", "autoDismissKeyboard")
            "key" -> setOf("type", "keyName")
            "launch" -> setOf("type", "uri")
            else -> setOf("type")
        }
        require(action.keys().asSequence().all { it in fields }) { "Unsupported action field" }
        if (type in setOf("tap", "swipe", "input")) {
            val geometry = checkNotNull(frame) { "Capture a fresh screen before this action" }
            check(clock() - captured < 60_000 && params.optString("frameId") == geometry.getString("frameId")) { "Stale frame. Capture a new screen." }
            fun coordinate(name: String, image: String, screen: String) {
                val value = action.getDouble(name)
                val size = geometry.getInt(image)
                require(value.isFinite() && value >= 0 && value < size) { "Coordinate outside screenshot" }
                action.put(name, value * geometry.getInt(screen) / size)
            }
            if (type == "tap") { coordinate("x", "imageWidth", "screenWidth"); coordinate("y", "imageHeight", "screenHeight") }
            if (type == "swipe") {
                for (name in listOf("startX", "endX")) coordinate(name, "imageWidth", "screenWidth")
                for (name in listOf("startY", "endY")) coordinate(name, "imageHeight", "screenHeight")
                require(action.optLong("duration", 500) in 100..2000) { "Swipe duration must be 100–2000 ms" }
            }
            if (type == "input") {
                require(action.getString("text").length <= 10_000) { "Text too long" }
                require(!action.has("x") && !action.has("y")) { "Focus the field with a tap first" }
            }
        }
        if (type == "launch") {
            val uri = action.getString("uri")
            val packageName = uri.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
            require(packageName || (uri.contains("/") && !uri.contains(":") && isLauncherComponent(uri))) {
                "Use an exact app package name or installed launcher component"
            }
        }
        if (type == "key") require(action.getString("keyName") == "ENTER") { "Unsupported keyboard key" }
        frame = null // Also consume the frame when execution later fails: never blindly retry a mutation.
        return result
    }
}
