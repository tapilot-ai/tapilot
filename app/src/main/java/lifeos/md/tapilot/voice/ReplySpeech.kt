package lifeos.md.tapilot.voice

/** Completed answers only. Initial history is a baseline, never an autoplay queue. */
class ReplySpeech(private val player: SpeechPlayback, private val dispatch: (() -> Unit) -> Unit,
    private val changed: (String, String) -> Unit) : AutoCloseable {
    private var conversation: String? = null
    private val seen = mutableSetOf<String>()
    private var generation = 0
    var speakingId = ""; private set
    fun update(id: String, replies: List<VoiceReply>, enabled: Boolean) {
        if (conversation != id) { stop(); conversation = id; seen.clear(); seen.addAll(replies.filter { it.complete }.map { it.id }); return }
        val fresh = replies.filter { it.complete && it.text.isNotBlank() && it.id !in seen }
        seen.addAll(replies.filter { it.complete }.map { it.id })
        if (enabled) fresh.lastOrNull()?.let { play(it) }
    }
    fun play(reply: VoiceReply) {
        stop(); val token = generation; speakingId = reply.id; changed(speakingId, "")
        runCatching { player.speak(reply.text, {}, { result -> dispatch {
            if (token == generation) { speakingId = ""; changed("", result.exceptionOrNull()?.message.orEmpty()) }
        } }) }.onFailure { if (token == generation) { speakingId = ""; changed("", it.message.orEmpty()) } }
    }
    fun stop() { generation++; player.stop(); speakingId = ""; changed("", "") }
    override fun close() { stop(); player.close() }
}
