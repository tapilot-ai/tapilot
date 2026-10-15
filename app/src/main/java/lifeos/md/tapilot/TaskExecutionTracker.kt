package lifeos.md.tapilot

object TaskExecutionTracker {
    @Volatile
    var isActive: Boolean = false
        private set

    fun update(status: TaskExecutionStatus) {
        isActive = status.isActive
    }
}
