package lifeos.md.tapilot.accessibility

import org.junit.Test

class MultiGestureTest {
    private val points = listOf(NativeAction.Point(10f, 20f), NativeAction.Point(100f, 200f))
    @Test fun `accepts long press drag and multiple fingers`() {
        validateMultiGesture(NativeAction.MultiGesture(listOf(points), 600, 800), 400, 800)
        validateMultiGesture(NativeAction.MultiGesture(listOf(points, points), 600, 0), 400, 800)
    }
    @Test(expected = IllegalArgumentException::class) fun `rejects points outside display`() {
        validateMultiGesture(NativeAction.MultiGesture(listOf(points), 600, 0), 50, 800)
    }
    @Test(expected = IllegalArgumentException::class) fun `rejects non finite points`() {
        validateMultiGesture(NativeAction.MultiGesture(listOf(points + NativeAction.Point(Float.NaN, 0f)), 600, 0), 400, 800)
    }
    @Test(expected = IllegalArgumentException::class) fun `rejects unbounded duration`() {
        validateMultiGesture(NativeAction.MultiGesture(listOf(points), 10001, 0), 400, 800)
    }
    @Test(expected = IllegalArgumentException::class) fun `rejects no fingers`() {
        validateMultiGesture(NativeAction.MultiGesture(emptyList(), 600, 0), 400, 800)
    }
}
