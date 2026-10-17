package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotRequestPacerTest {
    @Test fun `devices without observed limits are not slowed down`() {
        val pacer = ScreenshotRequestPacer()
        assertEquals(0L, pacer.delayMillis(0))
        pacer.requested(100)
        assertEquals(0L, pacer.delayMillis(101))
    }

    @Test fun `after a limit wait only the remaining interval before a fresh request`() {
        val pacer = ScreenshotRequestPacer()
        pacer.requested(100)
        pacer.rateLimited()
        assertEquals(250L, pacer.delayMillis(200))
        assertEquals(0L, pacer.delayMillis(450))
        pacer.requested(450)
        assertEquals(150L, pacer.delayMillis(650))
        assertEquals(0L, pacer.delayMillis(1000))
    }

    @Test fun `fresh service does not inherit previous service pacing`() {
        val previous = ScreenshotRequestPacer().apply { requested(100); rateLimited() }
        assertEquals(350L, previous.delayMillis(100))
        assertEquals(0L, ScreenshotRequestPacer().delayMillis(100))
    }

    @Test fun `guaranteed overlay preparation overlaps pacing without shortening request interval`() {
        val pacer = ScreenshotRequestPacer().apply { requested(100); rateLimited() }
        for (elapsed in listOf(0L, 50L, 200L, 325L, 350L, 500L)) {
            val now = 100L + elapsed
            val preparation = 50L
            val delay = pacer.delayMillis(now, preparation)
            assertEquals((350L - elapsed - preparation).coerceAtLeast(0L), delay)
            org.junit.Assert.assertTrue(now + delay + preparation >= 450L)
        }
        assertEquals(250L, pacer.delayMillis(200, 0))
    }

    @Test fun `preparation alone can cover the remaining interval without negative delay`() {
        val pacer = ScreenshotRequestPacer().apply { requested(100); rateLimited() }
        assertEquals(0L, pacer.delayMillis(420, 50))
        assertEquals(0L, ScreenshotRequestPacer().delayMillis(100, 50))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative preparation cannot change the interval`() {
        ScreenshotRequestPacer().delayMillis(100, -1)
    }
}
