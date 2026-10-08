package lifeos.md.tapilot.speech

import android.app.Activity
import lifeos.md.tapilot.bridge.SpeechRecognition
import lifeos.md.tapilot.bridge.SpeechTranscriber

/** Shared by native chat, the global assistant and the legacy speech bridge. */
internal class PolishingSpeechRecognition(
    private val recognition: SpeechRecognition,
    private val sessionFactory: ((String) -> Unit) -> SpeechPolishSession,
) : SpeechRecognition {
    constructor(activity: Activity, recognition: SpeechRecognition = SpeechTranscriber(activity)) : this(recognition, { display ->
        val store = SpeechPolishStore(activity)
        val polisher = SpeechPolisher(store.load(), kotlin.runCatching { store.key() }.getOrDefault(""))
        SpeechPolishSession(polisher::polish, display, polisher::cancel)
    })
    private var session: SpeechPolishSession? = null
    override val canRetry get() = recognition.canRetry
    override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
        onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, callback: (Result<Unit>) -> Unit) {
        session?.cancel()
        val current = sessionFactory(onTranscript)
        session = current
        try { recognition.start(provider, apiKey, language, onLevel, { error -> current.cancel(); onError(error) },
            current::update) { result -> if (result.isFailure) current.cancel(); callback(result) }
        } catch (error: Throwable) { current.cancel(); throw error }
    }
    override fun finish(callback: (Result<String>) -> Unit) {
        val current = session ?: error("No recording is in progress")
        recognition.finish { result -> result.fold({ text -> current.finish(text) { callback(Result.success(it)) } },
            { error -> current.cancel(); callback(Result.failure(error)) }) }
    }
    override fun retry(apiKey: String, callback: (Result<String>) -> Unit) {
        session?.cancel()
        val current = sessionFactory({})
        session = current
        recognition.retry(apiKey) { result -> result.fold({ text -> current.finish(text) { callback(Result.success(it)) } },
            { error -> current.cancel(); callback(Result.failure(error)) }) }
    }
    override fun cancel() { session?.cancel(); session = null; recognition.cancel() }
    override fun close() { cancel(); recognition.close() }
}
