package lifeos.md.tapilot.speech

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpeechPolisherTest {
    private val config = SpeechPolishSettings(enabled = true)
    @Test fun validatesPrivateEndpointAndChoosesPromptByTranscript() {
        listOf("http://example.com", "https://u:p@example.com", "https://example.com?key=x", "https://example.com#x").forEach {
            assertTrue(runCatching { config.copy(baseUrl = it).validated() }.isFailure)
        }
        assertTrue(runCatching { config.copy(model = "bad\nmodel").validated() }.isFailure)
        assertEquals(config.promptZh, config.prompt("嗯第一检查登录第二录音"))
        assertEquals(config.promptEn, config.prompt("First check login, second record"))
        assertEquals("https://example.com/v1", config.copy(baseUrl = "https://example.com/v1/").validated().baseUrl)
    }
    @Test fun sendsTranscriptAsDataAndPreservesFormattedResponse() {
        val formatted = "1. 检查登录\n2. 测试录音\n\n然后更新文档。"
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val request = chain.request()
            assertEquals("https://api.openai.com/v1/chat/completions", request.url.toString())
            assertEquals("Bearer synthetic-key", request.header("Authorization"))
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8())
            assertEquals("gpt-4o-mini", body.getString("model"))
            assertFalse(body.getBoolean("stream"))
            assertEquals("user", body.getJSONArray("messages").getJSONObject(1).getString("role"))
            assertEquals("嗯第一检查登录第二录音", body.getJSONArray("messages").getJSONObject(1).getString("content"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", formatted)))).toString().toResponseBody()).build()
        }.build()
        assertEquals(formatted, SpeechPolisher(config, "synthetic-key", client).polish("嗯第一检查登录第二录音"))
        assertEquals(1, requests)
        assertEquals("raw", SpeechPolisher(config.copy(enabled = false), "synthetic-key", client).polish("raw"))
        assertEquals("raw", SpeechPolisher(config, "", client).polish("raw"))
        assertEquals(1, requests)
    }
    @Test fun failedMalformedOrEmptyResponsesKeepOriginal() {
        listOf(500 to "error", 200 to "{bad", 200 to "{}", 200 to "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}").forEach { (code, response) ->
            val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(response.toResponseBody()).build() }.build()
            assertEquals("不要删除最后一个字", SpeechPolisher(config, "synthetic-key", client).polish("不要删除最后一个字"))
        }
        val timeout = OkHttpClient.Builder().addInterceptor { throw java.net.SocketTimeoutException() }.build()
        assertEquals("original", SpeechPolisher(config, "synthetic-key", timeout).polish("original"))
    }
    @Test fun deepseekDisablesThinkingAndFullEndpointIsNotDuplicated() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            assertEquals("https://api.deepseek.com/chat/completions", request.url.toString())
            assertEquals("disabled", JSONObject(buffer.readUtf8()).getJSONObject("thinking").getString("type"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("ok").body("{}".toResponseBody()).build()
        }.build()
        SpeechPolisher(config.copy(baseUrl = "https://api.deepseek.com/chat/completions"), "synthetic-key", client).polish("hello")
    }
    @Test fun disablesThinkingOnlyForSupportedAliyunHybridQwenModels() {
        listOf(
            Triple("https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", "qwen3.7-plus", true),
            Triple("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.5-flash-2026-02-23", true),
            Triple("https://example.com/v1", "qwen3.7-plus", false),
            Triple("https://dashscope.aliyuncs.com.example.com/v1", "qwen3.7-plus", false),
            Triple("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.7-max-preview", false),
        ).forEach { (url, model, supported) ->
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request(); val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = JSONObject(buffer.readUtf8())
                assertEquals(supported, body.has("enable_thinking"))
                if (supported) assertFalse(body.getBoolean("enable_thinking"))
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{}".toResponseBody()).build()
            }.build()
            SpeechPolisher(config.copy(baseUrl = url, model = model), "synthetic-key", client).polish("hello")
        }
    }

}
