package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PhoneOperationRouteTest {
    private val apps = JSONArray().put(JSONObject().put("label", "豆包").put("packageName", "example.doubao").put("component", "example.doubao/.Main"))
        .put(JSONObject().put("label", "Calendar").put("packageName", "example.calendar").put("component", "example.calendar/.Main"))
        .put(JSONObject().put("label", "Calendar").put("packageName", "example.work").put("component", "example.work/.Main"))
    @Test fun onlyUnambiguousInstalledAppsCanLaunchDirectly() {
        assertEquals("example.doubao/.Main", PhoneOperationRoute.resolveApp(apps, "豆包"))
        assertEquals("example.doubao/.Main", PhoneOperationRoute.resolveApp(apps, "example.doubao"))
        assertEquals("", PhoneOperationRoute.resolveApp(apps, ""))
        for (target in listOf("Calendar", "missing", "https://example.com", "fake.package/.Main"))
            assertThrows(IllegalArgumentException::class.java) { PhoneOperationRoute.resolveApp(apps, target) }
        assertEquals(2, PhoneOperationRoute.findApps(apps, "calendar").length())
        assertEquals(3, PhoneOperationRoute.findApps(apps, "").length())
    }
    @Test fun observeCannotGenerateInteractionStepsAndLaunchPrecedesObservation() {
        fun steps(mode: String, app: String = "") = JSONObject(NativeConversationAgent.verifiedTask("read", "visible", mode, app))
            .getJSONArray("cases").getJSONObject(0).getJSONArray("steps")
        val read = steps("observe")
        assertEquals(1, read.length()); assertTrue(read.getJSONObject(0).has("verifyPhone"))
        val launch = steps("observe", "example.doubao/.Main")
        assertEquals(2, launch.length())
        assertEquals("example.doubao/.Main", launch.getJSONObject(0).getJSONObject("launch").getString("uri"))
        assertTrue(launch.getJSONObject(1).has("verifyPhone"))
        val act = steps("act", "example.doubao/.Main")
        assertEquals(3, act.length()); assertTrue(act.getJSONObject(1).has("aiAct"))
        assertThrows(IllegalArgumentException::class.java) { steps("invalid") }
    }
}
