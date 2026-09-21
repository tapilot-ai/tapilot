package lifeos.md.tapilot.accessibility

internal fun calculateGestureCoordinateScale(
    logicalWidth: Int,
    logicalHeight: Int,
    physicalWidth: Int,
    physicalHeight: Int,
): Pair<Float, Float> {
    require(logicalWidth > 0 && logicalHeight > 0) {
        "Logical display dimensions must be positive"
    }
    require(physicalWidth > 0 && physicalHeight > 0) {
        "Physical display dimensions must be positive"
    }
    return physicalWidth.toFloat() / logicalWidth to
        physicalHeight.toFloat() / logicalHeight
}
