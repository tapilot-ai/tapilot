package lifeos.md.tapilot.chat

import org.junit.Assert.*
import org.junit.Test

class TaskDurationTest {
    private fun task(phase: String = "completed", created: Long = 1_000, completed: Long = 158_996) =
        ChatTask(phase, emptyList(), 0, 0, 0, 1, emptyList(), created, completed)

    @Test fun showsMeasuredWallTimeForEveryTerminalOutcome() {
        for (phase in listOf("completed", "failed", "paused", "waiting_user")) {
            val duration = task(phase).durationMillis!!
            assertEquals(157_996L, duration)
            assertEquals("耗时 2 分 38 秒", taskDurationLabel(duration, true))
            assertEquals("Duration 2m 38s", taskDurationLabel(duration, false))
        }
    }
    @Test fun missingOrInvalidHistoryDoesNotInventADuration() {
        assertNull(task(created = 0).durationMillis)
        assertNull(task(completed = 0).durationMillis)
        assertNull(task(completed = 999).durationMillis)
        assertNull(task(phase = "running").durationMillis)
    }
    @Test fun formatsShortAndLongTasksAndRoundingAcrossMinuteBoundary() {
        assertEquals("耗时不足 1 秒", taskDurationLabel(0, true))
        assertEquals("Duration <1s", taskDurationLabel(999, false))
        assertEquals("耗时 1 分", taskDurationLabel(59_996, true))
        assertEquals("Duration 1h 2m 3s", taskDurationLabel(3_723_000, false))
    }
}
