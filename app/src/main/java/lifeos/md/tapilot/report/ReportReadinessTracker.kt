package lifeos.md.tapilot.report

class ReportReadinessTracker(private val maxAttempts: Int) {
    enum class Decision { READY, RETRY, FAILED }

    data class Result(val decision: Decision, val errorMessage: String? = null)

    private var attempts = 0
    private var lastConsoleError: String? = null

    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }

    fun recordConsoleError(message: String) {
        lastConsoleError = message
    }

    fun evaluate(isReady: Boolean): Result {
        if (isReady) return Result(Decision.READY)
        attempts += 1
        if (attempts < maxAttempts) return Result(Decision.RETRY)
        val detail = lastConsoleError?.let { "\n\nJavaScript：$it" }.orEmpty()
        return Result(
            Decision.FAILED,
            "报告已加载，但页面渲染失败。$detail",
        )
    }
}
