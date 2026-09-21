package lifeos.md.tapilot

import android.content.Context
import android.content.res.Configuration

object AppTheme {
    private const val PREFERENCES = "midscene-preferences"
    private const val THEME_KEY = "theme"
    private val supported = setOf("system", "light", "dark")

    fun preference(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(THEME_KEY, "system")
            ?.takeIf { it in supported }
            ?: "system"

    fun set(context: Context, theme: String) {
        require(theme in supported) { "Unsupported app theme: $theme" }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME_KEY, theme)
            .apply()
    }

    fun isDark(context: Context): Boolean = when (preference(context)) {
        "dark" -> true
        "light" -> false
        else -> context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
