package lifeos.md.tapilot.voice

/** One owner for the floating gesture lifecycle. UI components can only issue transitions. */
class FloatingVoiceSession {
    sealed interface Phase {
        data object Idle : Phase
        data class Held(val cancelSelected: Boolean = false) : Phase
        data object Released : Phase
        data class Failed(val message: String) : Phase
    }
    var phase: Phase = Phase.Idle; private set
    var generation = 0; private set
    var contextReady = false; private set
    var startedWithHold = false; private set
    private var finishWhenReady = false
    val active get() = phase != Phase.Idle
    val held get() = phase is Phase.Held
    val cancelSelected get() = (phase as? Phase.Held)?.cancelSelected == true
    val error get() = (phase as? Phase.Failed)?.message.orEmpty()

    fun begin(held: Boolean): Int {
        generation++
        startedWithHold = held
        phase = if (held) Phase.Held() else Phase.Released
        contextReady = false; finishWhenReady = false
        return generation
    }
    fun finishInput() { startedWithHold = false }
    fun isCurrent(token: Int) = active && generation == token
    fun selectCancel(selected: Boolean) { if (held) phase = Phase.Held(selected) }
    fun release(): Boolean {
        if (!held) return false
        phase = Phase.Released; finishWhenReady = true
        return true
    }
    fun consumeFinish(): Boolean {
        if (phase != Phase.Released || !finishWhenReady) return false
        finishWhenReady = false
        return true
    }
    fun contextCaptured(token: Int): Boolean {
        if (!isCurrent(token) || phase is Phase.Failed) return false
        contextReady = true
        return true
    }
    fun fail(message: String) { end(); phase = Phase.Failed(message) }
    fun end() { generation++; phase = Phase.Idle; contextReady = false; finishWhenReady = false; startedWithHold = false }
}
