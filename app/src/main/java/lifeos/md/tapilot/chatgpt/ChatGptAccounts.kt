package lifeos.md.tapilot.chatgpt

import lifeos.md.tapilot.AppText.text as appText
import android.content.Context
import lifeos.md.tapilot.bridge.EncryptedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Renewable credentials stay in Keystore-encrypted storage, separate from model API keys. */
class ChatGptAccounts private constructor(context: Context) {
    private val ciVerification = context.packageName == "lifeos.md.tapilot.e2everify" &&
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    companion object {
        @Volatile private var instance: ChatGptAccounts? = null
        fun get(context: Context): ChatGptAccounts = instance ?: synchronized(this) {
            instance ?: ChatGptAccounts(context.applicationContext).also { instance = it }
        }
    }
    private val storage = EncryptedPreferences(context, "tapilot-chatgpt", "tapilot-chatgpt-credentials")
    private val stateLock = Any()
    private val refreshLock = Any()
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    data class Account(val id: String, val email: String, val signedIn: Boolean) {
        val label: String get() = "$email · ${id.takeLast(8)}"
    }
    data class Model(val id: String, val label: String)
    private fun read(): JSONObject = storage.get("state")?.let(::JSONObject) ?: JSONObject().put("host", "urn:uuid:${UUID.randomUUID()}").put("accounts", JSONObject())
    private fun <T> state(action: (JSONObject) -> T): T = synchronized(stateLock) { action(read()) }
    private fun change(action: (JSONObject) -> Unit) = synchronized(stateLock) { val value = read(); action(value); storage.set("state", value.toString()) }
    fun accounts(): List<Account> = state { value -> val records = value.getJSONObject("accounts")
        records.keys().asSequence().map { id -> records.getJSONObject(id).let { Account(id, it.optString("email", "ChatGPT"), it.optString("access_token").isNotBlank()) } }.toList() }
    fun selectedModel(id: String = activeId()): String = if (id.isBlank()) "" else record(id).optString("model")
    fun selectModel(id: String, model: String) = change { it.getJSONObject("accounts").getJSONObject(id).put("model", model) }
    fun activeId(): String = state { it.optString("active") }
    fun ready(id: String = activeId()): Boolean = accounts().any { it.id == id && it.signedIn }
    fun select(id: String) = change { require(it.getJSONObject("accounts").has(id)); it.put("active", id) }
    private fun record(id: String): JSONObject = state { JSONObject(it.getJSONObject("accounts").getJSONObject(id).toString()) }

    /** Debug CI imports only a short access lease; this account can never rotate credentials. */
    internal fun installCiAccessLease(access: String, expiresAt: Long) {
        check(ciVerification)
        require(access.isNotBlank() && expiresAt - System.currentTimeMillis() >= TimeUnit.MINUTES.toMillis(20))
        val id = "ci-access-only"
        change { value ->
            value.getJSONObject("accounts").put(id, JSONObject().put("access_token", access)
                .put("expires_at", expiresAt).put("ci_access_only", true)
                .put("email", "CI access lease").put("model", "gpt-6.1-terra"))
            value.put("active", id)
        }
    }

