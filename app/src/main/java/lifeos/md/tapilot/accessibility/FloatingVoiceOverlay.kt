package lifeos.md.tapilot.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.voice.AssistantRuntime
import lifeos.md.tapilot.voice.VoicePhase
import org.json.JSONObject

/** Persistent, non-focusable controls: the original app keeps its keyboard and touch input. */
internal class FloatingVoiceOverlay(private val context: Context, private val windows: WindowManager) {
    private var currentRuntime: AssistantRuntime? = null
    private var wasProcessing = false
    private var expandedProcessing = false
    private val processingIndicator: TaskStatusOverlay = TaskStatusOverlay(context, windows) {
        expandedProcessing = true
        processingIndicatorHide()
        currentRuntime?.let { refresh(it, false) }
    }
    private fun processingIndicatorHide(): Unit { processingIndicator.hide() }
    private var panel: LinearLayout? = null
    private var title: TextView? = null
    private var detail: TextView? = null
    private var action: TextView? = null
    private var transcript: TextView? = null
    private var transcriptArea: ScrollView? = null
    private val zh get() = AppLanguage.resolved(context) == "zh"
    private fun local(chinese: String, english: String) = if (zh) chinese else english
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun refresh(runtime: AssistantRuntime, hidden: Boolean) {
        currentRuntime = runtime
        val voice = runtime.voiceState
        val confirmation = runtime.state.confirmation
        val error = runtime.floatingVoiceError.ifBlank { voice.error }
        val processing = error.isBlank() && confirmation == null &&
            (runtime.state.running || voice.phase in listOf(VoicePhase.Recognizing, VoicePhase.Thinking))
        if (processing && !wasProcessing) expandedProcessing = false
        wasProcessing = processing
        if (hidden) {
            panel?.visibility = View.INVISIBLE
            processingIndicator.setHidden(true)
            return
        }
        if (processing && !expandedProcessing) {
            hidePanel()
            processingIndicator.show(false, 0)
            return
        }
        processingIndicator.hide()
        if (panel == null) show(runtime)
        panel?.visibility = View.VISIBLE
        title?.text = when {
            error.isNotBlank() -> local("语音暂不可用", "Voice unavailable")
            confirmation != null -> local("等待你的确认", "Waiting for confirmation")
            else -> when (voice.phase) {
                VoicePhase.Listening -> local("● 可以直接说话", "● Speak now")
                VoicePhase.Recognizing -> local("正在识别…", "Transcribing…")
                VoicePhase.Thinking -> local("正在处理…", "Working…")
                VoicePhase.Speaking -> local("正在回答 · 点击打断", "Speaking · Tap to interrupt")
                VoicePhase.Paused -> local("语音已暂停", "Voice paused")
                VoicePhase.Error -> local("语音暂不可用", "Voice unavailable")
                else -> local("正在开启录音…", "Starting microphone…")
            }
        }
        transcriptArea?.visibility = if (runtime.floatingTranscript.isBlank()) View.GONE else View.VISIBLE
        transcript?.text = local("你：", "You: ") + runtime.floatingTranscript
        detail?.text = when {
            error.isNotBlank() -> error
            confirmation != null -> confirmation
            voice.phase == VoicePhase.Listening -> local("我在听，说完停顿即可发送", "I'm listening. Pause when finished to send.")
            voice.phase == VoicePhase.Speaking -> voice.response.ifBlank { local("回复会直接播放", "Replies play aloud") }
            voice.phase == VoicePhase.Paused -> local("点击继续，即可接着说", "Tap Resume to continue")
            else -> local("留在当前页面就能对话", "Talk without leaving this app")
        }
        action?.text = when {
            confirmation != null -> local("确认", "Confirm")
            error.isNotBlank() -> local("重试", "Retry")
            voice.phase == VoicePhase.Paused -> local("继续", "Resume")
            voice.phase in listOf(VoicePhase.Speaking, VoicePhase.Thinking, VoicePhase.Recognizing) -> local("打断", "Interrupt")
            else -> local("暂停", "Pause")
        }
    }

    private fun show(runtime: AssistantRuntime) {
        val colors = AppTheme.colors(context)
        fun label(size: Float) = TextView(context).apply { textSize = size; setTextColor(colors.text) }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(8))
            background = GradientDrawable().apply {
                setColor(colors.surface); cornerRadius = dp(20).toFloat(); setStroke(dp(1), colors.line)
            }
            elevation = dp(6).toFloat()
        }
        title = label(17f).apply {
            setTextColor(colors.primary)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener { if (runtime.voiceState.phase == VoicePhase.Speaking) runtime.interruptVoice() }
        }
        detail = label(14f).apply { maxLines = 3; setPadding(0, dp(8), 0, dp(4)) }
        container.addView(title)
        transcript = label(15f).apply { setPadding(0, dp(8), 0, dp(4)) }
        transcriptArea = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(120), View.MeasureSpec.AT_MOST))
            }
        }.apply { addView(transcript); visibility = View.GONE }
        container.addView(transcriptArea)
        detail?.setTextColor(colors.muted)
        container.addView(detail)
        val controls = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        fun button(text: String, click: () -> Unit) = label(14f).apply {
            this.text = text; setTextColor(colors.primary); gravity = Gravity.CENTER
            minHeight = dp(48); minWidth = dp(64); setOnClickListener { click() }
        }
        action = button("") {
            when {
                runtime.state.confirmation != null -> runtime.command(JSONObject().put("action", "confirm").put("accepted", true).put("text", runtime.state.confirmation))
                runtime.floatingVoiceError.isNotBlank() -> {
                    context.startActivity(android.content.Intent(context, lifeos.md.tapilot.voice.VoiceEntryActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                runtime.voiceState.phase in listOf(VoicePhase.Paused, VoicePhase.Error) -> runtime.resumeVoice()
                runtime.voiceState.phase in listOf(VoicePhase.Speaking, VoicePhase.Thinking, VoicePhase.Recognizing) -> runtime.interruptVoice()
                else -> runtime.pauseVoice()
            }
        }
        controls.addView(button(local("收起", "Collapse")) {
            expandedProcessing = false
            if (wasProcessing) { hidePanel(); processingIndicator.show(false, 0) }
        }.apply { visibility = if (wasProcessing) View.VISIBLE else View.GONE })
        controls.addView(action)
        controls.addView(button(local("结束", "End")) { runtime.endVoice(); hide() })
        container.addView(controls)
        val params = WindowManager.LayoutParams(
            (windows.currentWindowMetrics.bounds.width() - dp(32)).coerceAtMost(dp(360)),
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }
        if (runCatching { windows.addView(container, params) }.isSuccess) panel = container
    }

    fun hide() {
        processingIndicator.hide()
        currentRuntime = null; wasProcessing = false; expandedProcessing = false
        hidePanel()
    }

    private fun hidePanel() {
        panel?.let { runCatching { windows.removeView(it) } }
        panel = null; title = null; detail = null; action = null; transcript = null; transcriptArea = null
    }
}
