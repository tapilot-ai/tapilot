package lifeos.md.tapilot.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportReadinessTrackerTest {
    @Test
    fun `waits for rendered content instead of treating navigation as ready`() {
        val tracker = ReportReadinessTracker(maxAttempts = 2)

        assertEquals(ReportReadinessTracker.Decision.RETRY, tracker.evaluate(false).decision)
        tracker.recordConsoleError("ReferenceError: missing module")
        val failure = tracker.evaluate(false)

        assertEquals(ReportReadinessTracker.Decision.FAILED, failure.decision)
        assertTrue(failure.errorMessage.orEmpty().contains("missing module"))
        assertEquals(ReportReadinessTracker.Decision.READY, tracker.evaluate(true).decision)
    }
}
