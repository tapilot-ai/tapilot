package lifeos.md.tapilot.chatgpt

import org.junit.Assert.*
import org.junit.Test

class ChatGptMidsceneTest {
    @Test fun phoneOperationsUseSelectedChatGptModelAndResponsesStreaming() {
        val env = ChatGptMidscene.environment("gpt-6.1-sol", "test-token")
        assertEquals("gpt-6.1-sol", env.getString("MIDSCENE_MODEL_NAME"))
        assertEquals("gpt-6", env.getString("MIDSCENE_MODEL_FAMILY"))
        assertEquals("test-token", env.getString("MIDSCENE_MODEL_API_KEY"))
        assertEquals("https://api.openai.com/v1", env.getString("MIDSCENE_MODEL_BASE_URL"))
        assertEquals("openai-responses", env.getString("MIDSCENE_MODEL_PROTOCOL"))
        assertEquals("stream", env.getString("MIDSCENE_MODEL_STREAM_MODE"))
        assertEquals("gpt-5", ChatGptMidscene.environment("gpt-5.6-sol", "test-token").getString("MIDSCENE_MODEL_FAMILY"))
        assertThrows(IllegalStateException::class.java) { ChatGptMidscene.environment("unknown", "test-token") }
        assertThrows(IllegalArgumentException::class.java) { ChatGptMidscene.environment("gpt-6-sol", "") }
    }
}
