package lifeos.md.tapilot.shell

import org.junit.Assert.*
import org.junit.Test

class BackgroundKeyTest {
    @Test fun onlyDisplayScopedNavigationAndEditingKeysAreAllowed() {
        assertEquals(4, backgroundKeyCode("Back"))
        assertEquals(66, backgroundKeyCode("KEYCODE_ENTER"))
        assertEquals(67, backgroundKeyCode("Backspace"))
        for (name in listOf("Home", "RecentApps", "Power", "VolumeUp", "notifications"))
            assertThrows(IllegalStateException::class.java) { backgroundKeyCode(name) }
    }
}
