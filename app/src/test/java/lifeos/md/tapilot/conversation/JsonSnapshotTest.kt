package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JsonSnapshotTest {
    @Test fun nestedContainersAreDetachedAndJsonValuesSurvive() {
        val nested = JSONObject().put("escaped", "quote\"\\\n中文").put("null", JSONObject.NULL)
            .put("integer", 9007199254740991L).put("decimal", 0.125).put("boolean", false)
        val array = JSONArray().put(nested).put(JSONArray().put("old")).put(JSONObject.NULL)
        val source = JSONObject().put("array", array)
        val copy = jsonSnapshot(source)
        val before = JSONObject(source.toString())
        nested.put("escaped", "changed")
        array.getJSONArray(1).put(0, "changed")
        array.put("new")
        source.put("extra", true)
        assertEquals(before.toString(), copy.toString())
        assertFalse(copy.has("extra"))
        assertTrue(copy.getJSONArray("array").isNull(2))
        assertEquals(9007199254740991L, copy.getJSONArray("array").getJSONObject(0).getLong("integer"))
        copy.getJSONArray("array").getJSONObject(0).put("integer", 0)
        assertEquals(9007199254740991L, nested.getLong("integer"))
    }
}
