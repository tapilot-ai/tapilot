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
    private var processed = ""
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
        if (raw == processed) dirtyAt = now()
        raw = text; changedAt = now()
        display(render())
        schedule()
    }
    private fun render() = if (processed.isNotEmpty() && raw.startsWith(processed)) polished + raw.substring(processed.length) else raw
    private fun schedule() {
        scheduled?.cancel(false)
        if (cancelled || finishing || inFlight || raw.isBlank() || raw == processed) return
        val due = maxOf(startedAt + intervalMs, minOf(changedAt + pauseMs, dirtyAt + intervalMs))
        scheduled = executor.schedule({ preview() }, maxOf(0, due - now()), TimeUnit.MILLISECONDS)
    }
    private fun preview() {
        val text = synchronized(this) {
            if (cancelled || finishing) return
            inFlight = true; startedAt = now(); raw
        }
        val result = runCatching { polish(text) }.getOrDefault(text).ifBlank { text }
        synchronized(this) {
            inFlight = false
            if (cancelled || finishing) return
            processed = text; polished = result
            if (raw != text) dirtyAt = changedAt
            display(render()); schedule()
        }
    }
    @Synchronized fun finish(text: String, callback: (String) -> Unit) {
        if (cancelled || finishing) return
        finishing = true; scheduled?.cancel(false)
        raw = text
        display(render())
        executor.execute {
            val cached = synchronized(this) { if (cancelled) return@execute; if (text == processed) polished else "" }
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
