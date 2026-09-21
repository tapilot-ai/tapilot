package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GestureCoordinatesTest {
    @Test
    fun scalesLogicalScreenshotCoordinatesToPhysicalGestureCoordinates() {
        val (scaleX, scaleY) = calculateGestureCoordinateScale(
            logicalWidth = 1080,
            logicalHeight = 2316,
            physicalWidth = 1440,
            physicalHeight = 3088,
        )

        assertEquals(4f / 3f, scaleX, 0.0001f)
        assertEquals(4f / 3f, scaleY, 0.0001f)
    }

    @Test
    fun rejectsInvalidLogicalDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            calculateGestureCoordinateScale(0, 2316, 1440, 3088)
        }
    }
}
