package lifeos.md.tapilot.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ModelCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getAll(): Map<String, String> = preferences.all.mapNotNull { (preset, value) ->
        if (preset !in PRESET_IDS) return@mapNotNull null
        val encrypted = value as? String
            ?: error("Invalid encrypted credential for preset $preset")
        preset to decrypt(encrypted)
    }.toMap()

    fun set(preset: String, apiKey: String) {
        requirePreset(preset)
        if (apiKey.isEmpty()) {
            delete(preset)
            return
        }
        check(preferences.edit().putString(preset, encrypt(apiKey)).commit()) {
            "Unable to persist model credential"
        }
    }

    fun delete(preset: String) {
        requirePreset(preset)
        check(preferences.edit().remove(preset).commit()) {
            "Unable to delete model credential"
        }
    }

    private fun requirePreset(preset: String) {
        require(preset in PRESET_IDS) { "Unsupported model preset: $preset" }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(
            cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )
        return "$iv.$ciphertext"
    }

    private fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2) { "Invalid encrypted model credential" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            .toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "midscene-secure-model-credentials"
        const val KEY_ALIAS = "midscene-model-credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
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
        )
    }
}
