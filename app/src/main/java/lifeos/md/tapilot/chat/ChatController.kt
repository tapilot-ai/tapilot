package lifeos.md.tapilot.chat

import android.app.Activity
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import lifeos.md.tapilot.bridge.ModelCredentialStore
import lifeos.md.tapilot.bridge.SpeechRecognition
import org.json.JSONObject

/** Native UI state and microphone lifecycle; production commands go directly to Kotlin. */
class ChatController(
    private val activity: Activity,
    private val engine: WebView? = null,
    private val onSurfaceChanged: (Boolean) -> Unit,
    private val picker: (String, (Result<JSONObject?>) -> Unit) -> Unit,
    private val speech: SpeechRecognition = lifeos.md.tapilot.speech.PolishingSpeechRecognition(activity),
    private val speechKey: (String) -> String? = { provider -> ModelCredentialStore(activity).getAll()["speech-$provider"] },
    private val nativeCommand: ((JSONObject) -> Unit)? = null,
) : AutoCloseable {
    var state by mutableStateOf(ChatState())
        private set
    var draft by mutableStateOf("")
        private set
    var recording by mutableStateOf(false)
        private set
    var willCancel by mutableStateOf(false)
        private set
    var willSend by mutableStateOf(false)
        private set
    var transcribing by mutableStateOf(false)
        private set
    var liveTranscript by mutableStateOf("")
        private set
    var recordingSeconds by mutableStateOf(0)
        private set
    var audioLevels by mutableStateOf(List(24) { 0f })
        private set
    var canRetrySpeech by mutableStateOf(false)
        private set
    var picking by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var focusRequest by mutableStateOf(0)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var speechGeneration = 0
    private var recordingStarted = 0L
    private var lastLevelUpdate = 0L
    private var retryProvider = ""
    private var retrySend = false
    private val recordingClock = object : Runnable {
        override fun run() {
            if (!recording || closed) return
            recordingSeconds = ((SystemClock.elapsedRealtime() - recordingStarted) / 1000).toInt()
            if (recordingSeconds >= 60) cancelRecording() else handler.postDelayed(this, 250)
        }
    }

    @JavascriptInterface
    fun update(payload: String) {
        val next = runCatching { ChatState.parse(payload) }.getOrNull() ?: return
        accept(next)
    }

    fun accept(next: ChatState) {
        handler.post {
            if (closed) return@post
            if (state.activeConversationId != next.activeConversationId && (recording || transcribing || canRetrySpeech)) {
                cancelRecording()
                error = ""
            }
            if (!state.ready || next.draftEpoch != state.draftEpoch) draft = next.draft
            if (next.error.isNotBlank() && next.errorEpoch != state.errorEpoch) error = next.error
            val surfaceChanged = state.webSurfaceOpen != next.webSurfaceOpen
            state = next
            if (surfaceChanged) onSurfaceChanged(next.webSurfaceOpen)
        }
    }

    @JavascriptInterface
    fun requestFocus() { handler.post { if (!closed) focusRequest++ } }

    fun command(action: String, text: String? = null, id: String? = null) {
        val payload = JSONObject().put("action", action)
        text?.let { payload.put("text", it) }
        id?.let { payload.put("id", it) }
        dispatch(payload)
    }

    private fun dispatch(payload: JSONObject) {
        if (closed) return
        if (nativeCommand != null) nativeCommand.invoke(payload)
        else engine?.evaluateJavascript("window.__tapilotNativeChatCommand?.($payload)", null)
    }

    fun edit(text: String) {
        draft = text
        command("draft", text)
    }

    fun send() {
        if (!state.ready || state.running || recording || transcribing || picking) return
        if (draft.isBlank() && state.attachment == null) return
        error = ""
        activity.currentFocus?.let {
            activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        command("send", draft)
    }

    fun confirm(accepted: Boolean, instruction: String) {
        dispatch(JSONObject().put("action", "confirm").put("accepted", accepted).put("text", instruction))
    }

    fun pick(kind: String) {
        if (state.running || recording || transcribing || picking) return
        picking = true
        picker(kind) { result -> handler.post {
            if (closed) return@post
            picking = false
            result.fold(onSuccess = { attachment ->
                if (attachment != null) dispatch(JSONObject().put("action", "attachment").put("attachment", attachment))
            }, onFailure = { showError(it.message.orEmpty()) })
        } }
    }

    fun prepareRecording() {
        if (state.ready && !state.running && !recording && !transcribing && !picking && draft.isEmpty()) speech.prepare()
    }
    fun discardPreparedRecording() = speech.cancelPreparation()

    fun startRecording() {
        if (!state.ready || state.running || recording || transcribing || picking) return
        error = ""
        val key = runCatching { speechKey(state.speechProvider) }.getOrNull()
        if (key.isNullOrBlank()) {
            showError(if (state.chinese) "请先填写语音转写 API 配置" else "Configure speech API first")
            command("settings")
            return
        }
        canRetrySpeech = false
        retryProvider = state.speechProvider
        retrySend = false
        liveTranscript = ""
        recordingSeconds = 0
        audioLevels = List(24) { 0f }
        recordingStarted = SystemClock.elapsedRealtime()
        lastLevelUpdate = 0
        handler.post(recordingClock)
        speechGeneration++
        val generation = speechGeneration
        willCancel = false
        willSend = false
        recording = true
        runCatching {
            speech.start(state.speechProvider, key, if (state.chinese) "zh-CN" else "en-US",
                onLevel = { level -> handler.post {
                    val now = SystemClock.elapsedRealtime()
                    if (!closed && recording && generation == speechGeneration && now - lastLevelUpdate >= 80) {
                        audioLevels = audioLevels.drop(1) + level
                        lastLevelUpdate = now
                    }
                } }, onTranscript = { text -> handler.post {
                    if (!closed && generation == speechGeneration && (recording || transcribing)) liveTranscript = text
                } }, onError = { failure -> handler.post {
                    if (!closed && generation == speechGeneration) speechFailure(failure)
                } }) { result ->
                handler.post {
                    if (!closed && generation == speechGeneration) result.onFailure {
                        speechFailure(it)
                    }
                }
            }
        }.onFailure { speechFailure(it) }
    }

    fun moveRecording(gesture: RecordingGesture) {
        if (!recording) return
        willCancel = gesture == RecordingGesture.Cancel
        willSend = gesture == RecordingGesture.Send
    }

    fun releaseRecording() {
        if (!recording) return
        if (willCancel) { cancelRecording(); return }
        handler.removeCallbacks(recordingClock)
        val shouldSend = willSend
        retrySend = shouldSend
        recording = false
        transcribing = true
        val generation = speechGeneration
        runCatching {
            speech.finish { result -> handler.post {
                if (closed || generation != speechGeneration) return@post
                transcribing = false
                result.fold(onSuccess = { text ->
                    edit(lifeos.md.tapilot.speech.appendSpeechTranscript(draft, text))
                    if (shouldSend) send() else focusRequest++
                }, onFailure = { speechFailure(it) })
            } }
        }.onFailure { speechFailure(it) }
    }

    private fun speechFailure(failure: Throwable) {
        if (recording) retrySend = false
        recording = false
        transcribing = false
        handler.removeCallbacks(recordingClock)
        canRetrySpeech = speech.canRetry
        val prefix = if (state.chinese) {
            if (canRetrySpeech) "转写失败，原录音已保留，可重试。" else "语音转写失败，请检查 API 配置或重新录音。"
        } else if (canRetrySpeech) "Transcription failed. Retry the saved recording." else "Transcription failed. Check API settings or record again."
        showError("$prefix ${failure.message.orEmpty()}")
    }

    fun retrySpeech() {
        if (!canRetrySpeech || recording || transcribing || state.running || picking) return
        val key = speechKey(retryProvider).orEmpty()
        error = ""
        canRetrySpeech = false
        transcribing = true
        val generation = speechGeneration
        runCatching {
            speech.retry(key) { result -> handler.post {
                if (closed || generation != speechGeneration) return@post
                transcribing = false
                result.fold(onSuccess = { text ->
                    edit(lifeos.md.tapilot.speech.appendSpeechTranscript(draft, text))
                    if (retrySend) send() else focusRequest++
                }, onFailure = { speechFailure(it) })
            } }
        }.onFailure { speechFailure(it) }
    }

    fun cancelRecording() {
        speech.cancelPreparation()
        handler.removeCallbacks(recordingClock)
        canRetrySpeech = false
        speechGeneration++
        speech.cancel()
        recording = false
        transcribing = false
        willCancel = false
        willSend = false
    }

    fun showError(message: String) { error = message }
    fun dismissError() {
        error = ""
        if (canRetrySpeech) { speech.cancel(); canRetrySpeech = false }
    }

    override fun close() {
        closed = true
        cancelRecording()
        handler.removeCallbacksAndMessages(null)
        speech.close()
    }
}
