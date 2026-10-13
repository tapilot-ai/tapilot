package lifeos.md.tapilot.bridge

import android.content.Context

class ModelCredentialStore(context: Context) {
    private val storage = EncryptedPreferences(context, "midscene-secure-model-credentials", "midscene-model-credentials")
    fun getAll(): Map<String, String> = storage.keys().filter { it in PRESET_IDS }.associateWith { storage.get(it).orEmpty() }
    fun set(preset: String, apiKey: String) {
        require(preset in PRESET_IDS) { "Unsupported model preset: $preset" }
        if (apiKey.isEmpty()) delete(preset) else storage.set(preset, apiKey)
    }
    fun delete(preset: String) {
        require(preset in PRESET_IDS) { "Unsupported model preset: $preset" }
        storage.remove(preset)
    }
    private companion object {
        val PRESET_IDS = setOf(
            "doubao",
            "qwen",
            "deepseek",
            "gemini",
            "openai",
            "kimi",
            "mimo",
            "glm",
            "custom",
            "speech-volcengine",
            "speech-deepgram",
            "speech-polish",
            "speech-output-volcengine",
            "speech-output-deepgram",
        )
    }
}
