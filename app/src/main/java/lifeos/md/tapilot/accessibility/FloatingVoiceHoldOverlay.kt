package lifeos.md.tapilot.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.voice.AssistantRuntime
import lifeos.md.tapilot.voice.VoicePhase

/** Vibelet's dim backdrop, speech bubble and bottom arc; release sends by default. */
internal class FloatingVoiceHoldOverlay(private val context: Context, private val windows: WindowManager) {
    private var view: HoldView? = null
    private val density get() = context.resources.displayMetrics.density
    fun isCancelTarget(x: Float, y: Float): Boolean {
        val bounds = windows.currentWindowMetrics.bounds
        return VoiceHoldTarget.isCancel(x, y, (view?.width ?: bounds.width()).toFloat(),
            (view?.height ?: bounds.height()).toFloat(), density)
    }
    fun refresh(runtime: AssistantRuntime) {
        if (view == null) {
            val surface = HoldView(context)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            if (runCatching { windows.addView(surface, params) }.isSuccess) view = surface
        }
        view?.runtime = runtime
        view?.invalidate()
    }
    fun hide() { view?.let { runCatching { windows.removeView(it) } }; view = null }

    private class HoldView(context: Context) : View(context) {
        var runtime: AssistantRuntime? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val density = resources.displayMetrics.density
        private fun dp(value: Float) = value * density
        override fun onDraw(canvas: Canvas) {
            val runtime = runtime ?: return
            val colors = AppTheme.colors(context)
            val zh = AppLanguage.resolved(context) == "zh"
            val cancel = runtime.floatingCancelSelected
            fun local(c: String, e: String) = if (zh) c else e
            fun text(value: String, x: Float, y: Float, size: Float, color: Int) {
                paint.color = color; paint.textSize = dp(size); paint.textAlign = Paint.Align.CENTER
                canvas.drawText(value, x, y, paint)
            }
            canvas.drawColor(0xC71B1223.toInt())
            val actionTop = height - minOf(dp(214f), height * .25f)
            val actionWidth = width * .49f
            val arcTop = height - minOf(dp(124f), height * .145f)
            paint.color = colors.primary; paint.alpha = 140
            val arc = RectF(-width * .8f, arcTop, width * 1.8f, arcTop + width * 2.6f)
            canvas.drawOval(arc, paint)
            paint.color = colors.contrast; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(3f)
            canvas.drawOval(arc, paint)
            paint.alpha = 255; paint.style = Paint.Style.FILL
            fun actionButton(box: RectF, rotation: Float, selected: Boolean, background: Int, foreground: Int, label: String) {
                canvas.save()
                canvas.rotate(rotation, box.centerX(), box.centerY())
                if (selected) canvas.scale(1.05f, 1.05f, box.centerX(), box.centerY())
                paint.color = background
                canvas.drawRoundRect(box, dp(44f), dp(44f), paint)
                if (selected) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f); paint.color = colors.contrast
                    canvas.drawRoundRect(box, dp(44f), dp(44f), paint)
                    paint.style = Paint.Style.FILL
                }
                paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                text(label, box.centerX(), box.centerY() + dp(6f), 17f, foreground)
                paint.typeface = android.graphics.Typeface.DEFAULT
                canvas.restore()
            }
            actionButton(RectF(-dp(12f), actionTop, actionWidth - dp(12f), actionTop + dp(72f)),
                -10f, cancel, if (cancel) colors.danger else colors.surface,
                if (cancel) colors.contrast else colors.text, local("取消", "Cancel"))
            actionButton(RectF(width - actionWidth + dp(12f), actionTop, width + dp(12f), actionTop + dp(72f)),
                10f, !cancel, colors.primary, colors.contrast, local("发送", "Send"))
            val bubbleWidth = width * .84f
            val content = runtime.floatingTranscript
            textPaint.color = colors.contrast; textPaint.textSize = dp(17f)
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, textPaint, (bubbleWidth - dp(40f)).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(dp(4f), 1f)
                .setMaxLines(8).setEllipsize(TextUtils.TruncateAt.END).build()
            val bubbleHeight = maxOf(dp(100f), layout.height + dp(40f))
            val bubble = RectF((width - bubbleWidth) / 2, height * .38f, (width + bubbleWidth) / 2, height * .38f + bubbleHeight)
            paint.color = colors.primary
            canvas.drawRoundRect(bubble, dp(20f), dp(20f), paint)
            if (content.isNotBlank()) {
                canvas.save(); canvas.translate(bubble.left + dp(20f), bubble.top + dp(20f)); layout.draw(canvas); canvas.restore()
            } else {
                paint.color = colors.contrast
                for (i in 0..6) {
                    val barHeight = dp(12f + ((i * 13) % 25) * (.3f + runtime.voiceState.level))
                    val x = bubble.centerX() + dp((i - 3) * 9f)
                    canvas.drawRoundRect(RectF(x, bubble.centerY() - barHeight / 2, x + dp(4f), bubble.centerY() + barHeight / 2), dp(2f), dp(2f), paint)
                }
            }
            text(if (runtime.voiceState.phase == VoicePhase.Listening) local("可以直接说话", "Speak now") else local("正在开启录音…", "Starting microphone…"), width / 2f, bubble.top - dp(22f), 16f, colors.contrast)
            text(if (cancel) local("松手取消", "Release to cancel") else local("松手发送 · 滑到取消区取消", "Release to send · Slide to Cancel"), width / 2f, bubble.bottom + dp(36f), 15f, colors.contrast)

        }
    }
}

internal object VoiceHoldTarget {
    fun isCancel(x: Float, y: Float, screenWidth: Float, screenHeight: Float, density: Float): Boolean {
        val width = screenWidth * .49f
        val top = screenHeight - minOf(214f * density, screenHeight * .25f)
        val centerX = -12f * density + width / 2
        val centerY = top + 36f * density
        val angle = Math.toRadians(10.0)
        val dx = x - centerX; val dy = y - centerY
        val localX = dx * kotlin.math.cos(angle) - dy * kotlin.math.sin(angle)
        val localY = dx * kotlin.math.sin(angle) + dy * kotlin.math.cos(angle)
        return kotlin.math.abs(localX) <= width / 2 + 8f * density &&
            kotlin.math.abs(localY) <= 44f * density
    }
}
