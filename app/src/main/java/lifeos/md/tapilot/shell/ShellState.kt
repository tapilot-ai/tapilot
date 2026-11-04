package lifeos.md.tapilot.shell

/** Transport state never implies that a task may resume or that consent was granted. */
internal enum class ShellState { OFF, SEARCHING, PAIR_CODE, PAIRING, CONNECTING, CONNECTED, REPAIR, PAIR_FAILED }

internal class ShellRetry {
    private var failures = 0
    fun reset() { failures = 0 }
    fun nextDelay(): Long {
        val delay = longArrayOf(2_000, 5_000, 15_000, 30_000, 60_000)[failures]
        failures = (failures + 1).coerceAtMost(4)
        return delay
    }
}
