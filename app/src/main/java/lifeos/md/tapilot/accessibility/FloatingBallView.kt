package lifeos.md.tapilot.accessibility

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.R

/** Feedback begins on touch-down; the sweep reaches a full ring at the long-press threshold. */
internal class FloatingBallView(context: Context) : ImageView(context) {
    private var hold: ValueAnimator? = null
    private var progress = 0f
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppTheme.color(context, if (AppTheme.isDark(context)) R.color.brand_dark_primary else R.color.brand_primary)
        style = Paint.Style.STROKE
        strokeWidth = 3 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    fun beginHold(duration: Long) {
        endHold()
        animate().scaleX(.94f).scaleY(.94f).setDuration(120).start()
        hold = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    fun endHold() {
        hold?.cancel(); hold = null; progress = 0f
        animate().cancel(); scaleX = 1f; scaleY = 1f; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress > 0f) {
            val inset = ring.strokeWidth / 2 + resources.displayMetrics.density
            canvas.drawArc(inset, inset, width - inset, height - inset, -90f, 360f * progress, false, ring)
        }
    }
    override fun onDetachedFromWindow() { endHold(); super.onDetachedFromWindow() }
}
