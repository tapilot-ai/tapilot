package lifeos.md.tapilot.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import kotlin.math.roundToInt

/** A compact task indicator, including its very first frame after window recreation. */
internal class TaskStatusOverlay(
    private val service: Context,
    private val windows: WindowManager,
    private val interrupt: () -> Unit,
) {
    private var view: Indicator? = null
    private var inputView: View? = null
    private var gesturePassThrough = false

    private fun params(touchable: Boolean): WindowManager.LayoutParams {
        val size = dp(48)
        return WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(8)
        }
    }

    fun show(hidden: Boolean) {
        if (view == null) {
            val indicator = Indicator()
            indicator.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            runCatching { windows.addView(indicator, params(false)) }.onSuccess { view = indicator }
        }
        setHidden(hidden)
        if (!gesturePassThrough) runCatching { showInput() }
    }

    private fun showInput() {
        val indicator = view ?: return
        if (inputView != null) return
        val target = View(service)
        target.visibility = indicator.visibility
        target.contentDescription = indicator.contentDescription
        target.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        target.setOnClickListener {
            // Ignore any queued event from an input window removed for injection.
            if (!gesturePassThrough && inputView === target) interrupt()
        }
        windows.addView(target, params(true))
        inputView = target
    }

    private fun removeInput() {
        val target = inputView ?: return
        inputView = null
        windows.removeViewImmediate(target)
    }

    fun hide() {
        runCatching { removeInput() }
        view?.let { runCatching { windows.removeViewImmediate(it) } }
        view = null
    }

    fun setGesturePassThrough(enabled: Boolean) {
        gesturePassThrough = enabled
        // Updating flags in place can race input routing on vendor launchers.
        // Remove the input window synchronously; the visual dot stays attached.
        if (enabled) removeInput() else showInput()
    }

    fun setHidden(hidden: Boolean) {
        val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        view?.visibility = visibility
        inputView?.visibility = visibility
    }

    private inner class Indicator : View(service) {
        private val text = if (AppLanguage.resolved(service) == "zh") "Tapilot 正在处理 · 点击查看" else "Tapilot working · Tap to view"
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppTheme.colors(service).primary }
        init {
            contentDescription = text
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(width / 2f, height / 2f, dp(7).toFloat(), fill)
        }
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).roundToInt()
}
