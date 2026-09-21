package lifeos.md.tapilot.bridge

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTaskStopRegistryTest {
    private var registration: ActiveTaskStopRegistry.Registration? = null

    @After
    fun tearDown() {
        ActiveTaskStopRegistry.unregister(registration)
    }

    @Test
    fun `notification stop reaches the active task`() {
        var stopped = false
        registration = ActiveTaskStopRegistry.register {
            stopped = true
            true
        }

        assertTrue(ActiveTaskStopRegistry.stopActive())
        assertTrue(stopped)
    }

    @Test
    fun `unregistered task cannot be stopped`() {
        registration = ActiveTaskStopRegistry.register { true }
        ActiveTaskStopRegistry.unregister(registration)
        registration = null

        assertFalse(ActiveTaskStopRegistry.stopActive())
    }
}
