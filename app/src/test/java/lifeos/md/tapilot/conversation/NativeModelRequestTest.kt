package lifeos.md.tapilot.conversation

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeModelRequestTest {
    @Test fun qwenRequestDisablesDefaultReasoningWithoutChangingStoredConfiguration() {
        val profile = ModelProfiles.defaults("qwen")
        val request = NativeModelRequest.params(profile, "test-key", JSONArray())
        val body = JSONObject(String(Base64.getDecoder().decode(request.getString("bodyBase64"))))
        assertFalse(body.getBoolean("enable_thinking"))
        assertFalse(profile.has("enable_thinking"))
        assertEquals("Bearer test-key", request.getJSONObject("headers").getString("Authorization"))
        assertEquals("operate_phone", body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
    }
    @Test fun otherProvidersDoNotReceiveQwenExtension() {
        val request = NativeModelRequest.params(ModelProfiles.defaults("openai"), "test-key", JSONArray())
        val body = JSONObject(String(Base64.getDecoder().decode(request.getString("bodyBase64"))))
        assertFalse(body.has("enable_thinking"))
    }
    @Test fun errorResponseDoesNotExposeItsBody() {
        val response = JSONObject().put("status", 401).put("bodyBase64", Base64.getEncoder().encodeToString("private credential".toByteArray()))
        val error = assertThrows(IllegalStateException::class.java) { NativeModelRequest.decode(response) }
        assertTrue(error.message!!.contains("401"))
        assertFalse(error.message!!.contains("private"))
    }
    @Test fun arrearageIsDistinctFromInvalidConfiguration() {
        val errorBody = JSONObject().put("error", JSONObject().put("code", "Arrearage").put("message", "private service message"))
        val response = JSONObject().put("status", 400).put("bodyBase64", Base64.getEncoder().encodeToString(errorBody.toString().toByteArray()))
        val error = assertThrows(IllegalStateException::class.java) { NativeModelRequest.decode(response) }
        assertTrue(error.message!!.contains("欠费"))
        assertFalse(error.message!!.contains("private"))
    }
}
