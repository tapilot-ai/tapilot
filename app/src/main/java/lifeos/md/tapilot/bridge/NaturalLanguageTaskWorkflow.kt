package lifeos.md.tapilot.bridge

import org.json.JSONArray
import org.json.JSONObject

internal object NaturalLanguageTaskWorkflow {
    fun asYaml(task: String): String = JSONObject()
        .put(
            "cases",
            JSONArray().put(
                JSONObject()
                    .put("name", "Tapilot Android task")
                    .put(
                        "steps",
                        JSONArray().put(
                            JSONObject().put(
                                "aiAct",
                                JSONObject().put("prompt", task),
                            ),
                        ),
                    ),
            ),
        ).toString(2)
}
