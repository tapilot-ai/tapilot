package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.AppText.text as appText
import org.json.JSONArray
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
    @Volatile private var failure: Throwable? = null

    fun save(value: JSONObject) {
        val snapshot = timed("snapshot") { snapshotObject(value) }
        enqueue { timed("write") { store.saveSnapshot(snapshot) } }
    }
    fun saveDraft(text: String) = enqueue { store.saveDraft(text) }
    private fun enqueue(action: () -> Unit) {
        worker.execute {
            runCatching(action).fold({ failure = null }, { failure = it; onFailure(it) })
        }
    }
    /** Call off the UI thread before starting more external work. */
    fun flush() {
        timed("flush") { worker.submit {}.get(10, TimeUnit.SECONDS) }
        failure?.let { throw IllegalStateException(appText("无法保存任务，请检查存储空间后重试", "Unable to save task. Check storage and try again"), it) }
    }
    private fun <T> timed(phase: String, action: () -> T): T {
        val started = System.nanoTime()
        try { return action() } finally {
            runCatching { onTiming(phase, (System.nanoTime() - started) / 1_000_000) }
        }
    }
    override fun close() { worker.shutdown() }

    private fun snapshotObject(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { snapshot(value.get(it)) }
    private fun snapshot(value: Any?): Any? = when (value) {
        is JSONObject -> snapshotObject(value)
        is JSONArray -> (0 until value.length()).map { snapshot(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}
