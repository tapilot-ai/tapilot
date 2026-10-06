package lifeos.md.tapilot

import android.content.Context
import android.content.res.Configuration

object AppTheme {
    private const val PREFERENCES = "midscene-preferences"
    private const val THEME_KEY = "theme"
    private val supported = setOf("system", "light", "dark")

    fun preference(context: Context): String =
        parsePreference(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(THEME_KEY, null))

    fun parsePreference(value: String?): String = value?.takeIf { it in supported } ?: "system"

    fun resolveDark(preference: String, systemDark: Boolean): Boolean = when (parsePreference(preference)) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    fun set(context: Context, theme: String) {
        require(theme in supported) { "Unsupported app theme: $theme" }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME_KEY, theme)
            .apply()
    }

    fun paletteId(context: Context): String = parsePaletteId(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("theme.palette", null))

    fun parsePaletteId(value: String?): String = if (value == "minimal") "minimal" else "classic"

    fun setPalette(context: Context, id: String) {
        require(id == "classic" || id == "minimal") { "Unsupported palette: $id" }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString("theme.palette", id).apply()
    }

    internal fun colors(context: Context, dark: Boolean = isDark(context)) =
        lifeos.md.tapilot.theme.ThemePalettes.get(paletteId(context), dark)

    fun color(context: Context, resource: Int): Int =
        lifeos.md.tapilot.theme.ThemePalettes.resource(paletteId(context), isDark(context), resource)
            ?: context.getColor(resource)

    fun isDark(context: Context): Boolean = resolveDark(preference(context),
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
}
