package lifeos.md.tapilot.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalLanguageTaskWorkflowTest {
    @Test
    fun appendsAnAiResponseStepAfterTheRequestedAction() {
        val task = "总结微信最近聊天"
        val steps = JSONObject(NaturalLanguageTaskWorkflow.asYaml(task))
            .getJSONArray("cases")
            .getJSONObject(0)
            .getJSONArray("steps")

        assertEquals(task, steps.getJSONObject(0).getJSONObject("aiAct").getString("prompt"))
        val responsePrompt = steps.getJSONObject(1).getJSONObject("aiAsk").getString("prompt")
        assertTrue(responsePrompt.contains(task))
        assertTrue(responsePrompt.contains("Reply in the same language"))
    }
}
