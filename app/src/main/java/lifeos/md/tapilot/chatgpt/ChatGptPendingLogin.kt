package lifeos.md.tapilot.chatgpt

import org.json.JSONObject

/** Stored only inside the encrypted credential record; never expose PKCE or state to diagnostics. */
internal data class ChatGptPendingLogin(val attempt: ChatGptOAuth.Attempt, val expiresAt: Long) {
    fun json(): JSONObject = JSONObject().put("redirect", attempt.redirect).put("host", attempt.host)
        .put("client", attempt.client).put("state", attempt.state).put("nonce", attempt.nonce)
        .put("verifier", attempt.verifier).put("expiresAt", expiresAt)
    companion object {
        fun read(value: JSONObject): ChatGptPendingLogin = ChatGptPendingLogin(ChatGptOAuth.Attempt(
            value.getString("redirect"), value.getString("host"), value.getString("client"),
            value.getString("state"), value.getString("nonce"), value.getString("verifier")), value.getLong("expiresAt"))
    }
}
