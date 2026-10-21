package lifeos.md.tapilot.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ScrollView
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.R
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
    private var playbackToggle: ImageButton? = null
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
        val processing = !voice.previewingTranscript && error.isBlank() && confirmation == null &&
            (runtime.state.running || voice.phase in listOf(VoicePhase.Recognizing, VoicePhase.Thinking))
        if (processing && !wasProcessing) expandedProcessing = false
        wasProcessing = processing
        if (hidden) {
            setHidden(true)
            return
        }
        if (processing && !expandedProcessing) {
            hidePanel()
            processingIndicator.show(false)
            return
        }
        processingIndicator.hide()
        if (panel == null) show(runtime)
        panel?.visibility = View.VISIBLE
        title?.text = when {
            voice.previewingTranscript -> local("即将发送", "Sending shortly")
            error.isNotBlank() -> local("语音暂不可用", "Voice unavailable")
            confirmation != null -> local("等待你的确认", "Waiting for confirmation")
            voice.noSpeechRecognized -> local("回答尚未提交", "Reply not sent")
            else -> when (voice.phase) {
                VoicePhase.Listening -> local("● 可以直接说话", "● Speak now")
                VoicePhase.Recognizing -> local("正在识别…", "Transcribing…")
                VoicePhase.Thinking -> local("正在处理…", "Working…")
                VoicePhase.Speaking -> local("正在回答 · 点击打断", "Speaking · Tap to interrupt")
                VoicePhase.Paused -> local("收听已暂停", "Listening paused")
                VoicePhase.Error -> local("语音暂不可用", "Voice unavailable")
                else -> local("正在开启录音…", "Starting microphone…")
            }
        }
        transcriptArea?.visibility = if (runtime.floatingTranscript.isBlank()) View.GONE else View.VISIBLE
        transcript?.text = local("你：", "You: ") + runtime.floatingTranscript
        detail?.text = when {
            voice.previewingTranscript -> local("1 秒后自动发送", "Sends automatically in 1 second")
            error.isNotBlank() -> error
            confirmation != null -> confirmation
            voice.noSpeechRecognized -> local("未识别到语音，点击继续收听后再说一次", "No speech recognized. Tap Resume listening and try again.")
            voice.phase == VoicePhase.Listening -> if (runtime.floatingHold) local("按住说话，松手发送", "Hold to speak, release to send")
                else local("停顿后自动发送；3 秒无新文字也会发送", "Sends after a pause, or 3 seconds without new text")
            voice.phase == VoicePhase.Speaking -> voice.response.ifBlank { local("回复会直接播放", "Replies play aloud") }
            voice.phase == VoicePhase.Paused && voice.transcriptPending -> local("回答尚未发送，点击发送继续处理", "Reply not sent. Tap Send to continue.")
            voice.phase == VoicePhase.Paused && voice.listeningTimedOut -> local("已达到 30 秒收听上限，点击继续收听", "30-second listening limit reached. Tap Resume listening.")
            voice.phase == VoicePhase.Paused -> local("点击继续收听，可以回答追问或提出新任务", "Resume listening to answer or start a new task")
            else -> local("留在当前页面就能对话", "Talk without leaving this app")
        }
        playbackToggle?.apply {
            setImageResource(if (runtime.speechMuted) R.drawable.ic_speaker_off else R.drawable.ic_speaker_on)
            contentDescription = if (runtime.speechMuted) local("开启播报", "Enable speech") else local("关闭播报", "Mute speech")
            val colors = AppTheme.colors(context)
            imageTintList = android.content.res.ColorStateList.valueOf(if (runtime.speechMuted) colors.muted else colors.primary)
        }
        action?.text = when {
            confirmation != null -> local("确认", "Confirm")
            error.isNotBlank() -> local("重试", "Retry")
            voice.phase == VoicePhase.Paused -> if (voice.transcriptPending) local("发送", "Send") else local("继续收听", "Resume listening")
            voice.phase == VoicePhase.Listening -> if (voice.transcriptPending) local("发送", "Send") else local("暂停", "Pause")
            voice.phase == VoicePhase.Preparing -> local("暂停", "Pause")
            else -> local("说话", "Speak")
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
                else -> runtime.voicePrimaryAction()
            }
        }
        controls.addView(button(local("收起", "Collapse")) {
            expandedProcessing = false
            if (wasProcessing) { hidePanel(); processingIndicator.show(false) }
        }.apply { visibility = if (wasProcessing) View.VISIBLE else View.GONE })
        playbackToggle = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val ripple = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener {
                runtime.toggleSpeechPlayback()
                refresh(runtime, false)
            }
        }
        controls.addView(playbackToggle)
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

    /** Screenshot suppression must preserve the user's expansion state. */
    fun setHidden(hidden: Boolean) {
        panel?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        processingIndicator.setHidden(hidden)
    }

    private fun hidePanel() {
        panel?.let { runCatching { windows.removeView(it) } }
        panel = null; title = null; detail = null; action = null; playbackToggle = null; transcript = null; transcriptArea = null
    }
}
