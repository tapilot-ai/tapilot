package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.AppText.text as appText
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Snapshot mutable UI state, then serialize and fsync exclusively on a serial I/O worker. */
class ConversationWriter(
    private val store: ConversationStore,
    private val onTiming: (String, Long) -> Unit = { _, _ -> },
    private val onFailure: (Throwable) -> Unit,
) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var checkpointFailure: Throwable? = null
    @Volatile private var draftFailure: Throwable? = null
    private var previousSnapshot: Map<String, Any?>? = null

    @Synchronized fun save(value: JSONObject) {
        val snapshot = timed("snapshot") { ConversationSnapshot.capture(value, previousSnapshot ?: store.lastSnapshot) }
        enqueue(checkpoint = true) { timed("write") { store.saveSnapshot(snapshot) } }
        previousSnapshot = snapshot
    }
    fun saveDraft(text: String) = enqueue(checkpoint = false) { store.saveDraft(text) }
    private fun enqueue(checkpoint: Boolean, action: () -> Unit) {
        worker.execute {
            runCatching(action).fold({
                // A draft sidecar cannot repair a missing task/history checkpoint.
                if (checkpoint) checkpointFailure = null
                draftFailure = null
            }, {
                if (checkpoint) checkpointFailure = it else draftFailure = it
                onFailure(it)
            })
        }
    }
    /** Call off the UI thread before starting more external work. */
    fun flush() {
        timed("flush") { worker.submit {}.get(10, TimeUnit.SECONDS) }
        (checkpointFailure ?: draftFailure)?.let { throw IllegalStateException(appText("无法保存任务，请检查存储空间后重试", "Unable to save task. Check storage and try again"), it) }
    }
    private fun <T> timed(phase: String, action: () -> T): T {
        val started = System.nanoTime()
        try { return action() } finally {
            runCatching { onTiming(phase, (System.nanoTime() - started) / 1_000_000) }
        }
    }
    override fun close() { worker.shutdown() }

}
