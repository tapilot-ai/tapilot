package lifeos.md.tapilot.experiment

import org.junit.Assert.*
import org.junit.Test

class VirtualTrialStateTest {
    @Test fun takeoverInvalidatesQueuedModelInputAndWaitsForDrain() {
        val state = VirtualTrialState()
        val old = state.begin()
        assertTrue(state.permits(old)); assertFalse(state.manual)
        val pause = state.pause()
        assertFalse(state.permits(old)); assertFalse(state.manual)
        state.finish(old, true)
        assertEquals(VirtualTrialState.Phase.PAUSING, state.phase)
        state.paused(pause)
        assertTrue(state.manual)
        val fresh = state.begin()
        assertFalse(state.permits(old)); assertTrue(state.permits(fresh))
        state.paused(pause)
        assertEquals(VirtualTrialState.Phase.RUNNING, state.phase)
    }
    @Test fun closeNeverAcceptsLateCompletionOrRestarts() {
        val state = VirtualTrialState()
        val run = state.begin()
        val pause = state.pause()
        state.close(); state.paused(pause); state.finish(run, true)
        assertEquals(VirtualTrialState.Phase.CLOSED, state.phase)
        assertFalse(state.manual); assertFalse(state.permits(run))
        assertThrows(IllegalStateException::class.java) { state.begin() }
    }
    @Test fun successfulRunCannotBeReplayed() {
        val state = VirtualTrialState()
        val run = state.begin(); state.finish(run, true)
        assertThrows(IllegalStateException::class.java) { state.begin() }
    }
}
