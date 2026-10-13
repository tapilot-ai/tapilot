package lifeos.md.tapilot.chatgpt

import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChatGptCallbackTest {
    private fun get(port: Int, path: String): String = Socket("127.0.0.1", port).use {
        it.soTimeout = 2000
        it.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray())
        it.getInputStream().bufferedReader().readText()
    }
    @Test fun restartRebindsExactCallbackAndPreservesPkceAndNonce() {
        val original = ChatGptCallback()
        val saved = ChatGptPendingLogin(ChatGptOAuth.Attempt(original.redirect, "stable-host"), System.currentTimeMillis() + 5000).json()
        original.close()
        val restored = ChatGptPendingLogin.read(saved)
        val port = URI(restored.attempt.redirect).port
        val executor = Executors.newSingleThreadExecutor()
        try {
            ChatGptCallback(port).use { listener ->
                val result = executor.submit<Map<String, String>> { listener.await(restored.attempt, restored.expiresAt) }
                val http = get(port, "/auth/callback?state=${restored.attempt.state}&client_id=oaiapp_test&code=test-code")
                assertTrue(http.startsWith("HTTP/1.1 200")); assertFalse(http.contains("test-code"))
                val grant = restored.attempt.exchange(result.get(2, TimeUnit.SECONDS))
                assertEquals(saved.getString("verifier"), grant["code_verifier"])
                assertEquals(saved.getString("nonce"), restored.attempt.nonce)
                assertEquals(saved.getString("redirect"), grant["redirect_uri"])
            }
        } finally { executor.shutdownNow() }
    }
    @Test fun invalidRequestDoesNotConsumeValidPendingAuthorization() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ChatGptCallback().use { listener ->
                val attempt = ChatGptOAuth.Attempt(listener.redirect, "host")
                val port = URI(listener.redirect).port
                val result = executor.submit<Map<String, String>> { listener.await(attempt, System.currentTimeMillis() + 5000) }
                assertTrue(get(port, "/auth/callback?state=wrong&code=wrong").startsWith("HTTP/1.1 400"))
                assertFalse(result.isDone)
                assertTrue(get(port, "/auth/callback?state=${attempt.state}&error=access_denied").startsWith("HTTP/1.1 200"))
                assertEquals("access_denied", result.get(2, TimeUnit.SECONDS)["error"])
            }
        } finally { executor.shutdownNow() }
    }
    @Test fun expiredAttemptExplainsHowToRecoverWithoutAcceptingCallback() {
        ChatGptCallback().use { listener ->
            val failure = assertThrows(IllegalStateException::class.java) {
                listener.await(ChatGptOAuth.Attempt(listener.redirect, "host"), System.currentTimeMillis() - 1)
            }
            assertTrue(failure.message!!.contains("重新登录"))
        }
    }
}
