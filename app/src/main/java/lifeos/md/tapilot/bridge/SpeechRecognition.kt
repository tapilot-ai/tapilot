package lifeos.md.tapilot.bridge

/** Hosts own gesture state; recognition can finish asynchronously after the finger lifts. */
interface SpeechRecognition : AutoCloseable {
    fun prepare() = Unit
    fun cancelPreparation() = Unit
    val canRetry: Boolean
    fun start(provider: String, apiKey: String, language: String,
        onLevel: (Float) -> Unit = {}, onError: (Throwable) -> Unit = {},
        onTranscript: (String) -> Unit = {}, onSpeechBoundary: (Boolean) -> Unit = {}, callback: (Result<Unit>) -> Unit)
    fun finish(callback: (Result<String>) -> Unit)
    fun retry(apiKey: String, callback: (Result<String>) -> Unit)
    fun cancel()
}
