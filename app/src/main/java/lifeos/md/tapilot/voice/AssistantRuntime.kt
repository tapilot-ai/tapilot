package lifeos.md.tapilot.voice

import lifeos.md.tapilot.AppText.text as appText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import lifeos.md.tapilot.bridge.*
import lifeos.md.tapilot.speech.SpeechTranscriber
import lifeos.md.tapilot.chat.ChatState
import lifeos.md.tapilot.conversation.*
import org.json.JSONObject
import java.io.File

/** Process-owned context and audio. Screens attach observers, never own a background conversation. */
class AssistantRuntime(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    var observe: ((ChatState) -> Unit)? = null
    var focus: (() -> Unit)? = null
    var picker: ((String, (Result<JSONObject?>) -> Unit) -> Unit)? = null
    var state = ChatState(); private set
    var voiceState by androidx.compose.runtime.mutableStateOf(VoiceState()); private set
    private var voicePhaseStartedAt = SystemClock.elapsedRealtime()
    var speakingId by androidx.compose.runtime.mutableStateOf(""); private set
    var outputError by androidx.compose.runtime.mutableStateOf(""); private set
    var startingVoice by androidx.compose.runtime.mutableStateOf(false); private set
    private var service: VoiceRuntimeService? = null
    private var voiceKey = ""
    private val floatingSession = FloatingVoiceSession()
    val floatingEntryGeneration get() = floatingSession.generation
    val floatingHold get() = floatingSession.held
    val floatingInputVisible get() = floatingHold || (floatingSession.startedWithHold && floatingVoice &&
        floatingVoiceError.isBlank() && (voiceState.previewingTranscript ||
        voiceState.phase in listOf(VoicePhase.Preparing, VoicePhase.Listening, VoicePhase.Recognizing)))
    val floatingCancelSelected get() = floatingSession.cancelSelected
    val floatingContextReady get() = floatingSession.contextReady
    val floatingVoice get() = floatingSession.active
    val floatingVoiceError get() = floatingSession.error
    fun beginFloatingHold() { endVoice(); floatingSession.begin(held = true) }
    fun beginFloatingRetry() { endVoice(); floatingSession.begin(held = false) }
    fun selectFloatingCancel(selected: Boolean) = floatingSession.selectCancel(selected)
    fun failFloatingEntry(message: String) { endVoice(); floatingSession.fail(message.ifBlank { appText("语音启动失败，请重试", "Unable to start voice input. Try again") }) }
    val floatingTranscript get() = voiceState.transcript
    var voiceEntryPending = false; private set
    private var openingUtterance: String? = null
    private var mutedUntilNextSend = false
    private val config = VoiceOutputSettings(context)
    var speechMuted by androidx.compose.runtime.mutableStateOf(config.muted); private set
    val device = lifeos.md.tapilot.device.AndroidDeviceService(context)
    private val conversationDevice = ConversationDeviceControl(device)
    val bridge = lifeos.md.tapilot.services.AppServices(context = context, device = conversationDevice,
        onProgress = { coordinator.phoneProgress(it) },
        onPickAttachment = { kind, callback -> picker?.invoke(kind, callback)
            ?: callback(Result.failure(IllegalStateException("Open Tapilot to select an attachment"))) })
    val coordinator: NativeConversationCoordinator = NativeConversationCoordinator(context, bridge,
        ConversationStore(File(context.filesDir, "native-conversation.json")), ::accept, { focus?.invoke() }, conversationDevice)
    private val replies = ReplySpeech(ApiSpeechPlayback(context) { state.speechProvider },
        { handler.post(it) }, { id, error -> speakingId = id; outputError = error; refreshService() })
    private val voice: VoiceConversation = VoiceConversation(SpeechTranscriber(context), ApiSpeechPlayback(context) { state.speechProvider },
        SystemClock::elapsedRealtime, { handler.post(it) },
        { text -> floatingSession.finishInput(); if (voiceEntryPending) openingUtterance = text else sendSpoken(text) },
        { next ->
            if (next.phase != voiceState.phase) {
                val time = SystemClock.elapsedRealtime()
                // Phase durations only: no transcript, credentials, or audio samples.
                android.util.Log.i("TapilotVoiceTiming", "${voiceState.phase}->${next.phase} elapsedMs=${time - voicePhaseStartedAt}")
                voicePhaseStartedAt = time
            }
            voiceState = next
            refreshService()
            if (next.phase == VoicePhase.Listening && floatingSession.consumeFinish()) {
                val token = floatingEntryGeneration
                handler.post { if (floatingSession.isCurrent(token) && !floatingHold && voiceState.phase == VoicePhase.Listening) voice.finishUtterance(previewBeforeSend = true) }
            }
        }, onListening = {
            if (!speechMuted && !floatingHold) runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, 35).also {
                    it.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                    handler.postDelayed({ it.release() }, 130)
                }
            }
        })
    private val clock = object : Runnable {
        override fun run() {
            if (!voiceState.active) return
            voice.tick(autoSend = !floatingHold, previewVisible = !floatingVoice || floatingInputVisible || floatingContextReady)
            voice.update(voiceReplies(), state.running, state.confirmation)
            handler.postDelayed(this, 100)
        }
    }
    private fun voiceReplies() = state.messages.filter { it.role == "assistant" }.map {
        VoiceReply(it.id, it.text, it.status !in listOf("running", "paused", "needs-confirmation")) }
    private fun hasOutputKey() = runCatching { ModelCredentialStore(context).let {
        !it.get("speech-output-${state.speechProvider}").isNullOrBlank() || !it.get("speech-${state.speechProvider}").isNullOrBlank() } }.getOrDefault(false)
    private fun accept(next: ChatState) {
        if (state.ready && state.activeConversationId != next.activeConversationId) endVoice()
        state = next
        if (voiceState.active) voice.update(voiceReplies(), next.running, next.confirmation)
        replies.update(next.activeConversationId, voiceReplies(), !speechMuted && config.autoRead && !mutedUntilNextSend && !voiceState.active && !startingVoice && hasOutputKey())
        observe?.invoke(next)
        refreshService()
    }
    fun attach(activity: Activity) { bridge.attach(activity) }
    fun detach() { bridge.attach(null); observe = null; focus = null; picker = null }
    fun command(value: JSONObject) {
        val action = value.optString("action")
        if (action in listOf("settings", "new", "select")) { endVoice(); replies.stop() }
        val spoken = action in listOf("send", "retry") && !voiceState.active && !speechMuted && config.autoRead && hasOutputKey() && state.ready && state.configured
        if (action in listOf("send", "retry")) { mutedUntilNextSend = false; coordinator.backgroundReplies = voiceState.active || spoken }
        if (spoken) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceRuntimeService::class.java))
            feedback()
        }
        coordinator.command(value)
        if (spoken) replies.play(VoiceReply("acknowledgement", if (state.chinese) "收到。" else "Got it.", true))
    }
    private fun feedback() {
        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            android.util.Log.d("TapilotVoice", "Receipt dispatched")
        if (speechMuted) return
        // Immediate local receipt; API acknowledgement follows without blocking the task.
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35).also {
            it.startTone(ToneGenerator.TONE_PROP_ACK, 100); handler.postDelayed({ it.release() }, 150)
        } }
    }
    private fun sendSpoken(text: String) {
        if (state.confirmation == null) {
            feedback(); command(JSONObject().put("action", "send").put("text", text).put("preserveDraft", true))
        }
    }
    fun prepareVoiceEntry() {
        coordinator.globalMode = true
        coordinator.openGlobalConversation()
        voiceEntryPending = true
        openingUtterance = null
    }
    fun finishVoiceEntry() {
        if (!voiceEntryPending) return
        voiceEntryPending = false
        val text = openingUtterance; openingUtterance = null
        text?.let(::sendSpoken)
    }
    fun startVoice(key: String) {
        replies.stop(); voiceKey = key; startingVoice = true
        runCatching { ContextCompat.startForegroundService(context,
            Intent(context, VoiceRuntimeService::class.java).setAction(VoiceRuntimeService.START_VOICE))
        }.onFailure { startingVoice = false; outputError = it.message.orEmpty(); voiceKey = "" }
    }
    fun serviceStarted(value: VoiceRuntimeService, startVoice: Boolean) {
        service = value
        if (startVoice && startingVoice) {
            voice.setPlaybackEnabled(!speechMuted)
            voice.start(state.speechProvider, voiceKey, if (state.chinese) "zh-CN" else "en-US", voiceReplies())
            startingVoice = false
            voiceKey = ""; handler.removeCallbacks(clock); handler.post(clock)
        }
        refreshService()
    }
    fun serviceDestroyed(value: VoiceRuntimeService) {
        if (service !== value) return
        service = null; endVoice()
        // Preserve a completed playback error for the settings / retry UI.
        if (speakingId.isNotBlank()) replies.stop()
    }
    private fun refreshService() { service?.refresh(voiceState, speakingId.isNotBlank(), state.running && !mutedUntilNextSend && !voiceState.active && !speechMuted && config.autoRead && hasOutputKey()) }
    fun endVoice() { floatingSession.end(); voiceEntryPending = false; openingUtterance = null; startingVoice = false; voiceKey = ""; if (voiceState.active) voice.stop(); handler.removeCallbacks(clock) }
    fun pauseVoice() = voice.pause()
    fun resumeVoice() = voice.resume()
    fun voicePrimaryAction() {
        if (voiceState.previewingTranscript || (voiceState.transcriptPending && voiceState.phase == VoicePhase.Paused)) {
            voice.sendPendingTranscript()
            return
        }
        when (voiceState.phase) {
            VoicePhase.Error -> voice.resume()
            VoicePhase.Paused -> voice.interrupt()
            VoicePhase.Recognizing -> Unit
            VoicePhase.Listening -> if (voiceState.transcriptPending) voice.finishUtterance() else voice.pause()
            VoicePhase.Preparing -> voice.pause()
            else -> voice.interrupt()
        }
    }
    fun beginVoiceTranscriptEdit() = voice.beginTranscriptEdit()
    fun editVoiceTranscript(text: String) = voice.editTranscript(text)
    fun interruptVoice() = voice.interrupt()
    fun finishUtterance() = voice.finishUtterance()
    fun completeFloatingContext(token: Int = floatingEntryGeneration) {
        if (!floatingSession.contextCaptured(token)) return
        if (state.ready) { coordinator.receiveLaunchContext(); finishVoiceEntry() }
    }
    fun releaseFloatingHold(cancel: Boolean) {
        if (!floatingHold) return
        if (cancel) { endVoice(); return }
        floatingSession.release()
        if (voiceState.phase == VoicePhase.Listening && floatingSession.consumeFinish()) voice.finishUtterance(previewBeforeSend = true)
    }
    fun toggleSpeechPlayback() {
        speechMuted = !speechMuted
        config.muted = speechMuted
        if (speechMuted) replies.stop()
        voice.setPlaybackEnabled(!speechMuted)
        refreshService()
    }
    fun stopSpeech() = replies.stop()
    fun read(reply: VoiceReply) {
        endVoice()
        ContextCompat.startForegroundService(context, Intent(context, VoiceRuntimeService::class.java))
        replies.play(reply)
    }
    fun clearOutputError() { outputError = "" }
    fun stopAudio() { mutedUntilNextSend = true; endVoice(); replies.stop() }
}
