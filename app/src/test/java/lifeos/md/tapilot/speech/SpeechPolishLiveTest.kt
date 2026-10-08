package lifeos.md.tapilot.speech

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in integration check. Ordinary tests never call a paid service. */
class SpeechPolishLiveTest {
    @Test fun polishesSyntheticTranscriptThroughRealService() {
        assumeTrue(System.getenv("TAPILOT_POLISH_LIVE") == "1")
        val key = System.getenv("TAPILOT_POLISH_TEST_KEY").orEmpty()
        require(key.isNotBlank()) { "Live test credential is missing" }
        val config = SpeechPolishSettings(enabled = true,
            baseUrl = requireNotNull(System.getenv("TAPILOT_POLISH_TEST_URL")),
            model = requireNotNull(System.getenv("TAPILOT_POLISH_TEST_MODEL"))).validated()
        var status = 0
        var lastStage = "start"
        var errorClass = ""
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .eventListener(object : EventListener() {
                override fun dnsStart(call: Call, domainName: String) { lastStage = "dns" }
                override fun connectStart(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) { lastStage = "connect" }
                override fun secureConnectStart(call: Call) { lastStage = "tls" }
                override fun requestHeadersStart(call: Call) { lastStage = "request" }
                override fun responseHeadersStart(call: Call) { lastStage = "response" }
                override fun callFailed(call: Call, ioe: IOException) { errorClass = ioe.javaClass.simpleName }
            })
            .addNetworkInterceptor { chain -> chain.proceed(chain.request()).also { status = it.code } }.build()
        val polisher = SpeechPolisher(config, key, client)
        val transcript = "嗯，第一检查登录，第二测试录音，第三更新文档。呃，明天不要发布。"
        val started = System.nanoTime()
        val result = try { polisher.polish(transcript) } finally { polisher.cancel() }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        System.getenv("TAPILOT_POLISH_TEST_REPORT")?.let { path ->
            File(path).writeText(JSONObject().put("syntheticTranscript", transcript).put("result", result)
                .put("httpStatus", status).put("lastStage", lastStage).put("errorClass", errorClass).put("elapsedMs", elapsedMs).put("endpoint", config.baseUrl)
                .put("model", config.model).put("customizedPrompt", false).toString(2))
        }
        assertEquals("Real service should return HTTP 200", 200, status)
        assertNotEquals("Service must return an actual polished response", transcript, result)
        listOf("登录", "录音", "文档", "明天", "不要发布").forEach {
            assertTrue("Polish must preserve the synthetic facts and negation", result.contains(it))
        }
        assertTrue("Polish must retain enumerated items", Regex("(?m)^1\\.\\s").containsMatchIn(result))
        assertTrue("Polish must retain enumerated items", Regex("(?m)^3\\.\\s").containsMatchIn(result))
    }
}
