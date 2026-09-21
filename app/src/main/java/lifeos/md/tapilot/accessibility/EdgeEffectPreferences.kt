package lifeos.md.tapilot.accessibility

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import org.json.JSONObject

internal enum class EdgeEffectGeometryMode(val wireValue: String) {
    AUTO("auto"),
    SQUARE("square"),
    CUSTOM("custom");

    companion object {
        fun fromWireValue(value: String): EdgeEffectGeometryMode =
            entries.firstOrNull { it.wireValue == value } ?: AUTO
    }
}

internal data class EdgeEffectGeometrySettings(
    val mode: EdgeEffectGeometryMode,
    val cornerRadiusDp: Float,
    val insetDp: Float,
)

internal object EdgeEffectPreferences {
    private const val PREFERENCES_NAME = "edge_effect_geometry"
    private const val DEFAULT_RADIUS_DP = 20f

    fun get(context: Context): EdgeEffectGeometrySettings {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val prefix = devicePrefix()
        return EdgeEffectGeometrySettings(
            mode = EdgeEffectGeometryMode.fromWireValue(
                preferences.getString("${prefix}_mode", null) ?: EdgeEffectGeometryMode.AUTO.wireValue,
            ),
            cornerRadiusDp = preferences.getFloat("${prefix}_radius_dp", DEFAULT_RADIUS_DP),
            insetDp = preferences.getFloat("${prefix}_inset_dp", 0f),
        )
    }

    fun set(context: Context, settings: EdgeEffectGeometrySettings) {
        val prefix = devicePrefix()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("${prefix}_mode", settings.mode.wireValue)
            .putFloat("${prefix}_radius_dp", settings.cornerRadiusDp.coerceIn(0f, 64f))
            .putFloat("${prefix}_inset_dp", settings.insetDp.coerceIn(0f, 24f))
            .apply()
    }

    fun describe(context: Context): JSONObject {
        val settings = get(context)
        val radii = detectedCornerRadii(context)
        return JSONObject()
            .put("mode", settings.mode.wireValue)
            .put("cornerRadiusDp", settings.cornerRadiusDp)
            .put("insetDp", settings.insetDp)
            .put("detected", radii.any { it > 0 })
            .put("detectedTopLeftPx", radii[0])
            .put("detectedTopRightPx", radii[1])
            .put("detectedBottomRightPx", radii[2])
            .put("detectedBottomLeftPx", radii[3])
    }

    private fun detectedCornerRadii(context: Context): IntArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return IntArray(4)
        val insets = context.getSystemService(WindowManager::class.java)
            .currentWindowMetrics.windowInsets
        return intArrayOf(
            insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0,
            insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0,
            insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
            insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
        )
    }

    private fun devicePrefix(): String = "${Build.MANUFACTURER}_${Build.MODEL}"
        .lowercase()
        .replace(Regex("[^a-z0-9_-]"), "_")
}
