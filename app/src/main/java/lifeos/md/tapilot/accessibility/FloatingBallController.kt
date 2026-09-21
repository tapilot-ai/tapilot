package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import lifeos.md.tapilot.MainActivity
import lifeos.md.tapilot.R
import kotlin.math.roundToInt

class FloatingBallController(
    private val service: AccessibilityService,
    private val windowManager: WindowManager,
) : AutoCloseable {
    private val size = (56 * service.resources.displayMetrics.density).roundToInt()
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private var appForeground = false
    private var taskRunning = false
    private var hiddenForCapture = false
    private var view: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

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
        updateVisibility()
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
                setColor(Color.rgb(236, 235, 255))
                setStroke(dp(1), Color.argb(60, 78, 62, 230))
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
        service.startActivity(
            Intent(service, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(MainActivity.EXTRA_FOCUS_TASK_INPUT, true)
            },
        )
    }

    override fun close() {
        hide()
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
