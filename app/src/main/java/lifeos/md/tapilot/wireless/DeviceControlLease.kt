package lifeos.md.tapilot.wireless

/** Shared by the local bridge and wireless controller; a session cannot steal another's screen. */
internal object DeviceControlLease {
    private var owner: String? = null
    @Synchronized fun acquire(session: String) {
        check(owner == null) { "Device is already controlled by another session" }
        owner = session
    }
    @Synchronized fun checkAccess(session: String) {
        check(owner == null || owner == session) { "Wireless control is active. Disconnect it before starting a local task." }
    }
    @Synchronized fun release(session: String) { if (owner == session) owner = null }
}
