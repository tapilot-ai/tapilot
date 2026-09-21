package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinchGeometryTest {
    @Test fun `preserves enlargement and shrinking`() {
        for ((start, end) in listOf(100f to 300f, 300f to 100f)) {
            val result = pinchGeometry(NativeAction.Pinch(200f, 400f, start, end, 500), 400, 800)
            assertEquals(start / 2, result.startRadius, 0.001f)
            assertEquals(end / 2, result.endRadius, 0.001f)
            assertEquals(200f, result.centerX, 0.001f)
        }
    }

    @Test fun `keeps both fingers within the screen near edges and for oversized distances`() {
        val result = pinchGeometry(NativeAction.Pinch(2f, 900f, 200f, 1200f, 500), 400, 800)
        val radius = maxOf(result.startRadius, result.endRadius)
        assertTrue(result.centerX - radius >= 1f)
        assertTrue(result.centerX + radius <= 399f)
        assertTrue(result.centerY <= 799f)
        assertEquals(6f, result.endRadius / result.startRadius, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid distances`() {
        pinchGeometry(NativeAction.Pinch(200f, 400f, -100f, 300f, 500), 400, 800)
    }
}
