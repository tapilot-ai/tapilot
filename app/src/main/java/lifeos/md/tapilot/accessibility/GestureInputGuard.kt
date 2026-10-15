package lifeos.md.tapilot.accessibility

/** Main-thread leases keep our own control overlay out of injected gestures. */
internal class GestureInputGuard(private val setPassThrough: (Boolean) -> Unit) {
    private var active = 0
    fun acquire(): AutoCloseable {
        if (active == 0) setPassThrough(true)
        active++
        var released = false
        return AutoCloseable {
            if (!released) {
                released = true
                active--
                if (active == 0) setPassThrough(false)
            }
        }
    }
}
