package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConversationSnapshotTest {
    @Test fun reusesUnchangedBranchesButDetectsInPlaceNestedChanges() {
        val oldEntry = JSONObject().put("id", "old").put("value", JSONArray().put(JSONObject.NULL).put("kept"))
        val active = JSONObject().put("id", "active").put("value", JSONArray().put("before"))
        val state = JSONObject().put("history", JSONArray().put(oldEntry).put(active))
        val first = ConversationSnapshot.capture(state)
        assertSame(first, ConversationSnapshot.capture(state, first))
        active.getJSONArray("value").put(0, "after")
        val second = ConversationSnapshot.capture(state, first)
        val oldHistory = first["history"] as List<*>
        val newHistory = second["history"] as List<*>
        assertSame(oldHistory[0], newHistory[0])
        assertNotSame(oldHistory[1], newHistory[1])
        assertEquals(listOf("before"), (oldHistory[1] as Map<*, *>)["value"])
        assertEquals(listOf("after"), (newHistory[1] as Map<*, *>)["value"])
        oldEntry.getJSONArray("value").put(1, "changed later")
        assertEquals(listOf(null, "kept"), (newHistory[0] as Map<*, *>)["value"])
    }

    @Test fun preservesNullsRemovalReorderingAndContainerTypeChanges() {
        val state = JSONObject().put("a", JSONObject.NULL).put("list", JSONArray().put("x").put("y"))
        val first = ConversationSnapshot.capture(state)
        state.remove("a"); state.put("b", JSONObject.NULL)
        state.put("list", JSONArray().put("y").put("x").put(JSONObject.NULL))
        val second = ConversationSnapshot.capture(state, first)
        assertFalse(second.containsKey("a")); assertTrue(second.containsKey("b"))
        assertEquals(listOf("y", "x", null), second["list"])
        state.put("list", JSONArray().put("y"))
        val third = ConversationSnapshot.capture(state, second)
        assertEquals(listOf("y"), third["list"])
        assertEquals(listOf("y", "x", null), second["list"])
        state.put("list", JSONObject().put("key", "value"))
        val fourth = ConversationSnapshot.capture(state, third)
        assertEquals(mapOf("key" to "value"), fourth["list"])
        state.remove("list"); state.remove("b")
        assertEquals(emptyMap<String, Any?>(), ConversationSnapshot.capture(state, fourth))
        assertEquals(listOf("x", "y"), first["list"])
    }
}
