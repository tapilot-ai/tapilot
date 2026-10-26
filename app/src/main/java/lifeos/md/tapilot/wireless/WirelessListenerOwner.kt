package lifeos.md.tapilot.wireless

/** Main-thread ownership of listeners built asynchronously for changing networks. */
internal class WirelessListenerOwner<K, T : AutoCloseable> : AutoCloseable {
    var target: K? = null
        private set
    var listener: T? = null
        private set
    private var generation = 0L
    private var closed = false

    fun change(next: K?): Long? {
        if (closed || target == next) return null
        generation++
        listener?.close()
        listener = null
        target = next
        return generation
    }

    fun isCurrent(ticket: Long) = !closed && target != null && generation == ticket

    fun install(ticket: Long, value: T): Boolean {
        if (!isCurrent(ticket)) { value.close(); return false }
        listener = value
        return true
    }

    override fun close() {
        closed = true
        generation++
        listener?.close()
        listener = null
        target = null
    }
}
