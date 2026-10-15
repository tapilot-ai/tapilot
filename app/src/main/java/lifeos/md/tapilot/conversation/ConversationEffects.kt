package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.services.ServiceGateway
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Persist before external work. Cancellation is checked both before dispatch and after completion. */
internal class ConversationEffects(
    private val services: ServiceGateway,
    private val flush: () -> Unit,
    private val closed: () -> Boolean,
) {
    fun call(method: String, params: JSONObject, sessionId: String, stopped: AtomicBoolean, seconds: Long): Any? {
        checkActive(stopped)
        flush()
        checkActive(stopped)
        val result = CompletableFuture<Any?>()
        services.call(method, params, sessionId) { it.fold(result::complete, result::completeExceptionally) }
        return await(result, stopped, seconds)
    }
    fun <T> await(result: CompletableFuture<T>, stopped: AtomicBoolean, seconds: Long): T {
        val deadline = if (seconds > 0) System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds) else null
        while (true) {
            checkActive(stopped)
            try {
                val value = result.get(100, TimeUnit.MILLISECONDS)
                checkActive(stopped)
                return value
            } catch (timeout: TimeoutException) {
                if (deadline != null && System.nanoTime() >= deadline) throw IllegalStateException("请求超时，请稍后重试", timeout)
            }
        }
    }
    private fun checkActive(stopped: AtomicBoolean) = check(!stopped.get() && !closed()) { "任务已停止" }
}
