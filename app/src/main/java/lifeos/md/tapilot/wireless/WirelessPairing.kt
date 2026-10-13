package lifeos.md.tapilot.wireless

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Only a hash of the paired computer's credential is persisted on the phone. */
internal class WirelessPairing(
    private val readHash: () -> String,
    private val writeHash: (String) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var code = ""
    private var expires = 0L

    @Synchronized fun offer(): String = random().also { code = it; expires = clock() + 300_000 }

    @Synchronized fun redeem(candidate: String): String {
        check(code.isNotEmpty() && clock() < expires && equal(code, candidate)) { "Pairing expired or invalid. Create a new pairing link on the phone." }
        val credential = random()
        writeHash(hash(credential))
        code = ""
        expires = 0
        return credential
    }

    @Synchronized fun authenticate(candidate: String): Boolean =
        candidate.length in 32..128 && readHash().let { it.isNotEmpty() && equal(it, hash(candidate)) }

    @Synchronized fun revoke() { code = ""; expires = 0; writeHash("") }

    companion object {
        private fun random() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        private fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
    }
}
