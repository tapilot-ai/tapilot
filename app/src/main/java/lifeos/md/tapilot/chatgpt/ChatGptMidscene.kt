package lifeos.md.tapilot.chatgpt

import lifeos.md.tapilot.AppText.text as appText
import org.json.JSONObject

/** Midscene's public SIWC configuration, scoped to one phone-operation process. */
object ChatGptMidscene {
    fun environment(model: String, accountId: String): JSONObject {
        val family = when {
            model.startsWith("gpt-6") -> "gpt-6"
            model.startsWith("gpt-5") -> "gpt-5"
            else -> error(appText("此模型暂不支持手机视觉操作，请选择 GPT-6 或 GPT-5.4 及以上模型", "This model does not support phone visual operations. Choose GPT-6 or GPT-5.4 or later."))
        }
        require(accountId.isNotBlank()) { appText("请先登录 ChatGPT", "Sign in to ChatGPT first") }
        return JSONObject()
            .put("MIDSCENE_MODEL_BASE_URL", ChatGptOAuth.RESOURCE)
            .put("MIDSCENE_MODEL_API_KEY", "tapilot-native-siwc")
            .put("MIDSCENE_CHATGPT_ACCOUNT_ID", accountId)
            .put("MIDSCENE_MODEL_NAME", model)
            .put("MIDSCENE_MODEL_FAMILY", family)
            .put("MIDSCENE_MODEL_PROTOCOL", "openai-responses")
            .put("MIDSCENE_MODEL_STREAM_MODE", "stream")
    }
}
