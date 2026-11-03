package lifeos.md.tapilot.experiment

/** An old model run never regains input when a user resumes with a fresh observation. */
internal class VirtualTrialState {
    enum class Phase { READY, RUNNING, PAUSING, PAUSED, SUCCEEDED, FAILED, CLOSED }
    @Volatile var phase = Phase.READY; private set
    @Volatile private var epoch = 0
    val manual get() = phase in setOf(Phase.READY, Phase.PAUSED, Phase.SUCCEEDED, Phase.FAILED)
    fun begin(): Int {
        check(phase in setOf(Phase.READY, Phase.PAUSED, Phase.FAILED))
        epoch++; phase = Phase.RUNNING
        return epoch
    }
    fun permits(run: Int) = epoch == run && phase == Phase.RUNNING
    fun pause(): Int { check(phase == Phase.RUNNING); epoch++; phase = Phase.PAUSING; return epoch }
    fun paused(ticket: Int) { if (epoch == ticket && phase == Phase.PAUSING) phase = Phase.PAUSED }
    fun finish(run: Int, success: Boolean) { if (permits(run)) phase = if (success) Phase.SUCCEEDED else Phase.FAILED }
    fun close() { epoch++; phase = Phase.CLOSED }
}
