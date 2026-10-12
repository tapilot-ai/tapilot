package lifeos.md.tapilot.voice

import android.content.Context

class VoiceOutputSettings(context: Context) {
    private val preferences = context.getSharedPreferences("tapilot-voice-output", Context.MODE_PRIVATE)
    var autoRead: Boolean
        get() = preferences.getBoolean("auto-read", true)
        set(value) { preferences.edit().putBoolean("auto-read", value).apply() }
    fun speaker(provider: String): String = preferences.getString("speaker-$provider", null).orEmpty().ifBlank {
        if (provider == "deepgram") "aura-2-thalia-en" else "zh_female_vv_uranus_bigtts"
    }
    fun saveSpeaker(provider: String, speaker: String) {
        require(speaker.length <= 160 && speaker.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid voice ID" }
        preferences.edit().putString("speaker-$provider", speaker).apply()
    }
}
