package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowCapturePolicyTest {
    private val app = CaptureWindow(1, true, false, true, 0, 0, 1080, 2400)
    @Test fun `full screen app ignores accessibility effect`() {
        val effect = app.copy(id = 2, application = false, assistantOverlay = true, active = false)
        assertEquals(1, windowCaptureCandidate(listOf(app, effect), 1080, 2400))
    }
    @Test fun `keyboard dialog and system bars require full display`() {
        for (overlay in listOf(
            app.copy(id = 2, application = false, top = 1800),
            app.copy(id = 2, top = 300, bottom = 900),
            app.copy(id = 2, application = false, bottom = 80),
        )) assertNull(windowCaptureCandidate(listOf(app, overlay), 1080, 2400))
    }
    @Test fun `cropped or split screen window cannot shift coordinates`() {
        assertNull(windowCaptureCandidate(listOf(app.copy(top = 80)), 1080, 2400))
        assertNull(windowCaptureCandidate(listOf(app.copy(right = 540), app.copy(id = 2, left = 540)), 1080, 2400))
    }
    @Test fun `empty inactive or system only windows fall back`() {
        assertNull(windowCaptureCandidate(emptyList(), 1080, 2400))
        assertNull(windowCaptureCandidate(listOf(app.copy(active = false)), 1080, 2400))
        assertNull(windowCaptureCandidate(listOf(app.copy(application = false)), 1080, 2400))
    }
    @Test fun `offscreen and empty windows do not obscure the app`() {
        assertEquals(1, windowCaptureCandidate(listOf(app, app.copy(id = 2, left = 1080, right = 1200), app.copy(id = 3, bottom = 0)), 1080, 2400))
    }
    @Test fun `rotated layout must match current display`() {
        assertNull(windowCaptureCandidate(listOf(app), 2400, 1080))
    }
}
