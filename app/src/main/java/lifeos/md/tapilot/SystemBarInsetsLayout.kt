package lifeos.md.tapilot

import kotlin.math.roundToInt

internal data class SystemBarInsetsLayout(
    val topInsetPx: Int,
    val topInsetCssPx: Int,
    val bottomInsetCssPx: Int,
) {
    fun panelTopMarginPx(baseMarginPx: Int): Int = topInsetPx + baseMarginPx

    companion object {
        fun fromPixels(topInsetPx: Int, bottomInsetPx: Int, density: Float): SystemBarInsetsLayout {
            require(density > 0f) { "Display density must be positive" }
            return SystemBarInsetsLayout(
                topInsetPx = topInsetPx,
                topInsetCssPx = (topInsetPx / density).roundToInt(),
                bottomInsetCssPx = (bottomInsetPx / density).roundToInt(),
            )
        }
    }
}
