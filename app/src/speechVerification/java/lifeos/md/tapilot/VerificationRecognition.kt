package lifeos.md.tapilot

import android.os.Handler
import android.os.Looper
import lifeos.md.tapilot.bridge.SpeechRecognition
import lifeos.md.tapilot.bridge.SpeechAudio
import lifeos.md.tapilot.bridge.SpeechCaptureBoundary

/** Synthetic transport in the isolated APK; exercises the production audio drain boundary. */
class VerificationRecognition(private val startDelay: Long = 0, private val failStart: Boolean = false) : SpeechRecognition {
    private val handler = Handler(Looper.getMainLooper())
    private var boundary = SpeechCaptureBoundary()
    private var audio = SpeechAudio(4)
    private var onText: (String) -> Unit = {}
    override val canRetry = false
    override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
        onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, callback: (Result<Unit>) -> Unit) {
        if (failStart) { callback(Result.failure(IllegalStateException("验证启动失败"))); return }
        boundary = SpeechCaptureBoundary()
        audio = SpeechAudio(4)
        audio.append(byteArrayOf(1, 2))
        onText = onTranscript
        handler.postDelayed({ onLevel(.7f); onText("保留最后"); callback(Result.success(Unit)) }, startDelay)
    }
    override fun finish(callback: (Result<String>) -> Unit) {
        boundary.requestFinish()
        // The last read arrives AFTER release, as a real buffered microphone read can.
        handler.postDelayed({
            if (boundary.acceptsAudio()) { audio.append(byteArrayOf(3, 4)); onText("保留最后两个字") }
        }, 256)
        handler.postDelayed({ boundary.captureDrained(); check(boundary.claimFinal()) }, 320)
        // Deliberately deliver after cancellation to exercise the host's stale-result guard too.
        handler.postDelayed({ callback(Result.success(if (audio.snapshot().size == 4) "保留最后两个字" else "保留最后")) }, maxOf(1600, startDelay))
    }
    override fun retry(apiKey: String, callback: (Result<String>) -> Unit) = finish(callback)
    override fun cancel() = Unit
    override fun close() { handler.removeCallbacksAndMessages(null) }
}
