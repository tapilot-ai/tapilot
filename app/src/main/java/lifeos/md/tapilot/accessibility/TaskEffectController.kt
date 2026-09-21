package lifeos.md.tapilot.accessibility

import android.graphics.PixelFormat
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import lifeos.md.tapilot.TaskEffect
import lifeos.md.tapilot.TaskExecutionStatus
import lifeos.md.tapilot.presentation

class TaskEffectController(
    private val service: MidsceneAccessibilityService,
    private val windowManager: WindowManager,
    private val handler: Handler,
) : AutoCloseable {
    class HiddenEffect internal constructor(
        internal val view: ExecutionEdgeEffectView?,
        internal val shouldRestore: Boolean,
    )

    private var effectView: ExecutionEdgeEffectView? = null
    private var effectUnavailable = false
    private var taskStatus = TaskExecutionStatus.IDLE
    private val resetStatus = Runnable { update(TaskExecutionStatus.IDLE) }
    private val clearPreview = Runnable {
        if (taskStatus == TaskExecutionStatus.IDLE) removeEffect()
    }

    fun update(status: TaskExecutionStatus, detail: String? = null) {
        handler.post {
            handler.removeCallbacks(resetStatus)
            taskStatus = status
            val presentation = status.presentation(detail)
            if (presentation.effect == TaskEffect.NONE) {
                removeEffect()
            } else {
                ensureEffect()?.setEffect(presentation.effect)
            }
            presentation.announcement?.let { announcement ->
                Toast.makeText(service, announcement, Toast.LENGTH_LONG).show()
                effectView?.announceForAccessibility(announcement)
            }
            presentation.resetAfterMillis?.let { handler.postDelayed(resetStatus, it) }
        }
    }

    fun hideForCapture(): HiddenEffect {
        val current = effectView
        val token = HiddenEffect(current, current?.visibility == View.VISIBLE)
        current?.visibility = View.INVISIBLE
        return token
    }

    fun restoreAfterCapture(hiddenEffect: HiddenEffect) {
        if (hiddenEffect.shouldRestore &&
            effectView === hiddenEffect.view &&
            taskStatus != TaskExecutionStatus.IDLE
        ) {
            hiddenEffect.view?.visibility = View.VISIBLE
        }
    }

    internal fun preview(settings: EdgeEffectGeometrySettings) {
        handler.post {
            handler.removeCallbacks(clearPreview)
            val view = ensureEffect() ?: return@post
            view.setGeometrySettings(settings)
            if (taskStatus == TaskExecutionStatus.IDLE) {
                view.setEffect(TaskEffect.ACTIVE)
                handler.postDelayed(clearPreview, PREVIEW_DURATION_MILLIS)
            }
        }
    }

    override fun close() {
        handler.removeCallbacks(resetStatus)
        removeEffect()
    }

    private fun ensureEffect(): ExecutionEdgeEffectView? {
        effectView?.let { return it }
        if (effectUnavailable) return null
        val view = ExecutionEdgeEffectView(service)
        view.setGeometrySettings(EdgeEffectPreferences.get(service))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
        }
        try {
            windowManager.addView(view, params)
        } catch (error: RuntimeException) {
            // This decoration must never terminate the task on devices that reject
            // an accessibility overlay token (notably some Android 16 ROMs).
            effectUnavailable = true
            Log.w(LOG_TAG, "Execution edge effect is unavailable", error)
            return null
        }
        effectView = view
        return view
    }

    private fun removeEffect() {
        effectView?.let { view ->
            view.setEffect(TaskEffect.NONE)
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(LOG_TAG, "Unable to remove execution effect", it) }
        }
        effectView = null
    }

    companion object {
        private const val LOG_TAG = "MidsceneTaskEffect"
        private const val PREVIEW_DURATION_MILLIS = 2_800L
    }
}
