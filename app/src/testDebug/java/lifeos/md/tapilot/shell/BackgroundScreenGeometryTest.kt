package lifeos.md.tapilot.shell

import org.junit.Assert.*
import org.junit.Test

class BackgroundScreenGeometryTest {
    @Test fun foldedOppoKeepsNativePhoneDensityAndAspectRatio() {
        assertEquals(BackgroundScreenGeometry(988, 1972, 440), BackgroundScreenGeometry.match(988, 1972, 440))
    }
    @Test fun largeDisplaysPreserveLogicalWidthWithinBoundedCaptureBudget() {
        val value = BackgroundScreenGeometry.match(1440, 3200, 640)
        assertTrue(value.width <= 2560 && value.height <= 2560)
        assertTrue(value.width.toLong() * value.height <= 4_194_304)
        assertEquals(1440.0 / 640, value.width.toDouble() / value.density, 0.01)
        assertEquals(1440.0 / 3200, value.width.toDouble() / value.height, 0.001)
    }
    @Test fun landscapeIsNotForcedIntoPortrait() {
        val value = BackgroundScreenGeometry.match(2400, 1080, 420)
        assertEquals(2400, value.width); assertEquals(1080, value.height); assertEquals(420, value.density)
    }
    @Test fun roundedPixelDimensionsNeverExceedAreaBudget() {
        for (width in 2100..2500 step 17) for (height in 2100..2500 step 19) {
            val value = BackgroundScreenGeometry.match(width, height, 440)
            assertTrue(value.width.toLong() * value.height <= 4_194_304)
        }
    }
}
