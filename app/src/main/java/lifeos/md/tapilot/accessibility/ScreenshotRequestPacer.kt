package lifeos.md.tapilot.accessibility

/** Main-thread state. Activate only after Android actually reports a rate limit. */
internal class ScreenshotRequestPacer(private val intervalMs: Long = 350L) {
    private var lastRequest: Long? = null
    private var limited = false

    fun requested(now: Long) { lastRequest = now }
    fun rateLimited() { limited = true }
    fun delayMillis(now: Long): Long {
        if (!limited) return 0L
        val last = lastRequest ?: return 0L
        return (intervalMs - (now - last)).coerceIn(0L, intervalMs)
    }
}
