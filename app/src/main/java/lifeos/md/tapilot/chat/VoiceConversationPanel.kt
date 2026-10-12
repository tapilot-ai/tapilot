package lifeos.md.tapilot.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lifeos.md.tapilot.voice.VoicePhase

@Composable
internal fun VoiceConversationPanel(controller: ChatController, connecting: Boolean = false) {
    val voice = if (connecting && !controller.voiceState.active) lifeos.md.tapilot.voice.VoiceState(active = true, phase = VoicePhase.Preparing) else controller.voiceState
    val colors = MaterialTheme.colorScheme
    val zh = controller.state.chinese
    fun label(a: String, b: String) = if (zh) a else b
    val title = when (voice.phase) {
        VoicePhase.Preparing -> label("正在连接，请稍候", "Connecting, please wait")
        VoicePhase.Listening -> label("可以说话了", "Ready — go ahead")
        VoicePhase.Recognizing -> label("正在转写", "Transcribing")
        VoicePhase.Thinking -> label("已收到，正在处理", "Received, working on it")
        VoicePhase.Speaking -> label("正在回答", "Speaking")
        VoicePhase.Paused -> label("语音已暂停", "Voice paused")
        VoicePhase.Error -> label("语音服务暂时不可用", "Voice service unavailable")
        VoicePhase.Idle -> label("语音对话", "Voice conversation")
    }
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainerLow).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (voice.phase == VoicePhase.Preparing) {
                CircularProgressIndicator(Modifier.size(40.dp), color = colors.primary, strokeWidth = 3.dp)
            } else {
                val live = voice.phase == VoicePhase.Listening || voice.phase == VoicePhase.Speaking
                val pulse = if (live) {
                    val animated by rememberInfiniteTransition(label = "voiceReady").animateFloat(
                        initialValue = .15f, targetValue = .45f,
                        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
                    animated
                } else .1f
                Canvas(Modifier.size(52.dp)) {
                    if (live) drawCircle(colors.primary.copy(alpha = pulse * .22f))
                    val unit = size.width / 40f
                    for (i in 0..4) {
                        val amplitude = if (voice.phase == VoicePhase.Listening) maxOf(voice.level, pulse)
                            else if (voice.phase == VoicePhase.Speaking) pulse + .25f else .1f
                        val height = (5f + amplitude * (if (i == 2) 24f else if (i % 2 == 1) 16f else 8f)) * unit
                        val x = (8f + i * 6f) * unit
                        drawLine(colors.primary, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 3f * unit, StrokeCap.Round)
                    }
                }
            }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        val detail = when {
            voice.error.isNotBlank() -> voice.error
            voice.phase == VoicePhase.Listening -> voice.transcript.ifBlank { label("我在听 · 说完停顿后自动发送", "Listening · Pause when finished to send") }
            voice.phase == VoicePhase.Speaking -> label("点击打断，接着说", "Tap interrupt to speak")
            voice.phase == VoicePhase.Paused -> if (controller.state.confirmation != null) label("请先确认手机操作", "Confirm the phone operation first") else label("点击继续，恢复对话", "Tap Resume to continue")
            voice.phase == VoicePhase.Preparing -> label("麦克风就绪后会提示可以说话", "Wait for the ready prompt before speaking")
            else -> voice.transcript
        }
        if (detail.isNotBlank()) Text(detail, color = if (voice.error.isNotBlank()) colors.error else colors.onSurfaceVariant, fontSize = 13.sp, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (voice.phase) {
                VoicePhase.Listening, VoicePhase.Preparing -> TextButton(onClick = controller::pauseVoice, enabled = !connecting) { Text(label("暂停", "Pause")) }
                VoicePhase.Error, VoicePhase.Paused -> TextButton(onClick = controller::resumeVoice,
                    enabled = controller.state.confirmation == null) { Text(label(if (voice.phase == VoicePhase.Error) "重试" else "继续", if (voice.phase == VoicePhase.Error) "Retry" else "Resume")) }
                else -> TextButton(onClick = controller::interruptVoice, enabled = controller.state.confirmation == null) { Text(label("打断", "Interrupt")) }
            }
            TextButton(onClick = controller::endVoiceConversation, enabled = !connecting) { Text(label("结束", "End"), color = colors.onSurfaceVariant) }
        }
    }
}
