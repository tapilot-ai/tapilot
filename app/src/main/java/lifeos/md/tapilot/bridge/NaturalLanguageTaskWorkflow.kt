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
                        JSONArray()
                            .put(
                                JSONObject().put(
                                    "aiAct",
                                    JSONObject().put("prompt", task),
                                ),
                            )
                            .put(
                                JSONObject().put(
                                    "aiAsk",
                                    JSONObject().put(
                                        "prompt",
                                        "Return the final result to the user based on the current " +
                                            "screen after completing the original task. Include the " +
                                            "specific information requested for reading, querying, or " +
                                            "summarization tasks; otherwise briefly confirm the outcome. " +
                                            "Do not operate the device. Reply in the same language as the " +
                                            "original task. Original task: $task",
                                    ),
                                ),
                            ),
                    ),
            ),
        ).toString(2)
}
