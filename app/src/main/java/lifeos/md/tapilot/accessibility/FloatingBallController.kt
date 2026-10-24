package lifeos.md.tapilot.accessibility

import lifeos.md.tapilot.AppText.text as appText
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
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.MainActivity
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
    private val holdDelayMillis = 280L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appForeground = false
    private var taskRunning = false
    private var hiddenForCapture = false
    private var view: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val taskStatus = TaskStatusOverlay(service, windowManager) { openAssistantPanel() }
    private var resultView: View? = null
    private var longPressAction: Runnable? = null
    private var voiceHint: View? = null
    private val runtime get() = (service.application as lifeos.md.tapilot.TapilotApplication).assistant
    private val holdOverlay = FloatingVoiceHoldOverlay(service, windowManager)
    private val voiceOverlay = FloatingVoiceOverlay(service, windowManager)
    private var voiceCaptureGeneration = -1
    private val updateVoice = object : Runnable {
        override fun run() {
            if (!runtime.floatingVoice) { voiceOverlay.hide(); holdOverlay.hide(); updateVisibility(); return }
            hideVoiceHint(); hideStatus(); hideResult()
            if (runtime.floatingInputVisible) {
                voiceOverlay.hide()
                holdOverlay.refresh(runtime)
                holdOverlay.setHidden(hiddenForCapture)
            } else {
                holdOverlay.hide()
                // Capture only after the input surface has finished. Capturing on release
                // hides accessibility overlays and makes the transcript disappear/reappear.
                if (runtime.voiceEntryPending && !runtime.floatingContextReady &&
                    runtime.voiceState.phase == lifeos.md.tapilot.voice.VoicePhase.Thinking &&
                    voiceCaptureGeneration != runtime.floatingEntryGeneration) {
                    voiceCaptureGeneration = runtime.floatingEntryGeneration
                    captureVoiceContext()
                }
                voiceOverlay.refresh(runtime, appForeground || hiddenForCapture)
            }
            mainHandler.postDelayed(this, 150)
        }
    }
    private val dismissVoiceHint = Runnable { hideVoiceHint() }
    private val openVoice = Runnable {
        service.startActivity(Intent(service, lifeos.md.tapilot.voice.VoiceEntryActivity::class.java)
            .putExtra("heldVoice", true)
            .putExtra("voiceEntryGeneration", runtime.floatingEntryGeneration)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val themePreferences = service.getSharedPreferences("midscene-preferences", 0)
    private val themeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme" || key == "theme.palette") mainHandler.post { refreshTheme() }
    }

    init {
        themePreferences.registerOnSharedPreferenceChangeListener(themeListener)
        if (runtime.floatingVoice) mainHandler.post(updateVoice)
    }

    fun refreshTheme() {
        val result = (resultView as? LinearLayout)?.let { panel -> (panel.getChildAt(1) as? TextView)?.text?.toString() }
        hide(); hideStatus(); updateVisibility()
        if (result != null) showResult(result)
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
        if (foreground) { hideResult(); hideVoiceHint(); mainHandler.removeCallbacks(openVoice) }
        updateVisibility()
    }

    fun setEnabled(enabled: Boolean) {
        FloatingBallPreferences.setEnabled(service, enabled)
        updateVisibility()
    }

    fun setTaskRunning(running: Boolean) {
        taskRunning = running
        if (running) hideResult()
        updateVisibility()
        if (running && !appForeground) showStatus() else hideStatus()
    }

    fun showResult(message: String) {
        if (appForeground || runtime.floatingVoice) return
        hideStatus()
        hideResult()
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(AppTheme.color(service, if (AppTheme.isDark(service)) R.color.brand_dark_surface else R.color.brand_surface))
                setStroke(dp(1), AppTheme.color(service, if (AppTheme.isDark(service)) R.color.brand_dark_line else R.color.brand_line))
            }
            elevation = dp(3).toFloat()
            addView(LinearLayout(service).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(service).apply {
                    text = appText("Tapilot 已回复 · 查看对话", "Tapilot replied · View conversation")
                    textSize = 14f
                    setTextColor(AppTheme.color(service, if (AppTheme.isDark(service)) R.color.brand_dark_primary else R.color.brand_primary))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(service).apply {
                    text = appText("关闭", "Close")
                    textSize = 13f
                    gravity = Gravity.CENTER
                    minWidth = dp(48); minHeight = dp(48)
                    setTextColor(AppTheme.color(service, if (AppTheme.isDark(service)) R.color.brand_dark_muted else R.color.brand_muted))
                    setOnClickListener { hideResult() }
                })
            })
            addView(TextView(service).apply {
                text = message.take(260)
                textSize = 14f
                setTextColor(AppTheme.color(service, if (AppTheme.isDark(service)) R.color.brand_dark_text else R.color.brand_text))
                maxLines = 5
            })
            setOnClickListener {
                hideResult()
                openAssistantPanel()
            }
        }
        val params = WindowManager.LayoutParams(
            windowManager.currentWindowMetrics.bounds.width() - dp(32),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }
        runCatching { windowManager.addView(panel, params) }
            .onSuccess { resultView = panel }
            .onFailure { Log.e("TapilotFloatingBall", "Unable to show assistant result", it) }
    }

    fun hideForCapture(): Boolean {
        val wasVisible = view != null
        hiddenForCapture = true
        resultView?.visibility = View.INVISIBLE
        voiceHint?.visibility = View.INVISIBLE
        voiceOverlay.setHidden(true)
        holdOverlay.setHidden(true)
        updateVisibility()
        return wasVisible
    }

    fun restoreAfterCapture(wasVisible: Boolean) {
        hiddenForCapture = false
        resultView?.visibility = View.VISIBLE
        voiceHint?.visibility = View.VISIBLE
        voiceOverlay.setHidden(appForeground)
        holdOverlay.setHidden(false)
        if (wasVisible || taskRunning) updateVisibility()
    }

    fun updatePosition() {
        val currentView = view ?: return
        val params = layoutParams ?: return
        applySavedPosition(params)
        windowManager.updateViewLayout(currentView, params)
    }

    private fun updateVisibility() {
        val shouldShow = runtime.floatingHold || (FloatingBallPreferences.isEnabled(service) &&
            !appForeground && !taskRunning && !hiddenForCapture)
        if (shouldShow && view == null) show() else if (!shouldShow && view != null) hide()
        if (taskRunning && !appForeground) showStatus() else hideStatus()
    }

    private fun showStatus() {
        if (!runtime.floatingVoice) taskStatus.show(hiddenForCapture)
    }

    internal fun setGesturePassThrough(enabled: Boolean) = taskStatus.setGesturePassThrough(enabled)

    private fun hideStatus() = taskStatus.hide()

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

        val ball = FloatingBallView(service).apply {
            contentDescription = service.getString(R.string.floating_ball_accessibility_label)
            setImageResource(R.drawable.tapilot_icon_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AppTheme.color(service, if (AppTheme.isDark(service)) R.color.brand_dark_soft else R.color.brand_soft))
                setStroke(dp(1), AppTheme.color(service, if (AppTheme.isDark(service)) R.color.brand_dark_line else R.color.brand_line))
            }
            elevation = dp(8).toFloat()
            setOnClickListener { openTaskInput() }
            setOnLongClickListener {
                runtime.beginFloatingHold()
                mainHandler.removeCallbacks(updateVoice)
                updateVoice.run()
                val generation = runtime.floatingEntryGeneration
                // Draw feedback before Activity startup / credential and microphone initialization.
                holdOverlay.afterDraw {
                    if (view != null && runtime.floatingVoice && runtime.floatingEntryGeneration == generation) openVoice.run()
                }
                true
            }
            setOnTouchListener(DragTouchListener(params))
        }
        runCatching { windowManager.addView(ball, params) }
            .onSuccess {
                view = ball
                layoutParams = params
            }
    }

    private fun hide() {
        cancelLongPress()
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

    private fun openTaskInput(voice: Boolean = false) {
        val launched = AtomicBoolean(false)
        val fallback = Runnable {
            if (launched.compareAndSet(false, true)) {
                Log.w("TapilotFloatingBall", "Screen capture timed out; opening panel")
                openAssistantPanel(voice)
            }
        }
        mainHandler.postDelayed(fallback, 2_200L)
        service.capture("launch-${UUID.randomUUID()}") { outcome ->
            mainHandler.post {
                if (!launched.compareAndSet(false, true)) return@post
                mainHandler.removeCallbacks(fallback)
                outcome.getOrNull()?.let { capture ->
                    LaunchContext.putPanel(JSONObject()
                        .put("kind", "screen")
                        .put("label", appText("当前屏幕", "Current screen"))
                        .put("dataUrl", capture.dataUrl))
                }
                openAssistantPanel(voice)
            }
        }
    }

    private fun captureVoiceContext() {
        val generation = runtime.floatingEntryGeneration
        val done = AtomicBoolean(false)
        val complete = Runnable {
            if (done.compareAndSet(false, true) && runtime.floatingVoice && generation == runtime.floatingEntryGeneration) {
                runtime.completeFloatingContext(generation)
            }
        }
        mainHandler.postDelayed(complete, 2_200)
        service.capture("held-voice-${UUID.randomUUID()}") { outcome ->
            mainHandler.post {
                if (done.get() || !runtime.floatingVoice || generation != runtime.floatingEntryGeneration) return@post
                outcome.getOrNull()?.let { capture ->
                    LaunchContext.put(JSONObject().put("kind", "screen").put("label", appText("当前屏幕", "Current screen"))
                        .put("dataUrl", capture.dataUrl).put("fromPanel", true).put("voice", true))
                }
                complete.run()
            }
        }
    }

    private fun openAssistantPanel(voice: Boolean = false) {
        LaunchContext.consumePanel()?.let { LaunchContext.put(it.put("fromPanel", true).put("voice", voice)) }
        service.startActivity(Intent(service, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_GLOBAL_PANEL, true)
        })
    }

    private fun hideVoiceHint() {
        mainHandler.removeCallbacks(dismissVoiceHint)
        voiceHint?.let { runCatching { windowManager.removeView(it) } }
        voiceHint = null
    }

    private fun hideResult() {
        resultView?.let { runCatching { windowManager.removeView(it) } }
        resultView = null
    }

    override fun close() {
        mainHandler.removeCallbacks(openVoice)
        mainHandler.removeCallbacks(updateVoice)
        voiceOverlay.hide()
        holdOverlay.hide()
        themePreferences.unregisterOnSharedPreferenceChangeListener(themeListener)
        hide()
        hideStatus()
        hideResult()
        hideVoiceHint()
    }

    private fun cancelLongPress() {
        longPressAction?.let(mainHandler::removeCallbacks)
        longPressAction = null
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
        private var longPressed = false

        override fun onTouch(target: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressed = false
                    cancelLongPress()
                    longPressAction = Runnable {
                        longPressAction = null
                        if (!dragging && target.isPressed) {
                            longPressed = true
                            target.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            target.performLongClick()
                        }
                    }.also { mainHandler.postDelayed(it, holdDelayMillis) }
                    target.isPressed = true
                    (target as? FloatingBallView)?.beginHold(holdDelayMillis)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressed) {
                        val selected = holdOverlay.isCancelTarget(event.rawX, event.rawY)
                        if (selected != runtime.floatingCancelSelected) {
                            runtime.selectFloatingCancel(selected)
                            target.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            holdOverlay.refresh(runtime)
                        }
                        return true
                    }
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop ||
                            kotlin.math.abs(deltaY) > touchSlop)
                    ) {
                        dragging = true
                        cancelLongPress()
                        (target as? FloatingBallView)?.endHold()
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
                    cancelLongPress()
                    (target as? FloatingBallView)?.endHold()
                    target.isPressed = false
                    if (longPressed) {
                        runtime.releaseFloatingHold(holdOverlay.isCancelTarget(event.rawX, event.rawY))
                        if (!runtime.floatingVoice) holdOverlay.hide()
                        updateVisibility()
                    } else if (dragging) persistPosition(params) else target.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (longPressed && runtime.floatingHold) { runtime.releaseFloatingHold(true); holdOverlay.hide() }
                    cancelLongPress()
                    (target as? FloatingBallView)?.endHold()
                    target.isPressed = false
                    return true
                }
            }
            return false
        }
    }
}
