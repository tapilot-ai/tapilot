package lifeos.md.tapilot.accessibility

import android.content.Context

object FloatingBallPreferences {
    private const val PREFERENCES = "floating-ball"
    private const val ENABLED_KEY = "enabled"
    private const val X_FRACTION_KEY = "x-fraction"
    private const val Y_FRACTION_KEY = "y-fraction"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED_KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ENABLED_KEY, enabled).apply()
    }

    fun position(context: Context): Pair<Float, Float> =
        preferences(context).getFloat(X_FRACTION_KEY, 0.9f) to
            preferences(context).getFloat(Y_FRACTION_KEY, 0.35f)

    fun setPosition(context: Context, xFraction: Float, yFraction: Float) {
        preferences(context).edit()
            .putFloat(X_FRACTION_KEY, xFraction.coerceIn(0f, 1f))
            .putFloat(Y_FRACTION_KEY, yFraction.coerceIn(0f, 1f))
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
