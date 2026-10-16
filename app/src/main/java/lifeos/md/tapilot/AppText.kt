package lifeos.md.tapilot

/** Localizes background-service messages without retaining an Activity. Defaults to English. */
object AppText {
    @Volatile internal var language: () -> String = { "en" }
    fun text(chinese: String, english: String): String = if (language() == "zh") chinese else english
}
