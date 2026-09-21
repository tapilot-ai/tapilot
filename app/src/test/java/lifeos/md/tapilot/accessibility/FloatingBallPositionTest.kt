package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBallPositionTest {
    @Test
    fun `restores a proportional position on a resized screen`() {
        val fractions = FloatingBallPosition.fractions(1000, 2000, 100, 450, 950)

        assertEquals(250 to 500, FloatingBallPosition.coordinates(550, 1050, 50, fractions.first, fractions.second))
    }

    @Test
    fun `clamps coordinates and fractions to the visible screen`() {
        assertEquals(900 to 0, FloatingBallPosition.coordinates(1000, 2000, 100, 2f, -1f))
        assertEquals(1f to 0f, FloatingBallPosition.fractions(1000, 2000, 100, 2000, -10))
    }
}
