package lifeos.md.tapilot

import android.os.Handler
import android.os.Looper
import lifeos.md.tapilot.speech.SpeechRecognition
import lifeos.md.tapilot.speech.SpeechAudio
import lifeos.md.tapilot.speech.SpeechCaptureBoundary

/** Synthetic transport in the isolated APK; exercises the production audio drain boundary. */
class VerificationRecognition(private val startDelay: Long = 0, private val failStart: Boolean = false, private val checkPreparation: Boolean = false) : SpeechRecognition {
    private val handler = Handler(Looper.getMainLooper())
    private var boundary = SpeechCaptureBoundary()
    private var audio = SpeechAudio(4)
    private var onText: (String) -> Unit = {}
    private var prepared = false
    private var opening = ""
    var preparations = 0; private set
    var discards = 0; private set
    var starts = 0; private set
    override fun prepare() { prepared = true; preparations++ }
    override fun cancelPreparation() { if (prepared) discards++; prepared = false }
    override val canRetry = false
    override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
        onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, onSpeechBoundary: (Boolean) -> Unit, callback: (Result<Unit>) -> Unit) {
        starts++
        opening = if (checkPreparation && prepared) "开头两个字，" else ""
        prepared = false
        if (failStart) { callback(Result.failure(IllegalStateException("验证启动失败"))); return }
        boundary = SpeechCaptureBoundary()
        audio = SpeechAudio(4)
        audio.append(byteArrayOf(1, 2))
        onText = onTranscript
        handler.postDelayed({ onLevel(.7f); onText(opening + "保留最后"); callback(Result.success(Unit)) }, startDelay)
    }
    override fun finish(callback: (Result<String>) -> Unit) {
        boundary.requestFinish()
        // The last read arrives AFTER release, as a real buffered microphone read can.
        handler.postDelayed({
            if (boundary.acceptsAudio()) { audio.append(byteArrayOf(3, 4)); onText(opening + "保留最后两个字") }
        }, 256)
        handler.postDelayed({ boundary.captureDrained(); check(boundary.claimFinal()) }, 320)
        // Deliberately deliver after cancellation to exercise the host's stale-result guard too.
        handler.postDelayed({ callback(Result.success(opening + if (audio.snapshot().size == 4) "保留最后两个字" else "保留最后")) }, maxOf(1600, startDelay))
    }
    override fun retry(apiKey: String, callback: (Result<String>) -> Unit) = finish(callback)
    override fun cancel() = Unit
    override fun close() { handler.removeCallbacksAndMessages(null) }
}
