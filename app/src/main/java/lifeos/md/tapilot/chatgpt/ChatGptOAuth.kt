package lifeos.md.tapilot.chatgpt

import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/** Public-client OAuth and OIDC validation. No credentials are returned to the browser. */
object ChatGptOAuth {
    const val ISSUER = "https://auth.openai.com"
    const val RESOURCE = "https://api.openai.com/v1"
    const val DYNAMIC_CLIENT = "dynamic_agent_client"
    const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    fun random(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun form(values: Map<String, String>): String = values.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }
    data class Attempt(val redirect: String, val host: String, val client: String = DYNAMIC_CLIENT,
                       val state: String = random(), val nonce: String = random(), val verifier: String = random()) {
        init {
            val uri = URI(redirect)
            require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.path == "/auth/callback" && uri.rawQuery == null && uri.fragment == null && uri.userInfo == null) { "Invalid OAuth loopback address" }
        }
        fun authorize(idToken: String = "", email: String = ""): String {
            val fields = linkedMapOf("client_id" to client, "ext_agent_host_id" to host, "response_type" to "code",
                "redirect_uri" to redirect, "scope" to "openid profile email offline_access resource.invoke $PLAN_SCOPE",
                "resource" to RESOURCE, "state" to state, "nonce" to nonce,
                "code_challenge_method" to "S256", "code_challenge" to challenge(verifier))
            if (client == DYNAMIC_CLIENT) fields["agent_name_hint"] = "Tapilot"
            else {
                if (idToken.isNotBlank()) fields["id_token_hint"] = idToken
                if (email.isNotBlank()) fields["login_hint"] = email
            }
            return "$ISSUER/api/accounts/authorize?${form(fields)}"
        }
        fun callback(target: String): Map<String, String> {
            val uri = URI(target)
            require(uri.path == "/auth/callback") { "Invalid OAuth callback path" }
            val values = linkedMapOf<String, String>()
            uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.forEach {
                val pair = it.split('=', limit = 2)
                val key = URLDecoder.decode(pair[0], "UTF-8")
                require(!values.containsKey(key)) { "Duplicate OAuth callback parameter" }
                values[key] = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }
            require(MessageDigest.isEqual(state.toByteArray(), values["state"].orEmpty().toByteArray())) { "Invalid OAuth state" }
            return values
        }
        fun exchange(values: Map<String, String>): Map<String, String> {
            check(values["error"].isNullOrBlank()) { if (values["error"] == "access_denied") "ChatGPT 授权已取消" else "ChatGPT 授权失败，请重新登录" }
            val issued = values["client_id"] ?: client
            require(issued.isNotBlank() && issued != DYNAMIC_CLIENT && (client == DYNAMIC_CLIENT || client == issued)) { "Invalid ChatGPT client registration" }
            val code = values["code"].orEmpty(); require(code.isNotBlank()) { "Missing authorization code" }
            return mapOf("grant_type" to "authorization_code", "client_id" to issued, "code" to code,
                "code_verifier" to verifier, "redirect_uri" to redirect, "resource" to RESOURCE)
        }
    }
    fun validateIdToken(token: String, jwks: JSONObject, client: String, nonce: String?, now: Long = System.currentTimeMillis() / 1000): JSONObject {
        val parts = token.split('.'); require(parts.size == 3) { "Invalid ID token" }
        val decoder = Base64.getUrlDecoder()
        val header = JSONObject(String(decoder.decode(parts[0]), Charsets.UTF_8))
        require(header.optString("alg") == "RS256") { "Unsupported ID token signature" }
        val keys = jwks.getJSONArray("keys")
        val key = (0 until keys.length()).map { keys.getJSONObject(it) }.firstOrNull {
            it.optString("kid") == header.getString("kid") && it.optString("kty") == "RSA" && it.optString("use", "sig") == "sig"
        } ?: error("ID token signing key not found")
        val rsa = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1, decoder.decode(key.getString("n"))), BigInteger(1, decoder.decode(key.getString("e")))))
        val signature = Signature.getInstance("SHA256withRSA").apply { initVerify(rsa); update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)) }
        require(signature.verify(decoder.decode(parts[2]))) { "Invalid ID token signature" }
        val claims = JSONObject(String(decoder.decode(parts[1]), Charsets.UTF_8))
        val audience = claims.optJSONArray("aud")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: listOf(claims.optString("aud"))
        require(claims.optString("iss") == ISSUER && client in audience) { "Invalid ID token issuer or audience" }
        require(audience.size == 1 || claims.optString("azp") == client) { "Invalid ID token authorized party" }
        require(claims.optLong("exp") > now && claims.optLong("iat") <= now + 60 && claims.optLong("nbf") <= now + 60) { "ID token expired or not yet valid" }
        require(claims.optString("sub").isNotBlank()) { "ID token has no subject" }
        if (nonce != null) require(MessageDigest.isEqual(nonce.toByteArray(), claims.optString("nonce").toByteArray())) { "Invalid ID token nonce" }
        return claims
    }
}
