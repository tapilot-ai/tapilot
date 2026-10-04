package lifeos.md.tapilot.chat

import org.junit.Assert.*
import org.junit.Test

class RecordingGestureTest {
    @Test fun onlyUnfocusedCompletelyEmptyEditorsAllowHolding() {
        assertTrue(canHoldToSpeak(true, false, ""))
        assertFalse(canHoldToSpeak(true, true, ""))
        assertFalse(canHoldToSpeak(false, false, ""))
        for (draft in listOf(" ", "\n", "\t", "文字")) assertFalse(canHoldToSpeak(true, false, draft))
    }
    @Test fun controlsAreTheOnlyActionTargetsAtEveryScreenSize() {
        for ((width, height) in listOf(360f to 740f, 393f to 873f, 430f to 932f)) {
            val layout = RecordingLayout(width, height)
            val y = layout.buttonTop + layout.buttonHeight / 2
            assertEquals(RecordingGesture.Cancel, layout.actionAt(layout.cancelLeft + layout.buttonWidth / 2, y))
            assertEquals(RecordingGesture.Send, layout.actionAt(layout.sendLeft + layout.buttonWidth / 2, y))
            assertEquals(RecordingGesture.Recording, layout.actionAt(width / 2, height - 40))
            assertEquals(RecordingGesture.Recording, layout.actionAt(width / 2, y))
            assertEquals(RecordingGesture.Recording, layout.actionAt(width * .9f, height * .4f))
        }
    }
    @Test fun pixelCoordinatesMatchTheDrawnDpControls() {
        val layout = RecordingLayout(393f, 873f)
        assertEquals(RecordingGesture.Send, recordingGesture((layout.sendLeft + layout.buttonWidth / 2) * 3,
            (layout.buttonTop + 36) * 3, 1179f, 2619f, 3f))
    }
}