    inner class Login internal constructor(private val pending: ChatGptPendingLogin, private val listener: ChatGptCallback, private val previous: JSONObject?) : AutoCloseable {
        val attempt get() = pending.attempt
        @Volatile var cancelled = false; private set
        val url: String get() = attempt.authorize(previous?.optString("id_token").orEmpty(), previous?.optString("email").orEmpty())
        fun finish(onReady: () -> Unit = {}): Account {
            listener.use {
                val callback = listener.await(attempt, pending.expiresAt, onReady)
                phase("callback_received")
                val grant = attempt.exchange(callback)
                val client = grant.getValue("client_id")
                val tokens = post("${ChatGptOAuth.ISSUER}/api/accounts/oauth/token", grant)
                val claims = ChatGptOAuth.validateIdToken(tokens.getString("id_token"), jwks(), client, attempt.nonce)
                require(previous == null || claims.getString("sub") == previous.getString("subject")) { appText("ChatGPT 账号与原注册不匹配", "ChatGPT account does not match the original registration") }
                val saved = tokenRecord(tokens, previous).put("client_id", client).put("subject", claims.getString("sub"))
                    .put("email", claims.optString("email", "ChatGPT")).put("issuer", ChatGptOAuth.ISSUER)
                synchronized(this) {
                    check(!cancelled) { appText("ChatGPT 登录已取消", "ChatGPT sign-in cancelled") }
                    change { state ->
                        val records = state.getJSONObject("accounts")
                        val existing = records.optJSONObject(client)
                        require(existing == null || existing.getString("subject") == claims.getString("sub")) { appText("ChatGPT 注册账号不匹配", "ChatGPT registration account mismatch") }
                        records.put(client, saved); state.put("active", client)
                        if (state.optJSONObject("pendingLogin")?.optString("state") == attempt.state) state.remove("pendingLogin")
                        state.put("loginPhase", "authorized")
                    }
                }
                return Account(client, saved.getString("email"), true)
            }
        }
        fun discard() {
            change { if (it.optJSONObject("pendingLogin")?.optString("state") == attempt.state) it.remove("pendingLogin") }
        }
        override fun close() { synchronized(this) { cancelled = true; listener.close() } }
    }
    private fun phase(value: String) = change { it.put("loginPhase", value) }
    fun begin(id: String? = null): Login {
        val previous = id?.let(::record)
        var host = ""
        change { host = it.getString("host") }
        val listener = ChatGptCallback()
        try {
            val attempt = ChatGptOAuth.Attempt(listener.redirect, host, id ?: ChatGptOAuth.DYNAMIC_CLIENT)
            val pending = ChatGptPendingLogin(attempt, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15))
            change { it.put("pendingLogin", pending.json()).put("loginPhase", "waiting_for_browser") }
            return Login(pending, listener, previous)
        } catch (failure: Exception) { listener.close(); throw failure }
    }
    /** Restore the exact PKCE attempt and port after process recreation, before opening any new login. */
    fun restoreLogin(): Login? {
        val value = state { it.optJSONObject("pendingLogin")?.let { pending -> JSONObject(pending.toString()) } } ?: return null
        val pending = ChatGptPendingLogin.read(value)
        if (pending.expiresAt <= System.currentTimeMillis()) {
            change { it.remove("pendingLogin"); it.put("loginPhase", "expired") }
            return null
        }
        val previous = pending.attempt.client.takeUnless { it == ChatGptOAuth.DYNAMIC_CLIENT }?.let(::record)
        val listener = ChatGptCallback(URI(pending.attempt.redirect).port)
        return Login(pending, listener, previous)
    }
    fun loginPhase(): String = state { it.optString("loginPhase") }
    private fun tokenRecord(tokens: JSONObject, old: JSONObject?): JSONObject {
        require(tokens.optString("token_type").equals("Bearer", true)) { "Unsupported ChatGPT token type" }
        val scope = tokens.optString("scope", old?.optString("scope").orEmpty())
        require(ChatGptOAuth.PLAN_SCOPE in scope.split(' ')) { appText("未授权使用 ChatGPT 套餐，请重新登录并允许套餐使用", "ChatGPT plan access was not granted. Sign in again and allow plan access") }
        require(tokens.optString("access_token").isNotBlank() && tokens.optLong("expires_in") > 0) { "Incomplete ChatGPT token response" }
        val result = old?.let { JSONObject(it.toString()) } ?: JSONObject()
        listOf("access_token", "refresh_token", "id_token").forEach { field -> if (tokens.optString(field).isNotBlank()) result.put(field, tokens.getString(field)) }
        require(result.optString("refresh_token").isNotBlank()) { appText("ChatGPT 未返回可续期授权，请重新登录", "ChatGPT returned no renewable authorization. Sign in again") }
        return result.put("scope", scope).put("expires_at", System.currentTimeMillis() + tokens.getLong("expires_in") * 1000)
    }
    fun accessToken(id: String, forceRefresh: Boolean = false): String = synchronized(refreshLock) {
        val saved = record(id)
        check(saved.optString("access_token").isNotBlank()) { appText("请先登录 ChatGPT", "Sign in to ChatGPT first") }
        if (!forceRefresh && saved.optLong("expires_at") > System.currentTimeMillis() + 60_000) return@synchronized saved.getString("access_token")
        check(!saved.optBoolean("ci_access_only")) { "CI SIWC lease expired or rejected; shards cannot refresh credentials" }
        val tokens = post("${ChatGptOAuth.ISSUER}/api/accounts/oauth/token", mapOf("grant_type" to "refresh_token", "client_id" to id,
            "refresh_token" to saved.getString("refresh_token"), "resource" to ChatGptOAuth.RESOURCE))
        if (tokens.optString("id_token").isNotBlank()) {
            val claims = ChatGptOAuth.validateIdToken(tokens.getString("id_token"), jwks(), id, null)
            require(claims.getString("sub") == saved.getString("subject")) { appText("ChatGPT 刷新账号不匹配，请重新登录", "ChatGPT refresh account mismatch. Sign in again") }
        }
        val updated = tokenRecord(tokens, saved)
        change { it.getJSONObject("accounts").put(id, updated) }
        updated.getString("access_token")
    }
    fun models(id: String): List<Model> {
        val body = request(Request.Builder().url("${ChatGptOAuth.RESOURCE}/models").header("Authorization", "Bearer ${accessToken(id)}").build())
        val models = body.getJSONArray("models")
        return (0 until models.length()).map { models.getJSONObject(it) }.filter { it.optString("visibility") == "list" }
            .map { Model(it.getString("slug"), it.optString("display_name", it.getString("slug"))) }.also { check(it.isNotEmpty()) { appText("此 ChatGPT 账号暂无可用模型", "No models available for this ChatGPT account") } }
    }
    /** Preserve registration identity and host; revoke and remove only renewable credentials. */
    fun signOut(id: String): Boolean = synchronized(refreshLock) {
        val saved = record(id)
        val revoked = saved.optString("refresh_token").isBlank() || runCatching {
            val endpoint = discovery().getString("revocation_endpoint")
            require(endpoint.startsWith("${ChatGptOAuth.ISSUER}/"))
            post(endpoint, mapOf("token" to saved.getString("refresh_token"), "token_type_hint" to "refresh_token", "client_id" to id)); true
        }.getOrDefault(false)
        change { value -> value.getJSONObject("accounts").getJSONObject(id).apply {
            listOf("access_token", "refresh_token", "id_token", "scope", "expires_at").forEach(::remove)
        } }
        revoked
    }
    private fun discovery() = request(Request.Builder().url("${ChatGptOAuth.ISSUER}/.well-known/openid-configuration").build())
    private fun jwks(): JSONObject {
        val config = discovery(); require(config.getString("issuer") == ChatGptOAuth.ISSUER)
        val uri = config.getString("jwks_uri"); require(uri.startsWith("${ChatGptOAuth.ISSUER}/"))
        return request(Request.Builder().url(uri).build())
    }
    private fun post(url: String, values: Map<String, String>) = request(Request.Builder().url(url)
        .post(ChatGptOAuth.form(values).toRequestBody("application/x-www-form-urlencoded".toMediaType())).build())
    private fun request(request: Request): JSONObject = http.newCall(request).execute().use {
        // Do not include raw OAuth bodies or request URLs in diagnostics.
        val text = it.body?.string().orEmpty()
        if (!it.isSuccessful) {
            val code = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
            error(if (code in listOf("invalid_grant", "invalid_token")) appText("ChatGPT 授权已失效，请重新登录", "ChatGPT authorization expired. Sign in again") else appText("ChatGPT 请求失败 · HTTP ${it.code}", "ChatGPT request failed · HTTP ${it.code}"))
        }
        if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
