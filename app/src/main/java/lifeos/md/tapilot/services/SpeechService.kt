package lifeos.md.tapilot.services

import android.content.Context
import lifeos.md.tapilot.bridge.ModelCredentialStore
import lifeos.md.tapilot.bridge.SpeechTranscriber
import lifeos.md.tapilot.speech.PolishingSpeechRecognition
import org.json.JSONObject

/** Owns transcription lifecycle and provider credentials, shared by all application entrypoints. */
internal class SpeechService(private val activity: Context, private val modelCredentialStore: ModelCredentialStore) : AutoCloseable {
    private val speechTranscriber = SpeechTranscriber(activity)
    private val speechRecognition = PolishingSpeechRecognition(activity, speechTranscriber)
    fun call(method: String, params: JSONObject, complete: (Result<Any?>) -> Unit) {
        fun success(value: Any?) = complete(Result.success(value))
        fun failure(error: Throwable) = complete(Result.failure(error))
        when (method) {
            "speech.checkConnection" -> {
                val provider = params.getString("provider")
                val key = modelCredentialStore.get(speechPreset(params)) ?: error("请先保存语音 API Key")
                speechTranscriber.checkConnection(provider, key) { result -> result.fold(
                    onSuccess = { success(JSONObject().put("available", true)) },
                    onFailure = { failure(it) },
                ) }
            }
            "speech.hasCredential" -> success(JSONObject().put("configured", modelCredentialStore.get(speechPreset(params))?.isNotBlank() == true),
            )
            "speech.setProvider" -> {
                val provider = params.getString("provider")
                require(provider == "volcengine" || provider == "deepgram")
                activity.getSharedPreferences("tapilot-speech", 0).edit()
                    .putString("provider", provider).apply()
                success(JSONObject.NULL)
            }
            "speech.setCredential" -> {
                modelCredentialStore.set(speechPreset(params), params.getString("apiKey"))
                success(JSONObject.NULL)
            }
            "speech.start" -> {
                val provider = params.getString("provider")
                val language = params.optString("language", "en-US")
                val key = modelCredentialStore.get(speechPreset(params)) ?: error("Speech API Key is missing")
                speechRecognition.start(provider, key, language) { outcome ->
                    outcome.fold(
                        onSuccess = { success(JSONObject.NULL) },
                        onFailure = { failure(it) },
                    )
                }
            }
            "speech.cancel" -> {
                speechRecognition.cancel()
                success(JSONObject.NULL)
            }
            "speech.finish" -> {
                try {
                    speechRecognition.finish { outcome ->
                        outcome.fold(
                            onSuccess = { success(JSONObject().put("text", it)) },
                            onFailure = { failure(it) },
                        )
                    }
                } catch (error: Throwable) {
                    speechRecognition.cancel()
                    throw error
                }
            }
            else -> throw UnsupportedOperationException("Unsupported speech method: $method")
        }
    }
    private fun speechPreset(params: JSONObject): String = when (
        params.getString("provider")
    ) {
        SpeechTranscriber.VOLCENGINE -> "speech-volcengine"
        SpeechTranscriber.DEEPGRAM -> "speech-deepgram"
        else -> throw IllegalArgumentException("Unsupported speech provider")
    }

    override fun close() = speechRecognition.close()
}
