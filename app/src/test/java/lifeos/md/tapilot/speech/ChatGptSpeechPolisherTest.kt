package lifeos.md.tapilot.speech

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatGptSpeechPolisherTest {
    private val settings = SpeechPolishSettings(enabled = true)
    private val completed = """data: {"type":"response.output_item.done","output_index":0,"item":{"type":"message","content":[{"type":"output_text","text":"明天下午三点开会。"}]}}

data: {"type":"response.completed","response":{"status":"completed"}}

"""
    @Test fun usesSelectedAccountAndModelAndRefreshesOnlyOnceOnUnauthorized() {
        val refreshes = mutableListOf<Boolean>()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("https://api.openai.com/v1/responses", request.url.toString())
            assertEquals("Bearer token-${requests}", request.header("Authorization"))
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8())
            assertEquals("gpt-6.1-sol", body.getString("model"))
            assertTrue(body.getBoolean("stream")); assertFalse(body.getBoolean("store"))
            val input = body.getJSONArray("input")
            assertEquals("developer", input.getJSONObject(0).getString("role"))
            assertEquals("嗯明天下午三点开会", input.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text"))
            requests++
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (requests == 1) 401 else 200).message("test")
                .body((if (requests == 1) "unauthorized" else completed).toResponseBody()).build()
        }.build()
        val polisher = ChatGptSpeechPolisher(settings, "gpt-6.1-sol", { force ->
            refreshes += force; "token-${refreshes.size - 1}"
        }, client)
        assertEquals("明天下午三点开会。", polisher.polish("嗯明天下午三点开会"))
        assertEquals(listOf(false, true), refreshes)
        assertEquals(2, requests)
    }
    @Test fun failuresIncompleteAndOversizedResponsesKeepOriginal() {
        listOf(500 to "error", 401 to "unauthorized", 200 to "data: {}\n\n", 200 to "x".repeat(1024 * 1024 + 1)).forEach { (code, data) ->
            var requests = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                requests++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("test").body(data.toResponseBody()).build()
            }.build()
            assertEquals("原始转写", ChatGptSpeechPolisher(settings, "gpt-6.1-sol", { "token" }, client).polish("原始转写"))
            assertEquals(if (code == 401) 2 else 1, requests)
        }
    }
    @Test fun disabledCancelledOrMissingModelNeverFetchCredentials() {
        val token: (Boolean) -> String = { error("Must not request credentials") }
        assertEquals("raw", ChatGptSpeechPolisher(settings.copy(enabled = false), "model", token).polish("raw"))
        assertEquals("raw", ChatGptSpeechPolisher(settings, "", token).polish("raw"))
        val cancelled = ChatGptSpeechPolisher(settings, "model", token)
        cancelled.cancel()
        assertEquals("raw", cancelled.polish("raw"))
    }
}
