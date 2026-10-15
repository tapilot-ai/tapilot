package lifeos.md.tapilot

enum class TaskExecutionStatus {
    IDLE,
    STARTING,
    RUNNING,
    FINALIZING,
    STOPPING,
    WAITING_USER,
    SUCCEEDED,
    FAILED,
    STOPPED;

    val isActive: Boolean get() = this in setOf(STARTING, RUNNING, FINALIZING, STOPPING)
    val isTerminal: Boolean get() = this in setOf(WAITING_USER, SUCCEEDED, FAILED, STOPPED)
}

data class TaskStatusPresentation(
    val effect: TaskEffect,
    val announcement: String?,
    val resetAfterMillis: Long?,
)

enum class TaskEffect {
    NONE,
    ACTIVE,
    SUCCESS,
    FAILURE,
    STOPPED,
}

fun TaskExecutionStatus.presentation(detail: String? = null): TaskStatusPresentation = when (this) {
    TaskExecutionStatus.IDLE -> TaskStatusPresentation(TaskEffect.NONE, null, null)
    TaskExecutionStatus.STARTING -> TaskStatusPresentation(
        TaskEffect.ACTIVE,
        "Tapilot：任务开始执行",
        null,
    )
    TaskExecutionStatus.RUNNING -> TaskStatusPresentation(TaskEffect.ACTIVE, null, null)
    TaskExecutionStatus.FINALIZING -> TaskStatusPresentation(TaskEffect.ACTIVE, null, null)
    TaskExecutionStatus.STOPPING -> TaskStatusPresentation(TaskEffect.ACTIVE, null, null)
    TaskExecutionStatus.WAITING_USER -> TaskStatusPresentation(
        TaskEffect.NONE,
        "Tapilot：等待你补充信息",
        null,
    )
    TaskExecutionStatus.SUCCEEDED -> TaskStatusPresentation(
        TaskEffect.SUCCESS,
        "Tapilot：任务执行成功",
        2_500L,
    )
    TaskExecutionStatus.FAILED -> TaskStatusPresentation(
        TaskEffect.FAILURE,
        detail?.takeIf { it.isNotBlank() }?.let { "Tapilot：任务执行失败，$it" }
            ?: "Tapilot：任务执行失败",
        4_000L,
    )
    TaskExecutionStatus.STOPPED -> TaskStatusPresentation(
        TaskEffect.STOPPED,
        "Tapilot：任务已停止",
        2_500L,
    )
}
