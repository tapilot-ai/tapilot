package lifeos.md.tapilot.conversation

import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConversationStoreTest {
    @Test fun importsProfilesHistoryAndDraftWithoutSecrets() {
        val values = JSONObject().put("midscene.modelPreset", "custom").put("midscene.baseUrl", "https://example.test/v1")
            .put("midscene.modelName", "legacy-model").put("midscene.modelFamily", "legacy-family").put("midscene.apiKey", "secret-must-not-persist")
            .put("midscene.modelPresetConfig.qwen.apiKey", "another-secret").put("midscene.task", "未发送草稿")
            .put("tapilot.activeConversation", "old-chat").put("tapilot.speech.provider", "deepgram")
            .put("midscene.reportHistory", JSONArray().put(JSONObject().put("id", "entry").put("conversationId", "old-chat")
                .put("source", "原消息").put("inputText", "用户文字").put("status", "running").put("attachment", JSONObject().put("id", "attachment"))
                .put("reports", JSONArray().put(JSONObject().put("reportId", "report")))).toString())
        val migrated = ConversationStore.importLegacy(values)
        assertFalse(migrated.toString().contains("secret"))
        assertEquals("legacy-model", migrated.getJSONObject("profiles").getJSONObject("custom").getString("modelName"))
        assertEquals("old-chat", migrated.getString("active"))
        assertEquals("未发送草稿", migrated.getString("draft"))
        val entry = migrated.getJSONArray("history").getJSONObject(0)
        assertEquals("stopped", entry.getString("status"))
        assertEquals("attachment", entry.getJSONObject("attachment").getString("id"))
        assertEquals("report", entry.getJSONArray("reports").getJSONObject(0).getString("reportId"))
    }
    @Test fun atomicRoundTripAndCorruptionDoesNotSilentlyReset() {
        val root = Files.createTempDirectory("tapilot-native-store").toFile()
        try {
            val file = root.resolve("state.json")
            val store = ConversationStore(file)
            store.save(ConversationStore.empty().put("draft", "保留"))
            assertEquals("保留", store.read().getString("draft"))
            assertFalse(root.resolve("state.json.tmp").exists())
            file.writeText("broken")
            assertThrows(org.json.JSONException::class.java) { store.read() }
            assertEquals("broken", file.readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun endpointsAreHttpsAndChatCompatible() {
        assertEquals("https://example.test/v1/chat/completions", ModelProfiles.endpoint("https://example.test/v1/"))
        assertEquals("https://example.test/chat/completions", ModelProfiles.endpoint("https://example.test/chat"))
        assertThrows(IllegalArgumentException::class.java) { ModelProfiles.endpoint("http://example.test") }
        assertThrows(IllegalStateException::class.java) { ModelProfiles.endpoint("https://example.test/v1/responses") }
    }
}
