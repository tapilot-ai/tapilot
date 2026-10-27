package lifeos.md.tapilot.conversation

import org.json.JSONObject
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal enum class MessageLoadAction { PREVIEW, EDIT, RETRY }

/** Main-thread request ownership; canceled reads release the serial task worker and never publish. */
internal class ConversationMessageLoader(
    private val worker: Executor,
    private val post: (() -> Unit) -> Unit,
) {
    private class Request(val action: MessageLoadAction, val canceled: AtomicBoolean = AtomicBoolean())
    private var pending: Request? = null

    fun load(action: MessageLoadAction, read: (AtomicBoolean) -> JSONObject?, complete: (Result<JSONObject?>) -> Unit) {
        discard()
        val request = Request(action)
        pending = request
        worker.execute {
            if (request.canceled.get()) return@execute
            val result = runCatching { read(request.canceled) }
            post {
                if (pending === request && !request.canceled.get()) {
                    pending = null
                    complete(result)
                }
            }
        }
    }

    fun discard(action: MessageLoadAction? = null) {
        if (action == null || pending?.action == action) {
            pending?.canceled?.set(true)
            pending = null
        }
    }
}
