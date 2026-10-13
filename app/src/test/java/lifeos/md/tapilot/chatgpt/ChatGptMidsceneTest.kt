package lifeos.md.tapilot.chatgpt

import org.junit.Assert.*
import org.junit.Test

class ChatGptMidsceneTest {
    @Test fun phoneOperationsUseSelectedChatGptModelAndResponsesStreaming() {
        val env = ChatGptMidscene.environment("gpt-6.1-sol", "account-id")
        assertEquals("gpt-6.1-sol", env.getString("MIDSCENE_MODEL_NAME"))
        assertEquals("gpt-6", env.getString("MIDSCENE_MODEL_FAMILY"))
        assertEquals("tapilot-native-siwc", env.getString("MIDSCENE_MODEL_API_KEY"))
        assertEquals("account-id", env.getString("MIDSCENE_CHATGPT_ACCOUNT_ID"))
        assertEquals("https://api.openai.com/v1", env.getString("MIDSCENE_MODEL_BASE_URL"))
        assertEquals("openai-responses", env.getString("MIDSCENE_MODEL_PROTOCOL"))
        assertEquals("stream", env.getString("MIDSCENE_MODEL_STREAM_MODE"))
        assertEquals("gpt-5", ChatGptMidscene.environment("gpt-5.6-sol", "account-id").getString("MIDSCENE_MODEL_FAMILY"))
        assertThrows(IllegalStateException::class.java) { ChatGptMidscene.environment("unknown", "account-id") }
        assertThrows(IllegalArgumentException::class.java) { ChatGptMidscene.environment("gpt-6-sol", "") }
    }
}
