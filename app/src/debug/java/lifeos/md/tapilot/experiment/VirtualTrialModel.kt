package lifeos.md.tapilot.experiment

import android.content.Context
import lifeos.md.tapilot.bridge.ModelCredentialStore
import lifeos.md.tapilot.chatgpt.ChatGptAccounts
import lifeos.md.tapilot.chatgpt.ChatGptMidscene
import lifeos.md.tapilot.conversation.ConversationStore
import lifeos.md.tapilot.conversation.ModelProfiles
import org.json.JSONObject
import java.io.File

/** Reads the user's existing model choice; never installs test credentials or changes selection. */
internal object VirtualTrialModel {
    fun environment(context: Context): Map<String, String> {
        val state = ConversationStore(File(context.filesDir, "native-conversation.json")).read()
        val json = if (state.optBoolean("chatgptConversation")) {
            val accounts = ChatGptAccounts.get(context)
            ChatGptMidscene.environment(accounts.selectedModel(), accounts.activeId())
        } else {
            val preset = state.optString("preset", "doubao")
            val profile = state.optJSONObject("profiles")?.optJSONObject(preset) ?: ModelProfiles.defaults(preset)
            val key = ModelCredentialStore(context).get(preset).orEmpty()
            check(key.isNotBlank()) { "Configure a model in Tapilot Settings first" }
            ModelProfiles.endpoint(profile.getString("baseUrl"))
            JSONObject().put("MIDSCENE_MODEL_BASE_URL", profile.getString("baseUrl"))
                .put("MIDSCENE_MODEL_API_KEY", key).put("MIDSCENE_MODEL_NAME", profile.getString("modelName"))
                .put("MIDSCENE_MODEL_FAMILY", profile.getString("modelFamily"))
        }
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
}
