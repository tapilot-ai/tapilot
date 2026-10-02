package lifeos.md.tapilot.chat

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import lifeos.md.tapilot.bridge.ModelCredentialStore
import lifeos.md.tapilot.bridge.SpeechTranscriber
import org.json.JSONObject

/** Native UI state and microphone lifecycle. The existing conversation agent owns task context. */
class ChatController(
    private val activity: Activity,
    private val engine: WebView,
    private val onSurfaceChanged: (Boolean) -> Unit,
    private val picker: (String, (Result<JSONObject?>) -> Unit) -> Unit,
) : AutoCloseable {
    var state by mutableStateOf(ChatState())
        private set
    var draft by mutableStateOf("")
        private set
    var recording by mutableStateOf(false)
        private set
    var willCancel by mutableStateOf(false)
        private set
    var willTranscribe by mutableStateOf(false)
        private set
    var transcribing by mutableStateOf(false)
        private set
    var picking by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var focusRequest by mutableStateOf(0)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val speech = SpeechTranscriber(activity)
    private val credentials = ModelCredentialStore(activity)
    private var closed = false
    private var speechGeneration = 0
    private var voiceSend = false
    val sendOnRelease: Boolean get() = voiceSend

    @JavascriptInterface
    fun update(payload: String) {
        val next = runCatching { ChatState.parse(payload) }.getOrNull() ?: return
        handler.post {
            if (closed) return@post
            if (!state.ready || next.draftEpoch != state.draftEpoch) draft = next.draft
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
        engine.evaluateJavascript("window.__tapilotNativeChatCommand?.($payload)", null)
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

    fun startRecording(sendOnRelease: Boolean) {
        if (!state.ready || state.running || recording || transcribing || picking) return
        error = ""
        val key = runCatching { credentials.getAll()["speech-${state.speechProvider}"] }.getOrNull()
        if (key.isNullOrBlank()) {
            showError(if (state.chinese) "请先填写语音转写 API 配置" else "Configure speech API first")
            command("settings")
            return
        }
        speechGeneration++
        val generation = speechGeneration
        voiceSend = sendOnRelease
        willCancel = false
        willTranscribe = false
        recording = true
        runCatching {
            speech.start(state.speechProvider, key, if (state.chinese) "zh-CN" else "en-US") { result ->
                handler.post {
                    if (!closed && generation == speechGeneration) result.onFailure {
                        recording = false
                        showError(it.message.orEmpty())
                    }
                }
            }
        }.onFailure { recording = false; speech.cancel(); showError(it.message.orEmpty()) }
    }

    fun moveRecording(gesture: RecordingGesture) {
        if (!recording) return
        willCancel = gesture == RecordingGesture.Cancel
        willTranscribe = gesture == RecordingGesture.Transcribe
    }

    fun releaseRecording() {
        if (!recording) return
        if (willCancel) { cancelRecording(); return }
        val shouldSend = voiceSend && !willTranscribe
        recording = false
        transcribing = true
        val generation = speechGeneration
        runCatching {
            speech.finish { result -> handler.post {
                if (closed || generation != speechGeneration) return@post
                transcribing = false
                result.fold(onSuccess = { text ->
                    edit(listOf(draft.trim(), text.trim()).filter { it.isNotBlank() }.joinToString(" "))
                    if (shouldSend) send() else focusRequest++
                }, onFailure = { showError(it.message.orEmpty()) })
            } }
        }.onFailure { transcribing = false; speech.cancel(); showError(it.message.orEmpty()) }
    }

    fun cancelRecording() {
        speechGeneration++
        speech.cancel()
        recording = false
        transcribing = false
        willCancel = false
        willTranscribe = false
    }

    fun showError(message: String) { error = message }
    fun dismissError() { error = "" }

    override fun close() {
        closed = true
        cancelRecording()
        handler.removeCallbacksAndMessages(null)
        speech.close()
    }
}
