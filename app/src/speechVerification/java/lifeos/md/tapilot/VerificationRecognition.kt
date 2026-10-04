package lifeos.md.tapilot

import android.os.Handler
import android.os.Looper
import lifeos.md.tapilot.bridge.SpeechRecognition

/** Synthetic recognition only in the separately named speechVerification APK. No microphone, keys or network. */
class VerificationRecognition(private val startDelay: Long = 0, private val failStart: Boolean = false) : SpeechRecognition {
    private val handler = Handler(Looper.getMainLooper())
    override val canRetry = false
    override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
        onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, callback: (Result<Unit>) -> Unit) {
        if (failStart) { callback(Result.failure(IllegalStateException("验证启动失败"))); return }
        handler.postDelayed({ onLevel(.7f); onTranscript("语音测试文字"); callback(Result.success(Unit)) }, startDelay)
    }
    override fun finish(callback: (Result<String>) -> Unit) {
        // Deliberately deliver after cancellation to verify the host rejects stale results.
        handler.postDelayed({ callback(Result.success("语音测试文字")) }, maxOf(1600, startDelay))
    }
    override fun retry(apiKey: String, callback: (Result<String>) -> Unit) = finish(callback)
    override fun cancel() = Unit
    override fun close() { handler.removeCallbacksAndMessages(null) }
}
