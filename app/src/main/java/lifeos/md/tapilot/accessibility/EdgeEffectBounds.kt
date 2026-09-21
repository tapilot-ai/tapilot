package lifeos.md.tapilot.accessibility

internal data class EdgeEffectBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal fun edgeEffectBounds(
    width: Int,
    height: Int,
    inset: Float = 0f,
): EdgeEffectBounds {
    val safeInset = inset.coerceAtLeast(0f)
    return EdgeEffectBounds(
        left = safeInset,
        top = safeInset,
        right = (width.toFloat() - safeInset).coerceAtLeast(safeInset),
        bottom = (height.toFloat() - safeInset).coerceAtLeast(safeInset),
    )
}

internal data class EdgeEffectCornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
) {
    fun asPathRadii(): FloatArray = floatArrayOf(
        topLeft,
        topLeft,
        topRight,
        topRight,
        bottomRight,
        bottomRight,
        bottomLeft,
        bottomLeft,
    )

    fun insetBy(inset: Float): EdgeEffectCornerRadii = EdgeEffectCornerRadii(
        topLeft = (topLeft - inset).coerceAtLeast(0f),
        topRight = (topRight - inset).coerceAtLeast(0f),
        bottomRight = (bottomRight - inset).coerceAtLeast(0f),
        bottomLeft = (bottomLeft - inset).coerceAtLeast(0f),
    )
}

internal fun edgeEffectCornerRadii(
    topLeft: Int?,
    topRight: Int?,
    bottomRight: Int?,
    bottomLeft: Int?,
    fallback: Float,
): EdgeEffectCornerRadii {
    // A reported small radius is real geometry, not something to enlarge for style.
    fun radius(value: Int?): Float = value?.takeIf { it > 0 }?.toFloat() ?: fallback
    return EdgeEffectCornerRadii(
        topLeft = radius(topLeft),
        topRight = radius(topRight),
        bottomRight = radius(bottomRight),
        bottomLeft = radius(bottomLeft),
    )
}

internal fun edgeEffectCornerRadiiForMode(
    mode: EdgeEffectGeometryMode,
    automatic: EdgeEffectCornerRadii,
    customRadius: Float,
): EdgeEffectCornerRadii = when (mode) {
    EdgeEffectGeometryMode.AUTO -> automatic
    EdgeEffectGeometryMode.SQUARE -> EdgeEffectCornerRadii(0f, 0f, 0f, 0f)
    EdgeEffectGeometryMode.CUSTOM -> EdgeEffectCornerRadii(
        customRadius,
        customRadius,
        customRadius,
        customRadius,
    )
}
