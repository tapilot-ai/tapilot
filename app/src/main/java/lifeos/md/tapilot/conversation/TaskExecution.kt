package lifeos.md.tapilot.conversation

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** Main-thread transitions; cancellation and its acknowledgement are visible to the task worker. */
internal class TaskExecution {
    var taskId = ""; private set
    var sessionId = ""; private set
    var running = false; private set
    var cancellation = AtomicBoolean(false); private set
    @Volatile var stopBarrier: CompletableFuture<Any?>? = null; private set

    fun begin(taskId: String, sessionId: String): AtomicBoolean {
        check(!running) { "A conversation task is already running" }
        this.taskId = taskId
        this.sessionId = sessionId
        cancellation = AtomicBoolean(false)
        stopBarrier = null
        running = true
        return cancellation
    }
    fun requestStop(): CompletableFuture<Any?> {
        check(running)
        stopBarrier?.let { return it }
        return CompletableFuture<Any?>().also { stopBarrier = it; cancellation.set(true) }
    }
    /** A late completion from an older worker cannot settle a replacement task. */
    fun finish(sessionId: String): Boolean {
        if (!running || this.sessionId != sessionId) return false
        running = false
        taskId = ""
        return true
    }
}
