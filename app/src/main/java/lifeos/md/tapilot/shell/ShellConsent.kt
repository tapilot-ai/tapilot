package lifeos.md.tapilot.shell

import java.security.MessageDigest

/** One-use, expiring consent. Process restart intentionally forgets authorization. */
internal class ShellConsent {
    private var token: String? = null
    private var until = 0L
    fun arm(value: String, now: Long) { token = value; until = now + 300_000 }
    fun consume(value: String?, uid: Int, now: Long): Boolean {
        val expected = token ?: return false
        if (uid != 2000 || now >= until || value == null) return false
        if (!MessageDigest.isEqual(expected.toByteArray(), value.toByteArray())) return false
        clear()
        return true
    }
    fun pending(now: Long): String? = token?.takeIf { now < until }
    fun clear() { token = null; until = 0L }
}
