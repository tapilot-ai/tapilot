package lifeos.md.tapilot.bridge

import org.junit.Assert.*
import org.junit.Test

class CredentialReadTest {
    @Test fun missingKeyDoesNotAccessKeystore() {
        assertNull(readCredential("unused", null) { error("must not decrypt") })
    }
    @Test fun damagedProviderDoesNotAffectAnotherProviderAndHasRecoveryMessage() {
        val failure = runCatching { readCredential("speech-volcengine", "damaged") { error("invalid tag") } }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("speech-volcengine"))
        assertTrue(failure.message!!.contains("重置"))
        assertEquals("healthy", readCredential("qwen", "encrypted") { "healthy" })
        assertNull(failure.message!!.takeIf { it.contains("damaged") })
    }
}
