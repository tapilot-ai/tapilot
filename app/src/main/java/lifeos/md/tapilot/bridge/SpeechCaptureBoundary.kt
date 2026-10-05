package lifeos.md.tapilot.bridge

/** Finishing requests a drain; it must not discard a read already in flight. */
internal class SpeechCaptureBoundary(private var drained: Boolean = false) {
    @Volatile var finishing = false
        private set
    @Volatile var finalSent = false
        private set
    @Synchronized fun requestFinish() {
        check(!finishing) { "Transcription is already finishing" }
        finishing = true
    }
    @Synchronized fun acceptsAudio() = !drained
    @Synchronized fun captureDrained() { drained = true }
    @Synchronized fun claimFinal(): Boolean {
        if (!finishing || !drained || finalSent) return false
        finalSent = true
        return true
    }
}
