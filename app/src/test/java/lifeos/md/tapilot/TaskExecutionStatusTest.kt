package lifeos.md.tapilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskExecutionStatusTest {
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

        assertEquals("Tapilot：任务执行失败，模型请求超时", presentation.announcement)
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
