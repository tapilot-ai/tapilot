package lifeos.md.tapilot.voice

internal data class VoicePreset(val id: String, val name: String, val languages: String, val languagesEn: String) {
    fun label(zh: Boolean) = "$name · ${if (zh) languages else languagesEn}"
}

// Seed TTS 2.0 only; do not mix Seed TTS 1.0 voices with the playback resource.
// https://www.volcengine.com/docs/6561/162929
// https://developers.deepgram.com/docs/tts-models
internal fun voicePresets(provider: String): List<VoicePreset> = when (provider) {
    "volcengine" -> listOf(
        VoicePreset("zh_female_vv_uranus_bigtts", "Vivi 2.0", "中文/英语", "Chinese/English"),
        VoicePreset("zh_male_dayi_saturn_bigtts", "大壹", "中文", "Chinese"),
        VoicePreset("zh_female_mizai_saturn_bigtts", "黑猫侦探社咪仔", "中文", "Chinese"),
        VoicePreset("zh_female_jitangnv_saturn_bigtts", "鸡汤女", "中文", "Chinese"),
        VoicePreset("zh_female_meilinvyou_saturn_bigtts", "魅力女友", "中文", "Chinese"),
        VoicePreset("zh_female_santongyongns_saturn_bigtts", "流畅女声", "中文", "Chinese"),
        VoicePreset("zh_male_ruyayichen_saturn_bigtts", "儒雅逸辰", "中文", "Chinese"),
    )
    "deepgram" -> listOf(
        VoicePreset("aura-2-thalia-en", "Thalia", "英语 · 女声", "English · Female"),
        VoicePreset("aura-2-andromeda-en", "Andromeda", "英语 · 女声", "English · Female"),
        VoicePreset("aura-2-helena-en", "Helena", "英语 · 女声", "English · Female"),
        VoicePreset("aura-2-apollo-en", "Apollo", "英语 · 男声", "English · Male"),
        VoicePreset("aura-2-arcas-en", "Arcas", "英语 · 男声", "English · Male"),
        VoicePreset("aura-2-aries-en", "Aries", "英语 · 男声", "English · Male"),
    )
    else -> emptyList()
}
