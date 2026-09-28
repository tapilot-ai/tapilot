package lifeos.md.tapilot.accessibility

import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import lifeos.md.tapilot.MainActivity
import lifeos.md.tapilot.GlobalAssistantPanelActivity
import lifeos.md.tapilot.LaunchContext
import lifeos.md.tapilot.R
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class FloatingBallController(
    private val service: MidsceneAccessibilityService,
    private val windowManager: WindowManager,
) : AutoCloseable {
    private val size = (56 * service.resources.displayMetrics.density).roundToInt()
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appForeground = false
    private var taskRunning = false
    private var hiddenForCapture = false
    private var view: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var statusView: View? = null
    private var resultView: View? = null

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
        updateVisibility()
    }

    fun setEnabled(enabled: Boolean) {
        FloatingBallPreferences.setEnabled(service, enabled)
        updateVisibility()
    }

    fun setTaskRunning(running: Boolean) {
        taskRunning = running
        if (running) {
            resultView?.let { runCatching { windowManager.removeView(it) } }
            resultView = null
        }
        updateVisibility()
        if (running && !appForeground) showStatus() else hideStatus()
    }

    fun showResult(message: String) {
        hideStatus()
        resultView?.let { runCatching { windowManager.removeView(it) } }
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(service.getColor(R.color.brand_surface))
                setStroke(dp(1), service.getColor(R.color.brand_line))
            }
            elevation = dp(10).toFloat()
            addView(TextView(service).apply {
                text = "Tapilot 已回复"
                textSize = 14f
                setTextColor(service.getColor(R.color.brand_blue))
            })
            addView(TextView(service).apply {
                text = message.take(260)
                textSize = 14f
                setTextColor(service.getColor(R.color.brand_text))
                maxLines = 5
            })
            setOnClickListener {
                resultView?.let { view -> runCatching { windowManager.removeView(view) } }
                resultView = null
                openMainActivity()
            }
        }
        val params = WindowManager.LayoutParams(
            windowManager.currentWindowMetrics.bounds.width() - dp(32),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }
        runCatching { windowManager.addView(panel, params) }.onSuccess { resultView = panel }
    }

    fun hideForCapture(): Boolean {
        val wasVisible = view != null
        hiddenForCapture = true
        updateVisibility()
        return wasVisible
    }

    fun restoreAfterCapture(wasVisible: Boolean) {
        hiddenForCapture = false
        if (wasVisible) updateVisibility()
    }

    fun updatePosition() {
        val currentView = view ?: return
        val params = layoutParams ?: return
        applySavedPosition(params)
        windowManager.updateViewLayout(currentView, params)
    }

    private fun updateVisibility() {
        val shouldShow = FloatingBallPreferences.isEnabled(service) &&
            !appForeground && !taskRunning && !hiddenForCapture
        if (shouldShow && view == null) show() else if (!shouldShow && view != null) hide()
        if (taskRunning && !appForeground) showStatus() else hideStatus()
    }

    private fun showStatus() {
        if (statusView != null) return
        val label = TextView(service).apply {
            text = "Tapilot 正在处理…"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                colors = intArrayOf(
                    service.getColor(R.color.brand_blue),
                    service.getColor(R.color.brand_violet),
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }
        runCatching { windowManager.addView(label, params) }.onSuccess { statusView = label }
    }

    private fun hideStatus() {
        statusView?.let { runCatching { windowManager.removeView(it) } }
        statusView = null
    }

    private fun show() {
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        applySavedPosition(params)

        val ball = ImageView(service).apply {
            contentDescription = service.getString(R.string.floating_ball_accessibility_label)
            setImageResource(R.drawable.tapilot_icon_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(service.getColor(R.color.brand_soft))
                setStroke(dp(1), service.getColor(R.color.brand_line))
            }
            elevation = dp(8).toFloat()
            setOnClickListener { openTaskInput() }
            setOnTouchListener(DragTouchListener(params))
        }
        runCatching { windowManager.addView(ball, params) }
            .onSuccess {
                view = ball
                layoutParams = params
            }
    }

    private fun hide() {
        val current = view ?: return
        view = null
        layoutParams = null
        runCatching { windowManager.removeView(current) }
    }

    private fun applySavedPosition(params: WindowManager.LayoutParams) {
        val bounds = windowManager.currentWindowMetrics.bounds
        val (xFraction, yFraction) = FloatingBallPreferences.position(service)
        val (x, y) = FloatingBallPosition.coordinates(
            bounds.width(),
            bounds.height(),
            size,
            xFraction,
            yFraction,
        )
        params.x = x
        params.y = y
    }

    private fun persistPosition(params: WindowManager.LayoutParams) {
        val bounds = windowManager.currentWindowMetrics.bounds
        val (xFraction, yFraction) = FloatingBallPosition.fractions(
            bounds.width(),
            bounds.height(),
            size,
            params.x,
            params.y,
        )
        FloatingBallPreferences.setPosition(
            service,
            xFraction,
            yFraction,
        )
    }

    private fun openTaskInput() {
        val launched = AtomicBoolean(false)
        val fallback = Runnable {
            if (launched.compareAndSet(false, true)) {
                Log.w("TapilotFloatingBall", "Screen capture timed out; opening panel")
                openAssistantPanel()
            }
        }
        mainHandler.postDelayed(fallback, 2_200L)
        service.capture("launch-${UUID.randomUUID()}") { outcome ->
            mainHandler.post {
                if (!launched.compareAndSet(false, true)) return@post
                mainHandler.removeCallbacks(fallback)
                outcome.getOrNull()?.let { capture ->
                    LaunchContext.put(JSONObject()
                        .put("kind", "screen")
                        .put("label", "当前屏幕")
                        .put("dataUrl", capture.dataUrl))
                }
                openAssistantPanel()
            }
        }
    }

    private fun openAssistantPanel() {
        service.startActivity(
            Intent(service, GlobalAssistantPanelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun openMainActivity() {
        service.startActivity(Intent(service, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_FOCUS_TASK_INPUT, true)
        })
    }

    override fun close() {
        hide()
        hideStatus()
        resultView?.let { runCatching { windowManager.removeView(it) } }
        resultView = null
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).roundToInt()

    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(target: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    target.isPressed = true
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop ||
                            kotlin.math.abs(deltaY) > touchSlop)
                    ) {
                        dragging = true
                    }
                    if (dragging) {
                        val bounds = windowManager.currentWindowMetrics.bounds
                        params.x = (startX + deltaX.roundToInt())
                            .coerceIn(0, (bounds.width() - size).coerceAtLeast(0))
                        params.y = (startY + deltaY.roundToInt())
                            .coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
                        windowManager.updateViewLayout(target, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    target.isPressed = false
                    if (dragging) persistPosition(params) else target.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    target.isPressed = false
                    return true
                }
            }
            return false
        }
    }
}
