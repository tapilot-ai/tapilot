package lifeos.md.tapilot.bridge

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTestRunCoordinatorTest {
    @Test
    fun `process failure after user stop is reported as stopped`() {
        val failure = nodeRunFailure(IllegalStateException("read interrupted"), true)

        assertTrue(failure is NodeTestRunCoordinator.NodeRunStoppedException)
    }

    @Test
    fun `process failure without user stop remains unchanged`() {
        val original = IllegalStateException("read interrupted")

        assertSame(original, nodeRunFailure(original, false))
    }
}
