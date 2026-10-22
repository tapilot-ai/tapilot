package lifeos.md.tapilot

import lifeos.md.tapilot.speech.SpeechServiceFailure
import org.junit.Assert.*
import org.junit.Test

class AppTextTest {
    @Test fun explicitPreferenceWinsAndUnsupportedSystemsUseEnglish() {
        assertEquals("en", resolveAppLanguage("en", "zh"))
        assertEquals("zh", resolveAppLanguage("zh", "en"))
        assertEquals("zh", resolveAppLanguage("system", "zh"))
        assertEquals("en", resolveAppLanguage("system", "en"))
        assertEquals("en", resolveAppLanguage("system", "fr"))
    }
    @Test fun serviceMessagesFollowLanguageChangesAndDefaultToEnglish() {
        val previous = AppText.language
        try {
            assertEquals("English", AppText.text("中文", "English"))
            AppText.language = { "zh" }
            assertTrue(SpeechServiceFailure.message("volcengine", 401, "").contains("鉴权失败"))
            AppText.language = { "en" }
            assertTrue(SpeechServiceFailure.message("volcengine", 401, "").contains("authentication failed"))
            AppText.language = { "fr" }
            assertEquals("English", AppText.text("中文", "English"))
        } finally { AppText.language = previous }
    }
}
