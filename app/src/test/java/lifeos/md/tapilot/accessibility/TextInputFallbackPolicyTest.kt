package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputFallbackPolicyTest {
    @Test
    fun `prefers editable node then input connection then clipboard paste`() {
        assertEquals(
            TextInputMethod.ACCESSIBILITY_NODE,
            TextInputFallbackPolicy.resolve(true, true, true),
        )
        assertEquals(
            TextInputMethod.INPUT_CONNECTION,
            TextInputFallbackPolicy.resolve(false, true, true),
        )
        assertEquals(
            TextInputMethod.CLIPBOARD_PASTE,
            TextInputFallbackPolicy.resolve(false, false, true),
        )
        assertEquals(
            TextInputMethod.UNAVAILABLE,
            TextInputFallbackPolicy.resolve(false, false, false),
        )
    }

    @Test
    fun `accepts only a recent tap for clipboard fallback`() {
        assertTrue(TextInputFallbackPolicy.isRecentTap(1_000L, 16_000L))
        assertFalse(TextInputFallbackPolicy.isRecentTap(1_000L, 16_001L))
        assertFalse(TextInputFallbackPolicy.isRecentTap(0L, 1_000L))
        assertFalse(TextInputFallbackPolicy.isRecentTap(2_000L, 1_000L))
    }
}
