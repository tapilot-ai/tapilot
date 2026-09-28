package lifeos.md.tapilot.bridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/** Streams 16 kHz mono PCM to the two API providers supported by Doubao Say. */
internal class SpeechTranscriber(private val activity: Activity) : AutoCloseable {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val timeouts = Executors.newSingleThreadScheduledExecutor()
    private var active: Session? = null
    private var lastFailure: Throwable? = null

    private class Session(
        val provider: String,
        val audio: AudioRecord,
        var startCallback: ((Result<Unit>) -> Unit)?,
    ) {
        val recording = AtomicBoolean(true)
        val pending = ArrayDeque<ByteArray>()
        var pendingBytes = 0
        var totalBytes = 0
        var socket: WebSocket? = null
        var captureThread: Thread? = null
        var ready = false
        var finishing = false
        var finalSent = false
        var cancelled = false
        var sequence = 2
        var transcript = ""
        val finalSegments = mutableListOf<String>()
        var finishCallback: ((Result<String>) -> Unit)? = null
    }

    @Synchronized
    fun start(provider: String, apiKey: String, language: String, callback: (Result<Unit>) -> Unit) {
        require(provider == VOLCENGINE || provider == DEEPGRAM) { "Unsupported speech provider" }
        require(language == "zh-CN" || language == "en-US") { "Unsupported speech language" }
        require(apiKey.isNotBlank() && apiKey.length <= 4096 &&
            apiKey.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Invalid speech API Key" }
        check(active == null) { "Recording is already in progress" }
        lastFailure = null
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
            error("Microphone permission requested. Tap the microphone again after allowing access.")
        }
        val minimum = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "Microphone audio format is unavailable" }
        val bufferSize = maxOf(minimum, 3200)
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize,
        )
        check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone is unavailable" }
        val session = Session(provider, audio, callback)
        try {
            audio.startRecording()
            active = session
            session.captureThread = Thread(
                { capture(session, bufferSize) }, "tapilot-speech-capture",
            ).apply { start() }
            val request = Request.Builder()
                .url(if (provider == VOLCENGINE) VOLCENGINE_URL else "$DEEPGRAM_URL&language=$language")
                .apply {
                    if (provider == VOLCENGINE) {
                        header("X-Api-Key", apiKey)
                        header("X-Api-Resource-Id", RESOURCE_ID)
                        header("X-Api-Request-Id", UUID.randomUUID().toString())
                    } else {
                        header("Authorization", "Token $apiKey")
                    }
                }.build()
            session.socket = client.newWebSocket(request, listener(session))
            session.startCallback = null
            callback(Result.success(Unit))
            timeouts.schedule({
                if (!session.ready) fail(session, IOException("Speech service connection timed out"))
            }, 12, TimeUnit.SECONDS)
        } catch (error: Throwable) {
            if (active === session) fail(session, error)
            else {
                runCatching { audio.release() }
                callback(Result.failure(error))
            }
        }
    }

    @Synchronized
    fun finish(callback: (Result<String>) -> Unit) {
        val session = active ?: throw (lastFailure ?: IllegalStateException("No recording is in progress"))
        synchronized(session) {
            check(!session.finishing) { "Transcription is already finishing" }
            session.finishing = true
            session.finishCallback = callback
        }
        stopCapture(session)
        if (session.totalBytes < RATE / 5) {
            fail(session, IllegalArgumentException("Recording is too short"))
            return
        }
        runCatching { synchronized(session) { flush(session) } }
            .onFailure { fail(session, it) }
        timeouts.schedule({
            fail(session, IOException("Speech service response timed out"))
        }, 25, TimeUnit.SECONDS)
    }

    @Synchronized
    fun cancel() {
        val session = active ?: return
        active = null
        synchronized(session) { session.cancelled = true }
        stopCapture(session)
        session.socket?.cancel()
    }

    private fun capture(session: Session, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            while (session.recording.get() && session.totalBytes < MAX_PCM_BYTES) {
                val count = session.audio.read(buffer, 0, buffer.size)
                if (count < 0) throw IOException("Microphone capture failed")
                if (count == 0) continue
                val chunk = buffer.copyOf(count)
                synchronized(session) {
                    if (session.cancelled || session.finishing) return
                    session.totalBytes += count
                    if (session.ready) sendAudio(session, chunk)
                    else {
                        session.pending.addLast(chunk)
                        session.pendingBytes += count
                        if (session.pendingBytes > MAX_PENDING_BYTES) {
                            throw IOException("Speech audio buffer is full")
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            if (session.recording.get()) fail(session, error)
        } finally {
            session.recording.set(false)
        }
    }

    private fun listener(session: Session): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (session.cancelled) { webSocket.cancel(); return }
            session.socket = webSocket
            if (session.provider == VOLCENGINE) {
                if (!webSocket.send(SeedAsrProtocol.fullRequest().toByteString())) {
                    fail(session, IOException("Unable to start Seed ASR"))
                }
            } else ready(session)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (session.provider != VOLCENGINE) return
            try {
                val response = SeedAsrProtocol.parse(bytes.toByteArray())
                if (response.code != 0) throw IOException("Seed ASR rejected request (${response.code})")
                if (session.cancelled) return
                if (!session.ready) ready(session)
                synchronized(session) { if (response.text.isNotBlank()) session.transcript = response.text }
                if (response.last) complete(session, session.transcript)
            } catch (error: Throwable) { fail(session, error) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (session.provider != DEEPGRAM) return
            try {
                val message = JSONObject(text)
                if (message.optString("type") == "Error") {
                    throw IOException("Deepgram rejected request: ${message.optString("code")}")
                }
                if (message.optString("type") != "Results") return
                val transcript = message.optJSONObject("channel")?.optJSONArray("alternatives")
                    ?.optJSONObject(0)?.optString("transcript")?.trim().orEmpty()
                synchronized(session) {
                    if (session.cancelled) return
                    if (message.optBoolean("is_final") && transcript.isNotBlank()) {
                        session.finalSegments.add(transcript)
                    }
                }
                if (message.optBoolean("from_finalize")) {
                    webSocket.send("{\"type\":\"CloseStream\"}")
                    complete(session, session.finalSegments.joinToString(" "))
                }
            } catch (error: Throwable) { fail(session, error) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (session.finishing && !session.cancelled) {
                val text = if (session.provider == VOLCENGINE) session.transcript
                    else session.finalSegments.joinToString(" ")
                complete(session, text)
            } else if (!session.cancelled) fail(session, IOException("Speech service closed early ($code)"))
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            val status = response?.code
            fail(session, IOException(
                if (status == 401 || status == 403) "Speech API Key was rejected (HTTP $status)"
                else "Speech service connection failed${status?.let { " (HTTP $it)" }.orEmpty()}",
                error,
            ))
        }
    }

    private fun ready(session: Session) {
        if (session.cancelled || session.ready) return
        session.ready = true
        try {
            synchronized(session) { flush(session) }
            session.startCallback?.let { callback ->
                session.startCallback = null
                callback(Result.success(Unit))
            }
        } catch (error: Throwable) { fail(session, error) }
    }

    private fun sendAudio(session: Session, pcm: ByteArray) {
        val packet = if (session.provider == VOLCENGINE) {
            SeedAsrProtocol.audioRequest(session.sequence++, pcm).toByteString()
        } else pcm.toByteString()
        if (session.socket?.send(packet) != true) throw IOException("Speech audio transport closed")
    }

    private fun flush(session: Session) {
        if (!session.ready || session.cancelled) return
        while (session.pending.isNotEmpty()) {
            val chunk = session.pending.removeFirst()
            session.pendingBytes -= chunk.size
            sendAudio(session, chunk)
        }
        if (session.finishing && !session.finalSent) {
            session.finalSent = true
            val sent = if (session.provider == VOLCENGINE) {
                session.socket?.send(
                    SeedAsrProtocol.audioRequest(session.sequence++, byteArrayOf(), last = true).toByteString(),
                )
            } else session.socket?.send("{\"type\":\"Finalize\"}")
            if (sent != true) throw IOException("Unable to finish speech stream")
        }
    }

    private fun complete(session: Session, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) fail(session, IOException("No speech recognized"))
        else finishSession(session, Result.success(normalized))
    }

    private fun fail(session: Session, error: Throwable) {
        synchronized(this) { if (active === session) lastFailure = error }
        finishSession(session, Result.failure(error))
    }

    private fun finishSession(session: Session, result: Result<String>) {
        var startCallback: ((Result<Unit>) -> Unit)? = null
        var finishCallback: ((Result<String>) -> Unit)? = null
        synchronized(this) {
            if (active !== session) return
            active = null
            synchronized(session) {
                session.cancelled = true
                startCallback = session.startCallback
                finishCallback = session.finishCallback
                session.startCallback = null
                session.finishCallback = null
            }
        }
        stopCapture(session)
        session.socket?.cancel()
        startCallback?.invoke(Result.failure(result.exceptionOrNull() ?: IOException("Speech service closed early")))
        finishCallback?.invoke(result)
    }

    private fun stopCapture(session: Session) {
        session.recording.set(false)
        runCatching { session.audio.stop() }
        if (Thread.currentThread() !== session.captureThread) session.captureThread?.join(1000)
        runCatching { session.audio.release() }
    }

    override fun close() {
        cancel()
        timeouts.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    companion object {
        const val VOLCENGINE = "volcengine"
        const val DEEPGRAM = "deepgram"
        const val RESOURCE_ID = "volc.seedasr.sauc.duration"
        const val VOLCENGINE_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"
        const val DEEPGRAM_URL = "wss://api.deepgram.com/v1/listen?model=nova-3&encoding=linear16&sample_rate=16000&channels=1&interim_results=true&punctuate=true&smart_format=true"
        private const val RATE = 16000
        private const val MAX_PCM_BYTES = RATE * 2 * 60
        private const val MAX_PENDING_BYTES = 1024 * 1024
        private const val MICROPHONE_REQUEST = 7102
    }
}
