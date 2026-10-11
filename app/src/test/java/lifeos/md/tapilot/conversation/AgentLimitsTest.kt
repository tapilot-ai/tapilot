package lifeos.md.tapilot.conversation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentLimitsTest {
    @Test fun defaultsAndDisabledValuesSurviveStorage() {
        assertEquals(AgentLimits(100, 100), AgentLimits())
        assertEquals(AgentLimits(), AgentLimits.from(JSONObject()))
        val settings = JSONObject().put("agentLimits", AgentLimits(null, 9).json())
        assertEquals(AgentLimits(null, 9), AgentLimits.from(JSONObject(settings.toString())))
        assertEquals(AgentLimits(null, null), AgentLimits.from(JSONObject().put("agentLimits", AgentLimits(null, null).json())))
        assertEquals(AgentLimits(3, 4), AgentLimits.from(JSONObject().put("agentLimits", AgentLimits(3, 4).json())))
    }
    @Test fun malformedNumbersUseDefaults() {
        for (value in listOf(-1, 2.5, "off", 2147483648L)) {
            assertEquals(AgentLimits(), AgentLimits.from(JSONObject().put("agentLimits", JSONObject().put("phoneOperations", value).put("modelTurns", value))))
        }
    }
}
