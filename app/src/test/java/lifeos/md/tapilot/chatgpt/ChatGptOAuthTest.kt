package lifeos.md.tapilot.chatgpt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class ChatGptOAuthTest {
    @Test fun pkceMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ChatGptOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun registrationUsesIssuedClientAndExactRedirect() {
        val a = ChatGptOAuth.Attempt("http://127.0.0.1:12345/auth/callback", "host", state = "expected")
        val grant = a.exchange(a.callback("/auth/callback?state=expected&code=abc&client_id=oaiapp_test"))
        assertEquals("oaiapp_test", grant["client_id"]); assertEquals(a.redirect, grant["redirect_uri"])
        assertTrue(a.authorize().contains("agent_name_hint=Tapilot"))
        assertFalse(ChatGptOAuth.Attempt(a.redirect, "host", "oaiapp_test").authorize().contains("agent_name_hint"))
    }
    @Test fun callbackRejectsMismatchedStateClientAndDuplicateParameters() {
        val a = ChatGptOAuth.Attempt("http://127.0.0.1:12345/auth/callback", "host", "oaiapp_existing", state = "expected")
        assertThrows(IllegalArgumentException::class.java) { a.callback("/auth/callback?state=wrong&code=abc") }
        assertThrows(IllegalArgumentException::class.java) { a.callback("/callback?state=expected&code=abc") }
        assertThrows(IllegalArgumentException::class.java) { a.callback("/auth/callback?state=expected&state=expected") }
        assertThrows(IllegalArgumentException::class.java) { a.exchange(mapOf("client_id" to "other", "code" to "abc")) }
        assertThrows(IllegalStateException::class.java) { a.exchange(mapOf("error" to "access_denied")) }
        assertThrows(IllegalArgumentException::class.java) { a.copy(client = ChatGptOAuth.DYNAMIC_CLIENT).exchange(mapOf("code" to "abc")) }
    }
    @Test fun oidcValidatesSignatureIssuerAudienceExpiryNonceAndIdentity() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val public = pair.public as RSAPublicKey
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val key = JSONObject().put("kty", "RSA").put("kid", "test").put("n", b64.encodeToString(public.modulus.toByteArray())).put("e", b64.encodeToString(public.publicExponent.toByteArray()))
        val jwks = JSONObject().put("keys", JSONArray().put(key))
        fun token(claims: JSONObject): String {
            val header = b64.encodeToString("""{"alg":"RS256","kid":"test"}""".toByteArray())
            val payload = b64.encodeToString(claims.toString().toByteArray())
            val signature = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update("$header.$payload".toByteArray()) }.sign()
            return "$header.$payload.${b64.encodeToString(signature)}"
        }
        val valid = JSONObject().put("iss", ChatGptOAuth.ISSUER).put("aud", "client").put("sub", "person").put("exp", 2000).put("nonce", "nonce")
        assertEquals("person", ChatGptOAuth.validateIdToken(token(valid), jwks, "client", "nonce", 1000).getString("sub"))
        for ((field, value) in listOf("iss" to "attacker", "aud" to "another", "nonce" to "wrong", "exp" to 900, "sub" to "")) {
            assertThrows(IllegalArgumentException::class.java) { ChatGptOAuth.validateIdToken(token(JSONObject(valid.toString()).put(field, value)), jwks, "client", "nonce", 1000) }
        }
        val signed = token(valid)
        val tampered = signed.split('.').toMutableList().apply { this[1] = b64.encodeToString(JSONObject(valid.toString()).put("sub", "attacker").toString().toByteArray()) }.joinToString(".")
        assertThrows(IllegalArgumentException::class.java) { ChatGptOAuth.validateIdToken(tampered, jwks, "client", "nonce", 1000) }
    }
}
