package lifeos.md.tapilot.chat

enum class RecordingGesture { Recording, Cancel, Transcribe }

internal fun recordingGesture(startY: Float, x: Float, y: Float, width: Float, density: Float): RecordingGesture {
    if (startY - y <= 72f * density) return RecordingGesture.Recording
    return if (x > width * .62f) RecordingGesture.Transcribe else RecordingGesture.Cancel
}
