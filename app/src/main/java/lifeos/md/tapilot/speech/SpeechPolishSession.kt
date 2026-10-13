package lifeos.md.tapilot.speech

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One serial queue per recording; ASR corrections never reuse an unrelated polished prefix. */
internal class SpeechPolishSession(
    private val polish: (String) -> String,
    private val display: (String) -> Unit,
    private val abort: () -> Unit = {},
    private val pauseMs: Long = 800,
    private val intervalMs: Long = 3000,
    private val completionDisplayMs: Long = 650,
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var raw = ""
    private var revision = 0L
    private var processedRevision = -1L
    private var polished = ""
    private var changedAt = 0L
    private var dirtyAt = 0L
    private var startedAt = Long.MIN_VALUE / 2
    private var finishing = false
    private var cancelled = false
    private var inFlight = false
    private var scheduled: ScheduledFuture<*>? = null
    private fun now() = System.nanoTime() / 1_000_000

    @Synchronized fun update(text: String) {
        if (cancelled || finishing || raw == text) return
        if (revision == processedRevision || raw.isEmpty()) dirtyAt = now()
        raw = text; revision++; changedAt = now()
        display(render())
        schedule()
    }
    private fun render() = if (revision == processedRevision) polished else raw
    private fun schedule() {
        scheduled?.cancel(false)
        if (cancelled || finishing || inFlight || raw.isBlank() || revision == processedRevision) return
        val due = maxOf(startedAt + intervalMs, minOf(changedAt + pauseMs, dirtyAt + intervalMs))
        scheduled = executor.schedule({ preview() }, maxOf(0, due - now()), TimeUnit.MILLISECONDS)
    }
    private fun preview() {
        val (text, requestRevision) = synchronized(this) {
            if (cancelled || finishing) return
            inFlight = true; startedAt = now(); raw to revision
        }
        val result = runCatching { polish(text) }.getOrDefault(text).ifBlank { text }
        synchronized(this) {
            inFlight = false
            // Finishing may reuse this request only when its ASR revision still matches.
            if (cancelled) return
            if (requestRevision == revision) {
                processedRevision = requestRevision; polished = result
                display(render())
            }
            schedule()
        }
    }
    @Synchronized fun finish(text: String, callback: (String) -> Unit) {
        if (cancelled || finishing) return
        finishing = true; scheduled?.cancel(false)
        if (raw != text) revision++
        raw = text
        display(render())
        executor.execute {
            val cached = synchronized(this) { if (cancelled) return@execute; if (revision == processedRevision) polished else "" }
            val result = cached.ifBlank { runCatching { polish(text) }.getOrDefault(text).ifBlank { text } }
            synchronized(this) {
                if (cancelled) return@execute
                display(result)
                // Let the complete bubble paint before the host closes it and fills the composer.
                scheduled = executor.schedule({
                    synchronized(this) {
                        if (!cancelled) {
                            try { callback(result) } finally { abort(); executor.shutdown() }
                        }
                    }
                }, completionDisplayMs, TimeUnit.MILLISECONDS)
            }
        }
    }
    @Synchronized fun cancel() {
        cancelled = true; scheduled?.cancel(false); abort(); executor.shutdownNow()
    }
}
