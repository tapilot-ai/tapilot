package lifeos.md.tapilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskExecutionStatusTest {
    @Test
    fun `waiting for clarification never displays a success effect`() {
        val presentation = TaskExecutionStatus.WAITING_USER.presentation()
        assertEquals(TaskEffect.NONE, presentation.effect)
        assertEquals("Tapilot: Waiting for your input", presentation.announcement)
        assertTrue(TaskExecutionStatus.WAITING_USER.isTerminal)
        TaskExecutionTracker.update(TaskExecutionStatus.WAITING_USER)
        assertFalse(TaskExecutionTracker.isActive)
    }

    @Test
    fun `cancellation remains active and silent until stopped is confirmed`() {
        TaskExecutionTracker.update(TaskExecutionStatus.STOPPING)
        assertTrue(TaskExecutionTracker.isActive)
        assertFalse(TaskExecutionStatus.STOPPING.isTerminal)
        assertNull(TaskExecutionStatus.STOPPING.presentation().announcement)
        assertNull(TaskExecutionStatus.STOPPING.presentation().resetAfterMillis)
        TaskExecutionTracker.update(TaskExecutionStatus.STOPPED)
        assertFalse(TaskExecutionTracker.isActive)
        assertTrue(TaskExecutionStatus.STOPPED.isTerminal)
    }

    @Test
    fun `running status remains visible without resetting`() {
        val presentation = TaskExecutionStatus.RUNNING.presentation()

        assertEquals(TaskEffect.ACTIVE, presentation.effect)
        assertNull(presentation.resetAfterMillis)
    }

    @Test
    fun `finalizing keeps the active effect until report persistence completes`() {
        val presentation = TaskExecutionStatus.FINALIZING.presentation()

        assertEquals(TaskEffect.ACTIVE, presentation.effect)
        assertNull(presentation.resetAfterMillis)
    }

    @Test
    fun `failure includes a useful detail and eventually resets`() {
        val presentation = TaskExecutionStatus.FAILED.presentation("模型请求超时")

        assertEquals("Tapilot: Task failed, 模型请求超时", presentation.announcement)
        assertEquals(TaskEffect.FAILURE, presentation.effect)
        assertEquals(4_000L, presentation.resetAfterMillis)
    }

    @Test
    fun `task tracker stays active through report finalization`() {
        TaskExecutionTracker.update(TaskExecutionStatus.STARTING)
        assertTrue(TaskExecutionTracker.isActive)

        TaskExecutionTracker.update(TaskExecutionStatus.FINALIZING)
        assertTrue(TaskExecutionTracker.isActive)

        TaskExecutionTracker.update(TaskExecutionStatus.STOPPED)
        assertFalse(TaskExecutionTracker.isActive)
    }
}
