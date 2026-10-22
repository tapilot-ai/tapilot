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
    val stopUnconfirmed: Boolean get() = stopBarrier?.let { !it.isDone || it.isCompletedExceptionally } == true

    fun checkCanBegin() {
        check(!running) { "A conversation task is already running" }
        check(!stopUnconfirmed) { "The previous task has not confirmed stopping" }
    }

    fun begin(taskId: String, sessionId: String): AtomicBoolean {
        checkCanBegin()
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
    fun owns(sessionId: String): Boolean = running && this.sessionId == sessionId

    /** An explicit retry may recheck a failed stop, but never bypass an in-flight acknowledgement. */
    fun retryStop(): CompletableFuture<Any?>? {
        if (running || !stopUnconfirmed || stopBarrier?.isDone != true) return null
        return CompletableFuture<Any?>().also { stopBarrier = it }
    }

    /** A late completion from an older worker cannot settle a replacement task. */
    fun finish(sessionId: String): Boolean {
        if (!owns(sessionId)) return false
        running = false
        taskId = ""
        return true
    }
}
