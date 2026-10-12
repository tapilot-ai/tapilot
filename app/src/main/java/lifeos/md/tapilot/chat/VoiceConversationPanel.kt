package lifeos.md.tapilot.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lifeos.md.tapilot.voice.VoicePhase

@Composable
internal fun VoiceConversationPanel(controller: ChatController) {
    val voice = controller.voiceState
    val colors = MaterialTheme.colorScheme
    val zh = controller.state.chinese
    fun label(a: String, b: String) = if (zh) a else b
    val title = when (voice.phase) {
        VoicePhase.Preparing -> label("正在连接麦克风", "Connecting microphone")
        VoicePhase.Listening -> label("我在听，你说", "I'm listening")
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
            Canvas(Modifier.size(32.dp)) {
                val unit = size.width / 32f
                for (i in 0..4) {
                    val amplitude = if (voice.phase == VoicePhase.Listening) voice.level else if (voice.phase == VoicePhase.Speaking) .55f else .1f
                    val height = (5f + amplitude * (if (i == 2) 24f else if (i % 2 == 1) 16f else 8f)) * unit
                    val x = (4f + i * 6f) * unit
                    drawLine(colors.primary, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 3f * unit, StrokeCap.Round)
                }
            }
            Text(title, fontSize = 16.sp)
        }
        val detail = when {
            voice.error.isNotBlank() -> voice.error
            voice.phase == VoicePhase.Listening -> voice.transcript.ifBlank { label("切到其他应用也能继续对答 · 停顿后发送", "Keep talking in other apps · Pause to send") }
            voice.phase == VoicePhase.Speaking -> label("点击打断，接着说", "Tap interrupt to speak")
            voice.phase == VoicePhase.Paused -> if (controller.state.confirmation != null) label("请先确认手机操作", "Confirm the phone operation first") else label("点击继续，恢复对话", "Tap Resume to continue")
            else -> voice.transcript
        }
        if (detail.isNotBlank()) Text(detail, color = if (voice.error.isNotBlank()) colors.error else colors.onSurfaceVariant, fontSize = 13.sp, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (voice.phase) {
                VoicePhase.Listening, VoicePhase.Preparing -> TextButton(onClick = controller::pauseVoice) { Text(label("暂停", "Pause")) }
                VoicePhase.Error, VoicePhase.Paused -> TextButton(onClick = controller::resumeVoice,
                    enabled = controller.state.confirmation == null) { Text(label(if (voice.phase == VoicePhase.Error) "重试" else "继续", if (voice.phase == VoicePhase.Error) "Retry" else "Resume")) }
                else -> TextButton(onClick = controller::interruptVoice, enabled = controller.state.confirmation == null) { Text(label("打断", "Interrupt")) }
            }
            TextButton(onClick = controller::endVoiceConversation) { Text(label("结束", "End"), color = colors.onSurfaceVariant) }
        }
    }
}
