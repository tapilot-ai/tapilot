package lifeos.md.tapilot.bridge

import lifeos.md.tapilot.AppText.text as appText
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

/** Streams 16 kHz mono PCM to the two API providers supported by Doubao Say. */
internal class SpeechTranscriber(private val activity: Context) : SpeechRecognition {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val timeouts = Executors.newSingleThreadScheduledExecutor()
    private val probes = ConcurrentHashMap.newKeySet<WebSocket>()
    private var active: Session? = null
    private var preparedAudio: AudioRecord? = null
    private var lastFailure: Throwable? = null
    private data class Retained(val provider: String, val language: String, val pcm: ByteArray, val attempts: Int)
    private var retained: Retained? = null
    override val canRetry: Boolean @Synchronized get() = retained?.let { it.attempts < 2 && it.pcm.isNotEmpty() } == true

    private class Session(
        val provider: String,
        val audio: AudioRecord?,
        val language: String,
        val attempts: Int = 0,
        val onLevel: (Float) -> Unit = {},
        val onError: (Throwable) -> Unit = {},
        val onTranscript: (String) -> Unit = {},
        var startCallback: ((Result<Unit>) -> Unit)?,
    ) {
        val recording = AtomicBoolean(true)
        val captureStopped = AtomicBoolean(false)
        val pcm = SpeechAudio()
        val pending = ArrayDeque<ByteArray>()
        var pendingBytes = 0
        var totalBytes = 0
        var socket: WebSocket? = null
        var captureThread: Thread? = null
        var ready = false
        val boundary = SpeechCaptureBoundary(audio == null)
        val finishing get() = boundary.finishing
        val finalSent get() = boundary.finalSent
        var cancelled = false
        var sequence = 2
        var transcript = ""
        val finalSegments = mutableListOf<String>()
        var finishCallback: ((Result<String>) -> Unit)? = null
    }

