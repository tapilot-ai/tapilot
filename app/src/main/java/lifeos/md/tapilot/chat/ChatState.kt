package lifeos.md.tapilot.chat

import org.json.JSONObject

data class ChatMessage(val id: String, val role: String, val text: String, val status: String, val reportId: String)
data class ChatConversation(val id: String, val title: String)
data class ChatAttachment(val name: String, val kind: String, val preview: String)

data class ChatState(
    val ready: Boolean = false,
    val language: String = "zh",
    val dark: Boolean = false,
    val webSurfaceOpen: Boolean = false,
    val advancedMode: Boolean = false,
    val activeConversationId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val draft: String = "",
    val draftEpoch: Int = 0,
    val running: Boolean = false,
    val configured: Boolean = false,
    val modelName: String = "",
    val speechProvider: String = "volcengine",
    val statusTitle: String = "",
    val needsAccessibility: Boolean = false,
    val attachment: ChatAttachment? = null,
    val confirmation: String? = null,
) {
    val chinese: Boolean get() = language == "zh"

    companion object {
        fun parse(payload: String): ChatState {
            val data = JSONObject(payload)
            require(data.getInt("protocolVersion") == 1) { "Unsupported chat protocol" }
            val messages = data.getJSONArray("messages")
            val conversations = data.getJSONArray("conversations")
            val attachment = data.optJSONObject("attachment")
            return ChatState(
                ready = data.optBoolean("ready"), language = data.optString("language", "zh"),
                dark = data.optString("theme") == "dark", webSurfaceOpen = data.optBoolean("webSurfaceOpen"),
                advancedMode = data.optBoolean("advancedMode"),
                activeConversationId = data.optString("activeConversationId"),
                messages = List(messages.length()) { index -> messages.getJSONObject(index).let {
                    ChatMessage(it.getString("id"), it.getString("role"), it.optString("text"),
                        it.optString("status"), it.optString("reportId"))
                } },
                conversations = List(conversations.length()) { index -> conversations.getJSONObject(index).let {
                    ChatConversation(it.getString("id"), it.getString("title"))
                } },
                draft = data.optString("draft"), draftEpoch = data.optInt("draftEpoch"),
                running = data.optBoolean("running"), configured = data.optBoolean("configured"),
                modelName = data.optString("modelName"), speechProvider = data.optString("speechProvider", "volcengine"),
                statusTitle = data.optString("statusTitle"), needsAccessibility = data.optBoolean("needsAccessibility"),
                attachment = attachment?.let { ChatAttachment(it.optString("name"), it.optString("kind"), it.optString("preview")) },
                confirmation = data.optString("confirmation").takeIf { !data.isNull("confirmation") && it.isNotBlank() },
            )
        }
    }
}
