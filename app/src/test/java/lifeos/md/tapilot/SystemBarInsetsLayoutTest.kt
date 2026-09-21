package lifeos.md.tapilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SystemBarInsetsLayoutTest {
    @Test
    fun convertsPhysicalInsetsToCssPixels() {
        val layout = SystemBarInsetsLayout.fromPixels(
            topInsetPx = 72,
            bottomInsetPx = 108,
            density = 3f,
        )

        assertEquals(24, layout.topInsetCssPx)
        assertEquals(36, layout.bottomInsetCssPx)
        assertEquals(96, layout.panelTopMarginPx(24))
    }

    @Test
    fun rejectsInvalidDensity() {
        assertThrows(IllegalArgumentException::class.java) {
            SystemBarInsetsLayout.fromPixels(0, 0, 0f)
        }
    }
}
