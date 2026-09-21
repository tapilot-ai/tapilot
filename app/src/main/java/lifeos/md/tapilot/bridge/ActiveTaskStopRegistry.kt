package lifeos.md.tapilot.bridge

internal object ActiveTaskStopRegistry {
    internal class Registration internal constructor(
        internal val stop: () -> Boolean,
    )

    private var active: Registration? = null

    @Synchronized
    fun register(stop: () -> Boolean): Registration {
        check(active == null) { "Another task is already registered" }
        return Registration(stop).also { active = it }
    }

    @Synchronized
    fun unregister(registration: Registration?) {
        if (active === registration) active = null
    }

    fun stopActive(): Boolean {
        val registration = synchronized(this) { active } ?: return false
        return registration.stop()
    }
}
