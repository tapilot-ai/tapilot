package lifeos.md.tapilot.voice

import lifeos.md.tapilot.speech.SpeechRecognition

interface SpeechPlayback : AutoCloseable {
    fun speak(text: String, onStarted: () -> Unit, callback: (Result<Unit>) -> Unit)
    fun stop()
}

enum class VoicePhase { Idle, Preparing, Listening, Recognizing, Thinking, Speaking, Paused, Error }
data class VoiceState(val active: Boolean = false, val phase: VoicePhase = VoicePhase.Idle,
    val transcript: String = "", val response: String = "", val level: Float = 0f, val error: String = "", val listeningTimedOut: Boolean = false, val transcriptPending: Boolean = false, val noSpeechRecognized: Boolean = false, val previewingTranscript: Boolean = false,
    val editingTranscript: Boolean = false) {
    val followUpListeningExpired: Boolean get() = active && phase == VoicePhase.Paused && listeningTimedOut && !transcriptPending && response.isNotBlank()
}
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
    private var previewRemaining: Long? = null
    private var previewTickAt: Long? = null
    private var foreground = true
    private var awaiting = false
    private var busy = false
    private var started = 0L
    private var quietSince: Long? = null
    private var lastAudio: Long? = null
    private var lastTranscriptChange: Long? = null
    private var speechEnded = false
    private var noiseFloor = 1f
    private var peakLevel = 0f
    private var voiceFrames = 0
    private val seen = mutableSetOf<String>()
    private var replies = emptyList<VoiceReply>()
    private var confirmation: String? = null
    private var spokenConfirmation: String? = null
    private var playbackFailed = false
    private var playbackEnabled = true
    private var reading = false
    private var acknowledging = false
    private var manuallyPaused = false

    private fun change(next: VoiceState) {
        state = if (previewRemaining == null) next.copy(previewingTranscript = false) else next
        onChange(state)
    }
    fun start(provider: String, key: String, language: String, previous: List<VoiceReply>) {
        stop()
        this.provider = provider; this.key = key; this.language = language
        foreground = true; manuallyPaused = false; playbackFailed = false
        seen.addAll(previous.filter { it.complete }.map { it.id }); replies = previous
        change(VoiceState(active = true))
        listen()
    }

    private fun cancelAudio() { previewRemaining = null; previewTickAt = null; reading = false; acknowledging = false; generation++; recognition.cancel(); playback.stop() }
    private fun listen() {
        if (!state.active || !foreground) return
        playbackFailed = false
        cancelAudio()
        val token = generation
        speechEnded = false; lastTranscriptChange = null
        started = now(); quietSince = null; lastAudio = null; noiseFloor = 1f; peakLevel = 0f; voiceFrames = 0
        change(state.copy(phase = VoicePhase.Preparing, transcript = "", level = 0f, error = "", listeningTimedOut = false, transcriptPending = false, noSpeechRecognized = false, editingTranscript = false))
        fun current() = state.active && foreground && generation == token
        runCatching {
            recognition.start(provider, key, language,
                onLevel = { level -> dispatch {
                    if (current() && state.phase in listOf(VoicePhase.Preparing, VoicePhase.Listening)) {
                        val time = now()
                        // Calibrate from observed audio across the full normalized range.
                        // A fixed initial ceiling mistakes louder background noise for speech.
                        // Never raise the floor during sustained speech.
                        peakLevel = maxOf(peakLevel, level)
                        // AudioRecord can emit digital silence while the microphone warms up.
                        // Zero is silence for this frame, not a measured ambient-noise baseline.
                        if (level > 0f) noiseFloor = minOf(noiseFloor, level)
                        val speaking = level > maxOf(.08f, noiseFloor + .06f)
                        if (speaking) { voiceFrames++; quietSince = null }
                        else if (quietSince == null || lastAudio == null || time - lastAudio!! > 300) quietSince = time
                        lastAudio = time
                        change(state.copy(level = level))
                    }
                } }, onTranscript = { text -> dispatch {
                    if (current() && text.isNotBlank()) {
                        if (text.trim() != state.transcript.trim()) lastTranscriptChange = now()
                        change(state.copy(transcript = text, transcriptPending = true))
                    }
                } }, onSpeechBoundary = { ended -> dispatch {
                    if (current() && state.phase in listOf(VoicePhase.Preparing, VoicePhase.Listening)) speechEnded = ended
                } }, onError = { error -> dispatch { if (current()) fail(error) } }) { result -> dispatch {
                if (current() && state.phase == VoicePhase.Preparing) result.fold({ change(state.copy(phase = VoicePhase.Listening)) }, { fail(it) })
            } }
        }.onFailure { fail(it) }
    }

    /** Require observed quiet audio, never infer silence from missing callbacks. */
    fun tick(autoSend: Boolean = true, previewVisible: Boolean = true) {
        if (!state.active || !foreground) return
        previewRemaining?.let { remaining ->
            val time = now()
            val elapsed = if (previewVisible) previewTickAt?.let { (time - it).coerceAtLeast(0) } ?: 0 else 0
            previewTickAt = if (previewVisible) time else null
            val next = (remaining - elapsed).coerceAtLeast(0)
            previewRemaining = next
            if (previewVisible && next == 0L) sendPendingTranscript()
            return
        }
        if (state.phase !in listOf(VoicePhase.Preparing, VoicePhase.Listening)) return
        val time = now()
        // A recognized answer must not wait forever for quiet in a noisy room.
        // Only changed text resets this timer, not repeated provider hypotheses.
        val textAt = lastTranscriptChange
        if (autoSend && state.phase == VoicePhase.Listening && state.transcriptPending &&
            textAt != null && time - textAt >= 3000) {
            finishUtterance()
            return
        }
        // This deadline also applies to preparation and press-and-hold entry.
        if (time - started >= 30_000) {
            pause()
            change(state.copy(listeningTimedOut = true))
            return
        }
        // Provider VAD distinguishes background noise from speech; amplitude alone cannot.
        if (autoSend && speechEnded && state.phase == VoicePhase.Listening && state.transcriptPending) {
            finishUtterance()
            return
        }
        val quiet = quietSince ?: return
        val audio = lastAudio ?: return
        // Noise or playback tail can cross the amplitude threshold without speech.
        // Keep the current stream open until words arrive or its original deadline expires.
        if (autoSend && state.phase == VoicePhase.Listening && state.transcriptPending &&
            (voiceFrames >= 4 || (state.transcript.isNotBlank() && peakLevel > maxOf(.08f, noiseFloor + .06f))) &&
            time - audio <= 300 && time - quiet >= 1500) finishUtterance()
    }

    fun finishUtterance(continueWhenEmpty: Boolean = false, previewBeforeSend: Boolean = false) {
        if (state.phase !in listOf(VoicePhase.Listening, VoicePhase.Preparing)) return
        val token = generation
        change(state.copy(phase = VoicePhase.Recognizing, level = 0f))
        runCatching { recognition.finish { result -> dispatch {
            if (!state.active || !foreground || token != generation) return@dispatch
            result.fold({ text ->
                if (text.isBlank()) { if (continueWhenEmpty) submitUtterance(continueText()) else noSpeech() } else {
                    if (previewBeforeSend) {
                        cancelAudio()
                        previewRemaining = 1_500L
                        previewTickAt = null
                        change(state.copy(transcript = text.trim(), transcriptPending = true, previewingTranscript = true,
                            editingTranscript = false))
                    } else submitUtterance(text)
                }
            }, { if (it.message == "No speech recognized") { if (continueWhenEmpty) submitUtterance(continueText()) else noSpeech() } else fail(it) })
        } } }.onFailure { fail(it) }
    }

    private fun noSpeech() {
        pause()
        change(state.copy(noSpeechRecognized = true))
    }

    /** Entering the editor stops both the microphone and the automatic send. */
    fun beginTranscriptEdit() {
        if (!state.active || !state.transcriptPending || confirmation != null ||
            (state.phase != VoicePhase.Paused && !state.previewingTranscript)) return
        pause()
        change(state.copy(editingTranscript = true))
    }

    fun editTranscript(text: String) {
        if (state.active && state.editingTranscript && state.phase == VoicePhase.Paused)
            change(state.copy(transcript = text))
    }

    fun sendPendingTranscript() {
        if (!state.active || !foreground || !state.transcriptPending || state.transcript.isBlank() || confirmation != null ||
            (state.phase != VoicePhase.Paused && !state.previewingTranscript)) return
        submitUtterance(state.transcript)
    }

    private fun submitUtterance(text: String) {
        previewRemaining = null; previewTickAt = null
        manuallyPaused = false
        seen.addAll(replies.filter { it.complete }.map { it.id })
        awaiting = true
        change(state.copy(phase = VoicePhase.Thinking, transcript = text.trim(), transcriptPending = false, previewingTranscript = false,
            level = 0f, error = "", listeningTimedOut = false, noSpeechRecognized = false, editingTranscript = false))
        acknowledge()
        onQuestion(text.trim())
    }

    private fun continueText() = if (language == "zh-CN") "继续" else "Continue"

    /** Continue the conversation, rather than silently reopening an empty microphone. */
    fun continueConversation() {
        if (!state.active || !foreground || confirmation != null || state.phase == VoicePhase.Recognizing) return
        // A new answer takes priority over waiting for an earlier task. Native submit
        // persists it as a steering input when that task is still running.
        if (state.phase in listOf(VoicePhase.Listening, VoicePhase.Preparing)) {
            finishUtterance(continueWhenEmpty = true)
            return
        }
        if (state.transcriptPending && state.transcript.isNotBlank()) {
            submitUtterance(state.transcript)
            return
        }
        // A second click must not enqueue a duplicate while the turn is being dispatched.
        if (busy || awaiting) {
            awaiting = true
            manuallyPaused = false
            cancelAudio()
            change(state.copy(phase = VoicePhase.Thinking, level = 0f, error = ""))
            update(replies, busy, confirmation)
            return
        }
        submitUtterance(continueText())
    }

    fun update(next: List<VoiceReply>, busy: Boolean, confirmation: String?) {
        replies = next; this.busy = busy; this.confirmation = confirmation
        if (state.active && confirmation == null && spokenConfirmation != null) {
            spokenConfirmation = null
            if (foreground && state.phase == VoicePhase.Paused && !manuallyPaused) {
                change(state.copy(phase = VoicePhase.Thinking))
                if (!busy && !awaiting) readyForNextTurn()
            }
        }
        if (!state.active || !foreground || state.phase in listOf(VoicePhase.Error, VoicePhase.Paused)) return
        if (busy && !awaiting && next.any { !it.complete && it.id !in seen }) {
            awaiting = true
            if (state.phase !in listOf(VoicePhase.Preparing, VoicePhase.Listening, VoicePhase.Recognizing)) {
                cancelAudio()
                change(state.copy(phase = VoicePhase.Thinking, level = 0f, error = ""))
            }
        }
        if (confirmation != null && confirmation != spokenConfirmation) {
            spokenConfirmation = confirmation
            read(if (language == "zh-CN") "请确认手机操作：$confirmation。请点击执行或取消。" else "Please confirm the phone operation: $confirmation. Tap Run or Cancel.")
            return
        }
        if (!awaiting || busy || confirmation != null || (state.phase == VoicePhase.Speaking || acknowledging)) return
        val reply = next.lastOrNull { it.complete && it.text.isNotBlank() && it.id !in seen } ?: return
        // A user who has already begun a new utterance gets to finish it first.
        if (state.phase == VoicePhase.Recognizing || state.transcriptPending ||
            (state.phase in listOf(VoicePhase.Preparing, VoicePhase.Listening) && voiceFrames >= 4)) return
        seen.add(reply.id); awaiting = false
        read(reply.text)
    }

    private fun acknowledge() {
        cancelAudio()
        if (!playbackEnabled) return
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
        if (!playbackEnabled) {
            change(state.copy(response = text, error = ""))
            if (confirmation != null) change(state.copy(phase = VoicePhase.Paused)) else readyForNextTurn()
            return
        }
        reading = true
        val token = generation
        change(state.copy(phase = VoicePhase.Thinking, response = text, level = 0f, error = ""))
        runCatching { playback.speak(text,
            onStarted = { dispatch { if (state.active && token == generation) change(state.copy(phase = VoicePhase.Speaking)) } },
            callback = { result -> dispatch {
                if (!state.active || !foreground || token != generation) return@dispatch
                reading = false
                result.fold({
                    if (confirmation != null) change(state.copy(phase = VoicePhase.Paused)) else readyForNextTurn()
                }, { playbackFailed = true; fail(it) })
            } }) }.onFailure { playbackFailed = true; fail(it) }
    }

    /** Mute output without cancelling the task or restarting an active microphone. */
    fun setPlaybackEnabled(enabled: Boolean) {
        if (playbackEnabled == enabled) return
        playbackEnabled = enabled
        if (enabled || !state.active || (!reading && !acknowledging && !playbackFailed)) return
        cancelAudio(); playbackFailed = false
        if (state.phase == VoicePhase.Paused || !foreground) return
        if (confirmation != null) change(state.copy(phase = VoicePhase.Paused, error = ""))
        else if (awaiting) {
            change(state.copy(phase = VoicePhase.Thinking, error = ""))
            update(replies, busy, confirmation)
        } else readyForNextTurn()
    }

    private fun readyForNextTurn() {
        if (!state.transcriptPending) listen() else pause(manual = false)
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
        if (!value) pause(manual = false) else if (!manuallyPaused) {
            // Foreground restoration must never erase or auto-submit an interrupted answer.
            if (state.transcriptPending) return
            if (awaiting || playbackFailed) resume() else readyForNextTurn()
        }
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
