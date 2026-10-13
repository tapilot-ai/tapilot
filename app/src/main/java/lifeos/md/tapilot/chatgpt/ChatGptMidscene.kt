package lifeos.md.tapilot.chatgpt

import org.json.JSONObject

/** Midscene's public SIWC configuration, scoped to one phone-operation process. */
object ChatGptMidscene {
    fun environment(model: String, accessToken: String): JSONObject {
        val family = when {
            model.startsWith("gpt-6") -> "gpt-6"
            model.startsWith("gpt-5") -> "gpt-5"
            else -> error("此模型暂不支持手机视觉操作，请选择 GPT-6 或 GPT-5.4 及以上模型")
        }
        require(accessToken.isNotBlank()) { "请先登录 ChatGPT" }
        return JSONObject()
            .put("MIDSCENE_MODEL_BASE_URL", ChatGptOAuth.RESOURCE)
            .put("MIDSCENE_MODEL_API_KEY", accessToken)
            .put("MIDSCENE_MODEL_NAME", model)
            .put("MIDSCENE_MODEL_FAMILY", family)
            .put("MIDSCENE_MODEL_PROTOCOL", "openai-responses")
            .put("MIDSCENE_MODEL_STREAM_MODE", "stream")
    }
}
