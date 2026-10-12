package lifeos.md.tapilot.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Base64
import lifeos.md.tapilot.bridge.ModelCredentialStore
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

/** Same API providers and default voices as Studio's ai-core voice player. No system TTS fallback. */
class ApiSpeechPlayback(context: Context, private val provider: () -> String) : SpeechPlayback {
    private val context = context.applicationContext
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    @Volatile private var call: Call? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var completion: ((Result<Unit>) -> Unit)? = null
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                val interrupted = completion
                stop()
                interrupted?.invoke(Result.failure(IOException("播报被其他音频打断，请点击重试或继续说话")))
            }
        }.build()

    override fun speak(text: String, onStarted: () -> Unit, callback: (Result<Unit>) -> Unit) {
        stop()
        val token = generation.get()
        val delivered = AtomicBoolean()
        val complete: (Result<Unit>) -> Unit = { result -> if (delivered.compareAndSet(false, true)) callback(result) }
        completion = complete
        worker.execute {
            if (generation.get() != token) return@execute
            val result = runCatching {
                val service = provider()
                val config = VoiceOutputSettings(context)
                val keys = ModelCredentialStore(context).getAll()
                val key = keys["speech-output-$service"].orEmpty().ifBlank { keys["speech-$service"].orEmpty() }
                require(key.isNotBlank()) { "请在设置中配置语音 API Key" }
                require(key.length <= 4096 && key.none { it == '\n' || it == '\r' || it == '\u0000' }) { "Invalid speech API Key" }
                check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "暂时无法取得音频播放权限，请重试" }
                var notified = false
                for (part in spokenText(text).chunked(1000)) {
                    if (generation.get() != token) return@runCatching
                    stream(service, key, config.speaker(service), part, token) {
                        if (!notified) {
                            notified = true
                            if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                                android.util.Log.d("TapilotVoice", "Playback started chars=${spokenText(text).length}")
                            onStarted()
                        }
                    }
                }
                check(notified || generation.get() != token) { "语音服务没有返回音频" }
            }
            manager.abandonAudioFocusRequest(focus)
            if (generation.get() == token) { if (completion === complete) completion = null; complete(result) }
        }
    }

    private fun stream(provider: String, key: String, speaker: String, text: String, token: Long, onStarted: () -> Unit) {
        val volc = provider == "volcengine"
        require(volc || provider == "deepgram") { "Unsupported speech output provider" }
        val rate = if (volc) 24000 else 48000
        val body = if (volc) JSONObject().put("req_params", JSONObject().put("text", text).put("speaker", speaker)
            .put("audio_params", JSONObject().put("format", "pcm").put("sample_rate", rate))) else JSONObject().put("text", text)
        val request = Request.Builder().url(if (volc) "https://openspeech.bytedance.com/api/v3/tts/unidirectional"
            else "https://api.deepgram.com/v1/speak?model=${java.net.URLEncoder.encode(speaker, "UTF-8")}&encoding=linear16&sample_rate=$rate&container=none")
            .post(body.toString().toRequestBody("application/json".toMediaType())).apply {
                if (volc) {
                    header("X-Api-Key", key)
                    header("X-Api-Resource-Id", if (speaker.startsWith("S_")) "seed-icl-2.0" else "seed-tts-2.0")
                    header("X-Api-Request-Id", UUID.randomUUID().toString())
                } else header("Authorization", "Token $key")
            }.build()
        val currentCall = client.newCall(request)
        call = currentCall
        if (generation.get() != token) { currentCall.cancel(); return }
        var audio: AudioTrack? = null
        var bytes = 0L
        val pcm = PcmStream()
        fun write(chunk: ByteArray) {
            val data = pcm.append(chunk)
            if (data.isEmpty() || generation.get() != token) return
            check(data.size % 2 == 0) { "Invalid PCM audio response" }
            bytes += data.size
            check(bytes <= 30 * 1024 * 1024) { "Speech audio response is too large" }
            if (audio == null) {
                audio = AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(rate / 2, AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                track = audio
                if (generation.get() != token) return
                audio!!.play(); onStarted()
            }
            var offset = 0
            while (offset < data.size && generation.get() == token) {
                val count = audio!!.write(data, offset, minOf(8192, data.size - offset), AudioTrack.WRITE_BLOCKING)
                check(count > 0) { "Unable to play speech audio" }
                offset += count
            }
        }
        try {
            currentCall.execute().use { response ->
                if (!response.isSuccessful) {
                    // Do not expose request headers or credential-bearing URLs in UI errors.
                    throw IOException(if (response.code == 403) "语音播报权限未开通（403），请检查 TTS 服务和 API Key" else "语音播报请求失败（HTTP ${response.code}）")
                }
                val source = response.body?.source() ?: throw IOException("Empty speech response")
                if (volc) {
                    while (!source.exhausted() && generation.get() == token) {
                        val newline = source.indexOf('\n'.code.toByte(), 0, 2L * 1024 * 1024)
                        val line = if (newline >= 0) source.readUtf8LineStrict(2L * 1024 * 1024).trim()
                            else { check(source.buffer.size <= 2L * 1024 * 1024) { "Speech response line is too large" }; source.readUtf8().trim() }
                        if (line.isEmpty()) continue
                        val event = JSONObject(line)
                        val code = event.optInt("code", -1)
                        if (code != 0 && code != 20000000) throw IOException("火山引擎播报失败（$code）：${event.optString("message").take(180)}")
                        event.optString("data").takeIf { it.isNotBlank() }?.let { write(Base64.decode(it, Base64.DEFAULT)) }
                    }
                } else {
                    val buffer = ByteArray(8192)
                    while (generation.get() == token) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        write(buffer.copyOf(count))
                    }

                }
            }
            if (generation.get() == token) pcm.finish()
            val deadline = SystemClock.elapsedRealtime() + bytes * 1000 / (rate * 2) + 5000
            while (generation.get() == token && audio != null && (audio!!.playbackHeadPosition.toLong() and 0xffffffffL) < bytes / 2) {
                check(SystemClock.elapsedRealtime() < deadline) { "Speech playback timed out" }
                Thread.sleep(20)
            }
            if (generation.get() == token && bytes > 0 && (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                android.util.Log.d("TapilotVoice", "Playback complete provider=$provider bytes=$bytes frames=${audio?.playbackHeadPosition} rate=$rate")
            }
        } finally {
            if (track === audio) track = null
            if (call === currentCall) call = null
            runCatching { audio?.pause(); audio?.flush(); audio?.release() }
        }
    }

    override fun stop() {
        generation.incrementAndGet(); completion = null; call?.cancel()
        manager.abandonAudioFocusRequest(focus)
        runCatching { track?.pause(); track?.flush() }
    }
    override fun close() {
        stop(); worker.shutdownNow(); manager.abandonAudioFocusRequest(focus)
        client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
    }
}

internal fun spokenText(text: String): String = text
    .replace(Regex("```[\\s\\S]*?```"), "代码已显示在对话中。")
    .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("(?m)^\\s*[#>]+\\s*"), "")
    .replace(Regex("[*_`]"), "").trim()
