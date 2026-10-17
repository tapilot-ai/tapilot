package lifeos.md.tapilot.accessibility

/** Main-thread state. Activate only after Android actually reports a rate limit. */
internal class ScreenshotRequestPacer(private val intervalMs: Long = 350L) {
    private var lastRequest: Long? = null
    private var limited = false

    fun requested(now: Long) { lastRequest = now }
    fun rateLimited() { limited = true }
    /** [preparationMs] must still elapse after this delay before the actual request. */
    fun delayMillis(now: Long, preparationMs: Long = 0L): Long {
        require(preparationMs >= 0L)
        if (!limited) return 0L
        val last = lastRequest ?: return 0L
        val remaining = (intervalMs - (now - last)).coerceIn(0L, intervalMs)
        return (remaining - preparationMs).coerceAtLeast(0L)
    }
}
