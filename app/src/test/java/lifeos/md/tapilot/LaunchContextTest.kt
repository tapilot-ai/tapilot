package lifeos.md.tapilot

import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class LaunchContextTest {
    @Before @After fun clear() { LaunchContext.consume(); LaunchContext.consumePanel() }

    @Test fun mainActivityResumeCannotConsumeThePanelScreenshot() {
        LaunchContext.putPanel(JSONObject().put("dataUrl", "private screen"))
        assertNull(LaunchContext.consume())
        assertEquals("private screen", LaunchContext.consumePanel()!!.getString("dataUrl"))
        assertNull(LaunchContext.consumePanel())
    }

    @Test fun panelLaunchAndSubmittedMessageRemainIndependent() {
        LaunchContext.put(JSONObject().put("text", "submitted message"))
        LaunchContext.putPanel(JSONObject().put("dataUrl", "new screen"))
        assertEquals("new screen", LaunchContext.consumePanel()!!.getString("dataUrl"))
        assertEquals("submitted message", LaunchContext.consume()!!.getString("text"))
        assertNull(LaunchContext.consume())
    }
}
