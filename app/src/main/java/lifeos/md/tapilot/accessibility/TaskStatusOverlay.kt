package lifeos.md.tapilot.accessibility

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import kotlin.math.roundToInt

/** A task hint that settles into a small, tappable indicator after five seconds. */
internal class TaskStatusOverlay(
    private val service: MidsceneAccessibilityService,
    private val windows: WindowManager,
    private val interrupt: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var view: Indicator? = null
    private var animator: ValueAnimator? = null
    private val collapse = Runnable { animateCollapse() }

    fun show(hidden: Boolean) {
        view?.let { it.visibility = if (hidden) View.INVISIBLE else View.VISIBLE; return }
        val indicator = Indicator()
        val bounds = windows.currentWindowMetrics.bounds
        val width = indicator.expandedWidth.coerceAtMost(bounds.width() - dp(24))
        val params = WindowManager.LayoutParams(width, dp(40),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (bounds.width() - width) / 2
            y = dp(48)
        }
        indicator.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        runCatching { windows.addView(indicator, params) }.onSuccess {
            view = indicator
            handler.postDelayed(collapse, 5_000)
        }
    }

    fun hide() {
        handler.removeCallbacks(collapse)
        animator?.cancel(); animator = null
        view?.let { runCatching { windows.removeView(it) } }
        view = null
    }

    private fun animateCollapse() {
        val indicator = view ?: return
        val params = indicator.layoutParams as WindowManager.LayoutParams
        val startX = params.x; val startY = params.y
        val startWidth = params.width; val startHeight = params.height
        val targetSize = dp(48) // Keep a usable touch target around the visual dot.
        val targetX = (windows.currentWindowMetrics.bounds.width() - targetSize - dp(8)).coerceAtLeast(0)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (view !== indicator) return@addUpdateListener
                val fraction = it.animatedValue as Float
                fun mix(from: Int, to: Int) = (from + (to - from) * fraction).roundToInt()
                params.x = mix(startX, targetX); params.y = mix(startY, dp(8))
                params.width = mix(startWidth, targetSize); params.height = mix(startHeight, targetSize)
                indicator.fraction = fraction
                windows.updateViewLayout(indicator, params)
                indicator.invalidate()
            }
            start()
        }
    }

    private inner class Indicator : View(service) {
        private val text = if (AppLanguage.resolved(service) == "zh") "Tapilot 正在处理 · 点击打断" else "Tapilot working · Tap to interrupt"
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppTheme.colors(service).primary }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AppTheme.colors(service).contrast
            textSize = 13 * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }
        val expandedWidth = (ink.measureText(text) + dp(32)).roundToInt()
        var fraction = 0f
        init {
            contentDescription = text
            setOnClickListener { interrupt() }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        override fun onDraw(canvas: Canvas) {
            val diameter = dp(14).toFloat()
            val visualWidth = width + (diameter - width) * fraction
            val visualHeight = dp(40) + (diameter - dp(40)) * fraction
            val left = (width - visualWidth) / 2f
            val top = (height - visualHeight) / 2f
            val radius = dp(10) + (diameter / 2 - dp(10)) * fraction
            canvas.drawRoundRect(left, top, left + visualWidth, top + visualHeight, radius, radius, fill)
            ink.alpha = (255 * (1 - fraction * 3).coerceAtLeast(0f)).roundToInt()
            if (ink.alpha > 0) canvas.drawText(text, width / 2f, height / 2f - (ink.ascent() + ink.descent()) / 2, ink)
        }
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).roundToInt()
}
