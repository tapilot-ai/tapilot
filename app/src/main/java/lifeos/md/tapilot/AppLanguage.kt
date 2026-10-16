package lifeos.md.tapilot

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList

object AppLanguage {
    private const val PREFERENCES = "midscene-preferences"
    private const val LANGUAGE_KEY = "language"
    private val supported = setOf("system", "zh", "en")

    fun wrap(context: Context): Context {
        val configuration = localizedConfiguration(context, preference(context))
        return context.createConfigurationContext(configuration)
    }

    fun apply(context: Context) {
        applyResources(context, preference(context))
    }

    fun set(context: Context, language: String): Boolean {
        require(language in supported) { "Unsupported app language: $language" }
        val changed = preference(context) != language
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language)
            .apply()
        applyResources(context.applicationContext, language)
        applyResources(context, language)
        return changed
    }

    fun resolved(context: Context): String {
        return resolveAppLanguage(preference(context), Resources.getSystem().configuration.locales[0].language)
    }

    private fun preference(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, "system")
            ?.takeIf { it in supported }
            ?: "system"

    @Suppress("DEPRECATION")
    private fun applyResources(context: Context, language: String) {
        val configuration = localizedConfiguration(context, language)
        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics,
        )
    }

    private fun localizedConfiguration(context: Context, language: String): Configuration {
        val locales = if (language == "system") {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList.forLanguageTags(language)
        }
        return Configuration(context.resources.configuration).apply {
            setLocales(locales)
        }
    }
}

internal fun resolveAppLanguage(preference: String, systemLanguage: String): String = when (preference) {
    "zh" -> "zh"
    "en" -> "en"
    else -> if (systemLanguage == "zh") "zh" else "en"
}
