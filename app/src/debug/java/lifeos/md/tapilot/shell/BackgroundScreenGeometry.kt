package lifeos.md.tapilot.shell

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Keep phone dp geometry when bounding the capture's pixel cost. Never change the physical display. */
internal data class BackgroundScreenGeometry(val width: Int, val height: Int, val density: Int) {
    companion object {
        fun match(width: Int, height: Int, density: Int): BackgroundScreenGeometry {
            require(width > 0 && height > 0 && density > 0)
            val scale = minOf(1.0, 2560.0 / width, 2560.0 / height, sqrt(4_194_304.0 / width / height))
            return BackgroundScreenGeometry((width * scale).toInt().coerceAtLeast(240),
                (height * scale).toInt().coerceAtLeast(240), (density * scale).roundToInt().coerceIn(80, 800))
        }
    }
}
