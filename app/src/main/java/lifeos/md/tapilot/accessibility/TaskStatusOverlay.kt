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
    private var gesturePassThrough = false

    fun show(hidden: Boolean) {
        view?.let { it.visibility = if (hidden) View.INVISIBLE else View.VISIBLE; return }
        val indicator = Indicator()
        val bounds = windows.currentWindowMetrics.bounds
        val size = dp(48) // Keep a usable touch target around the 14dp visual dot.
        val params = WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (gesturePassThrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (bounds.width() - size - dp(8)).coerceAtLeast(0)
            y = dp(8)
        }
        indicator.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        runCatching { windows.addView(indicator, params) }.onSuccess {
            view = indicator
        }
    }

    fun hide() {
        view?.let { runCatching { windows.removeView(it) } }
        view = null
    }

    fun setGesturePassThrough(enabled: Boolean) {
        gesturePassThrough = enabled
        val indicator = view ?: return
        val params = indicator.layoutParams as WindowManager.LayoutParams
        params.flags = if (enabled) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try {
            windows.updateViewLayout(indicator, params)
        } catch (error: Exception) {
            gesturePassThrough = false
            hide() // Never leave an inert input window if its flags cannot be restored.
            throw error
        }
    }

    fun setHidden(hidden: Boolean) {
        view?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    private inner class Indicator : View(service) {
        private val text = if (AppLanguage.resolved(service) == "zh") "Tapilot 正在处理 · 点击查看" else "Tapilot working · Tap to view"
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppTheme.colors(service).primary }
        init {
            contentDescription = text
            setOnClickListener { interrupt() }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(width / 2f, height / 2f, dp(7).toFloat(), fill)
        }
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).roundToInt()
}
