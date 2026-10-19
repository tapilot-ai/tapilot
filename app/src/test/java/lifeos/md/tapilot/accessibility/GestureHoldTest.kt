package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureHoldTest {
    @Test fun `early DOWN acknowledgement cannot start movement before the hold expires`() {
        var now = 100L
        var moved = false
        var readyAt = 0L
        var scheduled: (() -> Unit)? = null
        val hold = GestureHold(800, { now }) { delay, next ->
            readyAt = now + delay
            scheduled = next
        }
        now = 120 // Android acknowledged DOWN, not the end of the 800 ms hold.
        hold.continueWhenReady { moved = true }
        assertFalse(moved)
        assertEquals(900L, readyAt)
        now = readyAt
        scheduled!!()
        assertTrue(moved)
    }

    @Test fun `a callback after the hold does not add another full hold`() {
        var now = 100L
        var delay = -1L
        val hold = GestureHold(800, { now }) { wait, _ -> delay = wait }
        now = 950
        hold.continueWhenReady {}
        assertEquals(0L, delay)
    }
}
