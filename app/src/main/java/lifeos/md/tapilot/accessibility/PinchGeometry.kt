package lifeos.md.tapilot.accessibility

internal data class PinchGeometry(
    val centerX: Float,
    val centerY: Float,
    val startRadius: Float,
    val endRadius: Float,
)

/** Fit both simultaneous strokes on screen while preserving their distance ratio. */
internal fun pinchGeometry(action: NativeAction.Pinch, width: Int, height: Int): PinchGeometry {
    require(width > 2 && height > 2) { "Invalid screen size for pinch" }
    require(listOf(action.centerX, action.centerY, action.startDistance, action.endDistance).all { it.isFinite() }) {
        "Pinch coordinates must be finite"
    }
    require(action.startDistance > 0 && action.endDistance > 0 && action.duration > 0) {
        "Pinch distances and duration must be positive"
    }
    val largest = maxOf(action.startDistance, action.endDistance)
    val scale = minOf(1f, (width - 2f) / largest)
    val start = action.startDistance * scale / 2f
    val end = action.endDistance * scale / 2f
    val radius = maxOf(start, end)
    return PinchGeometry(
        action.centerX.coerceIn(radius + 1f, maxOf(radius + 1f, width - 1f - radius)),
        action.centerY.coerceIn(1f, height - 1f),
        start,
        end,
    )
}
