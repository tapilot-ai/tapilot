package lifeos.md.tapilot.accessibility

import kotlin.math.roundToInt

object FloatingBallPosition {
    fun coordinates(
        screenWidth: Int,
        screenHeight: Int,
        ballSize: Int,
        xFraction: Float,
        yFraction: Float,
    ): Pair<Int, Int> {
        val maxX = (screenWidth - ballSize).coerceAtLeast(0)
        val maxY = (screenHeight - ballSize).coerceAtLeast(0)
        return (maxX * xFraction.coerceIn(0f, 1f)).roundToInt() to
            (maxY * yFraction.coerceIn(0f, 1f)).roundToInt()
    }

    fun fractions(
        screenWidth: Int,
        screenHeight: Int,
        ballSize: Int,
        x: Int,
        y: Int,
    ): Pair<Float, Float> {
        val maxX = (screenWidth - ballSize).coerceAtLeast(1)
        val maxY = (screenHeight - ballSize).coerceAtLeast(1)
        return (x.coerceIn(0, maxX).toFloat() / maxX) to
            (y.coerceIn(0, maxY).toFloat() / maxY)
    }
}
