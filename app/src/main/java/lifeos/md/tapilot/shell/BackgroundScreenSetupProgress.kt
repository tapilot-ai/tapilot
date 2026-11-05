package lifeos.md.tapilot.shell

/** Native prerequisites choose the stage; every stage entry restores its UI, including re-entry. */
internal class BackgroundScreenSetupProgress(private val timeoutMs: Long = 5 * 60_000) {
    private var current: SetupStep? = null
    private var deadline = 0L

    fun advance(snapshot: SetupSnapshot, now: Long): SetupDecision {
        val next = snapshot.next()
        if (next == SetupStep.READY) return SetupDecision.Ready
        if (current != null && current != SetupStep.CONSENT && next == SetupStep.CONSENT)
            return SetupDecision.Revoked
        if (next != current) {
            current = next
            deadline = now + timeoutMs
            return SetupDecision.Enter(next)
        }
        return if (now >= deadline) SetupDecision.TimedOut(next) else SetupDecision.Wait
    }
}

internal sealed interface SetupDecision {
    data object Ready : SetupDecision
    data object Wait : SetupDecision
    data object Revoked : SetupDecision
    data class Enter(val step: SetupStep) : SetupDecision
    data class TimedOut(val step: SetupStep) : SetupDecision
}
