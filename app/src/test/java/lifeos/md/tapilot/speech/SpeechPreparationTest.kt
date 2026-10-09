package lifeos.md.tapilot.speech

import lifeos.md.tapilot.bridge.SpeechRecognition
import org.junit.Assert.*
import org.junit.Test

class SpeechPreparationTest {
    @Test fun preparationAndDiscardNeverStartRecognitionOrPolishing() {
        var prepared = false; var starts = 0; var polishes = 0
        val raw = object : SpeechRecognition {
            override val canRetry = false
            override fun prepare() { prepared = true }
            override fun cancelPreparation() { prepared = false }
            override fun start(provider: String, apiKey: String, language: String, onLevel: (Float) -> Unit,
                onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, callback: (Result<Unit>) -> Unit) { starts++; callback(Result.success(Unit)) }
            override fun finish(callback: (Result<String>) -> Unit) { callback(Result.success("开头")) }
            override fun retry(apiKey: String, callback: (Result<String>) -> Unit) = finish(callback)
            override fun cancel() { prepared = false }
            override fun close() = cancel()
        }
        val speech = PolishingSpeechRecognition(raw) { display -> polishes++; SpeechPolishSession({ it }, display) }
        speech.prepare(); assertTrue(prepared); assertEquals(0, starts); assertEquals(0, polishes)
        speech.cancelPreparation(); assertFalse(prepared); assertEquals(0, starts); assertEquals(0, polishes)
        speech.prepare()
        speech.start("volcengine", "synthetic", "zh-CN", {}, {}, {}) {}
        assertTrue(prepared); assertEquals(1, starts); assertEquals(1, polishes)
        speech.close(); assertFalse(prepared)
    }
}
