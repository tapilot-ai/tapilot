package lifeos.md.tapilot.voice

import lifeos.md.tapilot.bridge.SpeechRecognition

interface SpeechPlayback : AutoCloseable {
    fun speak(text: String, onStarted: () -> Unit, callback: (Result<Unit>) -> Unit)
    fun stop()
}

enum class VoicePhase { Idle, Preparing, Listening, Recognizing, Thinking, Speaking, Paused, Error }
data class VoiceState(val active: Boolean = false, val phase: VoicePhase = VoicePhase.Idle,
    val transcript: String = "", val response: String = "", val level: Float = 0f, val error: String = "")
data class VoiceReply(val id: String, val text: String, val complete: Boolean)

/** All calls are serialized on the UI thread. Audio/network callbacks enter through dispatch. */
class VoiceConversation(
    private val recognition: SpeechRecognition,
    private val playback: SpeechPlayback,
    private val now: () -> Long,
    private val dispatch: (() -> Unit) -> Unit,
    private val onQuestion: (String) -> Unit,
    private val onChange: (VoiceState) -> Unit,
) : AutoCloseable {
    var state = VoiceState(); private set
    private var provider = ""
    private var key = ""
    private var language = ""
    private var generation = 0
    private var foreground = true
    private var awaiting = false
    private var busy = false
    private var started = 0L
    private var lastVoice = 0L
    private var voiceFrames = 0
    private var lastText = 0L
    private val seen = mutableSetOf<String>()
    private var replies = emptyList<VoiceReply>()
    private var confirmation: String? = null
    private var spokenConfirmation: String? = null
    private var playbackFailed = false
    private var acknowledging = false
    private var manuallyPaused = false

    private fun change(next: VoiceState) { state = next; onChange(next) }
    fun start(provider: String, key: String, language: String, previous: List<VoiceReply>) {
        stop()
        this.provider = provider; this.key = key; this.language = language
        foreground = true; manuallyPaused = false; playbackFailed = false
        seen.addAll(previous.map { it.id }); replies = previous
        change(VoiceState(active = true))
        listen()
    }

    private fun cancelAudio() { acknowledging = false; generation++; recognition.cancel(); playback.stop() }
    private fun listen() {
        if (!state.active || !foreground) return
        playbackFailed = false
        cancelAudio()
        val token = generation
        started = now(); lastVoice = 0; voiceFrames = 0; lastText = 0
        change(state.copy(phase = VoicePhase.Preparing, transcript = "", level = 0f, error = ""))
        fun current() = state.active && foreground && generation == token
        runCatching {
            recognition.start(provider, key, language,
                onLevel = { level -> dispatch {
                    if (current() && state.phase in listOf(VoicePhase.Preparing, VoicePhase.Listening)) {
                        // SpeechAudio reports a compressed dB level, rather than linear RMS.
                        if (level > .25f) { voiceFrames++; lastVoice = now() }
                        change(state.copy(level = level))
                    }
                } }, onTranscript = { text -> dispatch {
                    if (current() && text.isNotBlank()) {
                        if (text != state.transcript) lastText = now()
                        change(state.copy(transcript = text))
                    }
                } }, onError = { error -> dispatch { if (current()) fail(error) } }) { result -> dispatch {
                if (current()) result.fold({ change(state.copy(phase = VoicePhase.Listening)) }, { fail(it) })
            } }
        }.onFailure { fail(it) }
    }

    /** A short pause ends the utterance; idle listening pauses, long speech respects the ASR cap. */
    fun tick() {
        if (!state.active || !foreground || state.phase != VoicePhase.Listening) return
        val time = now()
        if ((state.transcript.isNotBlank() || (voiceFrames >= 4 && lastVoice > 0)) &&
            time - maxOf(lastVoice, lastText) >= 1200) finishUtterance()
        else if (time - started >= 30_000 && state.transcript.isBlank() && voiceFrames < 4) pause()
        else if (time - started >= 48_000) {
            if (state.transcript.isNotBlank() || voiceFrames >= 4) finishUtterance() else listen()
        }
    }

    fun finishUtterance() {
        if (state.phase !in listOf(VoicePhase.Listening, VoicePhase.Preparing)) return
        val token = generation
        change(state.copy(phase = VoicePhase.Recognizing, level = 0f))
        runCatching { recognition.finish { result -> dispatch {
            if (!state.active || !foreground || token != generation) return@dispatch
            result.fold({ text ->
                if (text.isBlank()) listen() else {
                    seen.addAll(replies.map { it.id })
                    awaiting = true
                    change(state.copy(phase = VoicePhase.Thinking, transcript = text.trim()))
                    acknowledge()
                    onQuestion(text.trim())
                }
            }, { if (it.message == "No speech recognized") listen() else fail(it) })
        } } }.onFailure { fail(it) }
    }

    fun update(next: List<VoiceReply>, busy: Boolean, confirmation: String?) {
        replies = next; this.busy = busy; this.confirmation = confirmation
        if (state.active && confirmation == null && spokenConfirmation != null) {
            spokenConfirmation = null
            if (foreground && state.phase == VoicePhase.Paused && !manuallyPaused) {
                change(state.copy(phase = VoicePhase.Thinking))
                if (!busy && !awaiting) listen()
            }
        }
        if (!state.active || !foreground || state.phase in listOf(VoicePhase.Error, VoicePhase.Paused)) return
        if (busy && !awaiting && next.any { !it.complete && it.id !in seen }) {
            awaiting = true
            cancelAudio()
            change(state.copy(phase = VoicePhase.Thinking, level = 0f, error = ""))
        }
        if (confirmation != null && confirmation != spokenConfirmation) {
            spokenConfirmation = confirmation
            read(if (language == "zh-CN") "请确认手机操作：$confirmation。请点击执行或取消。" else "Please confirm the phone operation: $confirmation. Tap Run or Cancel.")
            return
        }
        if (!awaiting || busy || confirmation != null || (state.phase == VoicePhase.Speaking || acknowledging)) return
        val reply = next.lastOrNull { it.complete && it.text.isNotBlank() && it.id !in seen } ?: return
        // A user who has already begun a new utterance gets to finish it first.
        if (state.phase == VoicePhase.Listening && (voiceFrames >= 4 || state.transcript.isNotBlank())) return
        seen.add(reply.id); awaiting = false
        read(reply.text)
    }

    private fun acknowledge() {
        cancelAudio()
        acknowledging = true
        val token = generation
        runCatching { playback.speak(if (language == "zh-CN") "收到。" else "Got it.", {}, { _ -> dispatch {
            if (!state.active || token != generation) return@dispatch
            acknowledging = false
            update(replies, busy, confirmation)
        } }) }.onFailure { acknowledging = false }
    }

    private fun read(text: String) {
        playbackFailed = false
        cancelAudio()
        val token = generation
        change(state.copy(phase = VoicePhase.Thinking, response = text, level = 0f, error = ""))
        runCatching { playback.speak(text,
            onStarted = { dispatch { if (state.active && token == generation) change(state.copy(phase = VoicePhase.Speaking)) } },
            callback = { result -> dispatch {
                if (!state.active || !foreground || token != generation) return@dispatch
                result.fold({
                    if (confirmation != null) change(state.copy(phase = VoicePhase.Paused)) else listen()
                }, { playbackFailed = true; fail(it) })
            } }) }.onFailure { playbackFailed = true; fail(it) }
    }

    /** Clicking interrupt stops sound immediately, then starts a new turn in the same context. */
    fun interrupt() { if (state.active && confirmation == null) { manuallyPaused = false; listen() } }
    fun pause(manual: Boolean = true) {
        if (!state.active) return
        if (manual) manuallyPaused = true
        cancelAudio(); change(state.copy(phase = VoicePhase.Paused, level = 0f))
    }
    fun resume() {
        if (!state.active || !foreground || confirmation != null) return
        manuallyPaused = false
        if (playbackFailed && state.response.isNotBlank()) read(state.response)
        else if (awaiting) {
            change(state.copy(phase = VoicePhase.Thinking, error = ""))
            update(replies, busy, confirmation)
        } else listen()
    }
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (!state.active) return
        if (!value) pause(manual = false) else if (!manuallyPaused) resume()
    }
    private fun fail(error: Throwable) {
        cancelAudio()
        change(state.copy(phase = VoicePhase.Error, error = error.message ?: "Voice service failed", level = 0f))
    }
    fun stop() {
        cancelAudio(); awaiting = false; seen.clear(); replies = emptyList()
        confirmation = null; spokenConfirmation = null
        change(VoiceState())
    }
    override fun close() { stop(); recognition.close(); playback.close() }
}
