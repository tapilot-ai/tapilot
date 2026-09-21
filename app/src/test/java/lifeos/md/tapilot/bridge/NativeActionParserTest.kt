package lifeos.md.tapilot.bridge

import lifeos.md.tapilot.accessibility.NativeAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeActionParserTest {
    @Test fun `parses extended actions`() {
        assertEquals(NativeAction.System("notifications"), NativeActionParser.parse(JSONObject("""{"type":"system","action":"notifications"}""")))
        assertEquals(NativeAction.EditText("select", 1, 3, null), NativeActionParser.parse(JSONObject("""{"type":"editText","operation":"select","start":1,"end":3}""")))
        assertEquals(NativeAction.MultiGesture(listOf(listOf(NativeAction.Point(10f, 20f), NativeAction.Point(30f, 40f))), 600, 800),
            NativeActionParser.parse(JSONObject("""{"type":"gesture","fingers":[[{"x":10,"y":20},{"x":30,"y":40}]],"duration":600,"holdMs":800}""")))
        assertEquals(
            NativeAction.AppDetails("计算器"),
            NativeActionParser.parse(JSONObject("""{"type":"appDetails","packageOrName":"计算器"}""")),
        )
    }
    @Test
    fun `parses a typed input action`() {
        val action = NativeActionParser.parse(
            JSONObject(
                """{
                    "type":"input",
                    "text":"Midscene",
                    "x":100,
                    "y":200,
                    "replace":false,
                    "autoDismissKeyboard":false
                }""".trimIndent(),
            ),
        )

        assertEquals(
            NativeAction.Input("Midscene", 100f, 200f, false, false),
            action,
        )
    }
}
