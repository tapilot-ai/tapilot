package lifeos.md.tapilot.speech

import android.content.Context
import lifeos.md.tapilot.speech.SpeechRecognition
import lifeos.md.tapilot.speech.SpeechTranscriber
import lifeos.md.tapilot.chatgpt.ChatGptAccounts
import lifeos.md.tapilot.conversation.ConversationStore
import java.io.File

internal fun transcriptPolisher(context: Context): TranscriptPolisher {
    val store = SpeechPolishStore(context)
    val settings = store.load()
    val useChatGpt = ConversationStore(File(context.filesDir, "native-conversation.json"))
        .read().optBoolean("chatgptConversation")
    if (!useChatGpt) return SpeechPolisher(settings, runCatching { store.key() }.getOrDefault(""))
    val accounts = ChatGptAccounts.get(context)
    val account = accounts.activeId()
    return ChatGptSpeechPolisher(settings, accounts.selectedModel(account), { force -> accounts.accessToken(account, force) })
}

/** Shared by native chat, the global assistant and the legacy speech bridge. */
internal class PolishingSpeechRecognition(
    private val recognition: SpeechRecognition,
    private val sessionFactory: ((String) -> Unit) -> SpeechPolishSession,
) : SpeechRecognition {
    constructor(activity: Context, recognition: SpeechRecognition = SpeechTranscriber(activity)) : this(recognition, { display ->
        val polisher = transcriptPolisher(activity)
        SpeechPolishSession(polisher::polish, display, polisher::cancel)
    })
    private var session: SpeechPolishSession? = null
    override fun prepare() = recognition.prepare()
    override fun cancelPreparation() = recognition.cancelPreparation()
    override val canRetry get() = recognition.canRetry
    override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
        onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, onSpeechBoundary: (Boolean) -> Unit, callback: (Result<Unit>) -> Unit) {
        session?.cancel()
        val current = sessionFactory(onTranscript)
        session = current
        try { recognition.start(provider, apiKey, language, onLevel, { error -> current.cancel(); onError(error) },
            current::update, onSpeechBoundary) { result -> if (result.isFailure) current.cancel(); callback(result) }
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