    @Synchronized
    override fun start(provider: String, apiKey: String, language: String,
        onLevel: (Float) -> Unit, onError: (Throwable) -> Unit, onTranscript: (String) -> Unit, callback: (Result<Unit>) -> Unit) {
        require(provider == VOLCENGINE || provider == DEEPGRAM) { "Unsupported speech provider" }
        require(language == "zh-CN" || language == "en-US") { "Unsupported speech language" }
        require(apiKey.isNotBlank() && apiKey.length <= 4096 &&
            apiKey.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Invalid speech API Key" }
        check(active == null) { "Recording is already in progress" }
        lastFailure = null
        retained = null
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            (activity as? Activity)?.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
            error("Microphone permission required. Open Tapilot and allow microphone access.")
        }
        val bufferSize = 3200
        val audio = preparedAudio?.also { preparedAudio = null } ?: createMicrophone()
        val session = Session(provider, audio, language, onLevel = onLevel, onError = onError, onTranscript = onTranscript, startCallback = callback)
        try {
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) audio.startRecording()
            active = session
            if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                android.util.Log.d("TapilotVoice", "Microphone capture started")
            session.captureThread = Thread(
                { capture(session, bufferSize) }, "tapilot-speech-capture",
            ).apply { start() }
            connect(session, apiKey)
            // A wall-clock backstop also releases the socket if capture stalls or
            // its PCM limit is reached while the UI timer is unavailable.
            timeouts.schedule({ fail(session, IOException("Recording reached the 60-second limit")) },
                60, TimeUnit.SECONDS)
            session.startCallback = null
            callback(Result.success(Unit))

        } catch (error: Throwable) {
            if (active === session) fail(session, error)
            else {
                runCatching { audio.release() }
                callback(Result.failure(error))
            }
        }
    }

    private fun createMicrophone(): AudioRecord {
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Microphone audio format is unavailable" }
        // AudioRecord retains the opening PCM until the confirmed hold starts the reader.
        return AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 32768)).also {
            if (it.state != AudioRecord.STATE_INITIALIZED) { it.release(); error("Microphone is unavailable") }
        }
    }

    @Synchronized override fun prepare() {
        if (active != null || preparedAudio != null ||
            activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            val audio = createMicrophone()
            try { audio.startRecording(); preparedAudio = audio }
            catch (failure: Throwable) { audio.release(); throw failure }
            timeouts.schedule({ synchronized(this) { if (preparedAudio === audio) cancelPreparation() } }, 700, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized override fun cancelPreparation() {
        preparedAudio?.let { audio -> runCatching { audio.stop() }; audio.release() }
        preparedAudio = null
    }

    /** Validate API access without opening the microphone or sending user audio. */
    fun checkConnection(provider: String, apiKey: String, callback: (Result<Unit>) -> Unit) {
        require(provider == VOLCENGINE || provider == DEEPGRAM)
        require(apiKey.isNotBlank() && apiKey.length <= 4096 && apiKey.none { it == '\r' || it == '\n' || it == '\u0000' })
        val done = AtomicBoolean(false)
        val request = Request.Builder().url(if (provider == VOLCENGINE) VOLCENGINE_URL else "$DEEPGRAM_URL&language=zh-CN")
            .apply {
                if (provider == VOLCENGINE) {
                    header("X-Api-Key", apiKey)
                    header("X-Api-Resource-Id", RESOURCE_ID)
                    header("X-Api-Request-Id", UUID.randomUUID().toString())
                } else header("Authorization", "Token $apiKey")
            }.build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            fun finish(socket: WebSocket, result: Result<Unit>) {
                if (done.compareAndSet(false, true)) { probes.remove(socket); socket.cancel(); callback(result) }
            }
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (done.get()) { webSocket.cancel(); return }
                if (provider == VOLCENGINE) webSocket.send(SeedAsrProtocol.fullRequest().toByteString())
                else finish(webSocket, Result.success(Unit))
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (provider != VOLCENGINE) return
                finish(webSocket, runCatching {
                    check(SeedAsrProtocol.parse(bytes.toByteArray()).code == 0) { appText("语音服务拒绝识别请求，请检查资源开通状态", "Speech request rejected. Check that the recognition service is enabled") }
                })
            }
            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                finish(webSocket, Result.failure(IOException(SpeechServiceFailure.message(provider, response?.code,
                    runCatching { response?.peekBody(4096)?.string().orEmpty() }.getOrDefault(""), response?.header("X-Tt-Logid")))))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(webSocket, Result.failure(IOException(appText("语音服务提前关闭，请稍后重试", "Speech service closed early. Try again"))))
            }
        })
        probes.add(socket)
        if (done.get()) { probes.remove(socket); socket.cancel() }
        timeouts.schedule({ if (done.compareAndSet(false, true)) {
            probes.remove(socket)
            socket.cancel(); callback(Result.failure(IOException(appText("语音服务连接超时，请稍后重试", "Speech connection timed out. Try again"))))
        } }, 12, TimeUnit.SECONDS)
    }

    private fun connect(session: Session, apiKey: String) {
        val request = Request.Builder()
            .url(if (session.provider == VOLCENGINE) VOLCENGINE_URL else "$DEEPGRAM_URL&language=${session.language}")
            .apply {
                if (session.provider == VOLCENGINE) {
                    header("X-Api-Key", apiKey)
                    header("X-Api-Resource-Id", RESOURCE_ID)
                    header("X-Api-Request-Id", UUID.randomUUID().toString())
                } else header("Authorization", "Token $apiKey")
            }.build()
        session.socket = client.newWebSocket(request, listener(session))
        timeouts.schedule({ if (!session.ready) fail(session, IOException("Speech service connection timed out")) },
            12, TimeUnit.SECONDS)
    }

    @Synchronized
    override fun retry(apiKey: String, callback: (Result<String>) -> Unit) {
        check(active == null) { "Transcription is already in progress" }
        val saved = retained?.takeIf { it.attempts < 2 } ?: error("No recording available to retry")
        require(apiKey.isNotBlank() && apiKey.length <= 4096 &&
            apiKey.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Invalid speech API Key" }
        val session = Session(saved.provider, null, saved.language, attempts = saved.attempts + 1, startCallback = null)
        session.pcm.append(saved.pcm)
        session.totalBytes = saved.pcm.size
        session.boundary.requestFinish()
        session.finishCallback = callback
        for (offset in saved.pcm.indices step 3200) {
            session.pending.addLast(saved.pcm.copyOfRange(offset, minOf(offset + 3200, saved.pcm.size)))
        }
        session.pendingBytes = saved.pcm.size
        active = session
        runCatching { connect(session, apiKey) }.onFailure { fail(session, it) }
        timeouts.schedule({ fail(session, IOException("Speech service response timed out")) }, 35, TimeUnit.SECONDS)
    }

    @Synchronized
    override fun finish(callback: (Result<String>) -> Unit) {
        val session = active ?: throw (lastFailure ?: IllegalStateException("No recording is in progress"))
        synchronized(session) {
            session.boundary.requestFinish()
            session.finishCallback = callback
        }
        // Keep one microphone-buffer window after release, while the host already shows transcription.
        timeouts.schedule({
            if (session.cancelled) return@schedule
            runCatching {
                stopCapture(session)
                check(session.captureThread?.isAlive != true) { "Microphone capture did not stop" }
                synchronized(session) {
                    session.boundary.captureDrained()
                    require(session.totalBytes >= RATE / 5) { "Recording is too short" }
                    // Preserve the existing debug-only recovery test hook.
                    if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                        (activity as? Activity)?.intent?.getBooleanExtra("tapilot.debug.failSpeechOnce", false) == true) {
                        (activity as? Activity)?.intent?.removeExtra("tapilot.debug.failSpeechOnce")
                        throw IOException("Injected speech response failure for E2E")
                    }
                    flush(session)
                }
            }.onFailure { fail(session, it) }
        }, 320, TimeUnit.MILLISECONDS)
        timeouts.schedule({ fail(session, IOException("Speech service response timed out")) }, 25, TimeUnit.SECONDS)
    }

    @Synchronized
    override fun cancel() {
        cancelPreparation()
        retained = null
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
                val count = session.audio!!.read(buffer, 0, buffer.size)
                if (count < 0) throw IOException("Microphone capture failed")
                if (count == 0) continue
                val chunk = buffer.copyOf(minOf(count, MAX_PCM_BYTES - session.totalBytes))
                synchronized(session) {
                    if (session.cancelled || !session.boundary.acceptsAudio()) return
                    session.totalBytes += chunk.size
                    session.pcm.append(chunk)
                    session.onLevel(SpeechAudio.level(chunk))
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
                session.onTranscript(session.transcript)
                if (response.last) complete(session, session.transcript)
            } catch (error: Throwable) { fail(session, error) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (session.provider != DEEPGRAM) return
            try {
                val transcript = DeepgramProtocol.finalTranscript(text) ?: return
                synchronized(session) {
                    if (session.cancelled) return
                    session.finalSegments.add(transcript)
                    session.onTranscript(session.finalSegments.joinToString(" "))
                }
            } catch (error: Throwable) { fail(session, error) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (session.finishing && session.finalSent && !session.cancelled && code == 1000 && session.provider == DEEPGRAM) {
                val text = if (session.provider == VOLCENGINE) session.transcript
                    else session.finalSegments.joinToString(" ")
                complete(session, text)
            } else if (!session.cancelled) fail(session, IOException("Speech service closed early ($code)"))
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            val status = response?.code
            fail(session, IOException(
                SpeechServiceFailure.message(session.provider, status,
                    runCatching { response?.peekBody(4096)?.string().orEmpty() }.getOrDefault(""),
                    response?.header("X-Tt-Logid")),
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
        if (session.boundary.claimFinal()) {
            val sent = if (session.provider == VOLCENGINE) {
                session.socket?.send(
                    SeedAsrProtocol.audioRequest(session.sequence++, byteArrayOf(), last = true).toByteString(),
                )
            } else session.socket?.send(DeepgramProtocol.CLOSE_STREAM)
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
            val pcm = synchronized(session) { session.pcm.snapshot() }
            retained = if (result.isFailure && pcm.size >= RATE / 5) Retained(session.provider, session.language, pcm, session.attempts) else null
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
        if (finishCallback == null && startCallback == null) result.exceptionOrNull()?.let(session.onError)
    }

    private fun stopCapture(session: Session) {
        if (!session.captureStopped.compareAndSet(false, true)) return
        session.recording.set(false)
        runCatching { session.audio?.stop() }
        if (Thread.currentThread() !== session.captureThread) session.captureThread?.join(1000)
        runCatching { session.audio?.release() }
    }

    override fun close() {
        cancel()
        probes.forEach { it.cancel() }
        probes.clear()
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
