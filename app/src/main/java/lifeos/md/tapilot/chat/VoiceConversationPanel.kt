package lifeos.md.tapilot.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import lifeos.md.tapilot.R
import lifeos.md.tapilot.voice.VoicePhase

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceConversationPanel(controller: ChatController, connecting: Boolean = false) {
    val voice = if (connecting && !controller.voiceState.active) lifeos.md.tapilot.voice.VoiceState(active = true, phase = VoicePhase.Preparing) else controller.voiceState
    val colors = MaterialTheme.colorScheme
    val zh = controller.state.chinese
    fun label(a: String, b: String) = if (zh) a else b
    val reviewing = controller.state.confirmation == null && voice.error.isBlank() &&
        (voice.previewingTranscript || (voice.phase == VoicePhase.Paused && voice.transcriptPending))
    val title = if (reviewing) label("核对文字", "Review transcript") else when (voice.phase) {
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
            voice.previewingTranscript -> label("即将发送 · 点文字或修改可编辑", "Sending shortly · Tap the text or Edit to change it")
            reviewing && voice.editingTranscript -> label("已停止自动发送，修改后点击发送", "Auto-send stopped. Edit, then tap Send")
            reviewing -> label("文字尚未发送，可以修改或发送", "Not sent yet. Edit or send the text")
            voice.error.isNotBlank() -> voice.error
            voice.noSpeechRecognized -> label("未识别到语音，回答尚未提交。请点击继续收听", "No speech recognized. Your reply was not sent. Tap Resume listening")
            voice.phase == VoicePhase.Listening -> voice.transcript.ifBlank {
                if (controller.floatingHold) label("按住说话，松手后核对文字", "Hold to speak, release to review")
                else label("停顿后自动发送；3 秒无新文字也会发送", "Sends after a pause, or 3 seconds without new text")
            }
            voice.phase == VoicePhase.Speaking -> label("点击说话可打断播报", "Tap Speak to interrupt playback")
            voice.followUpListeningExpired -> label("30 秒内没有新指令，麦克风已关闭。上一条任务结果见上方", "No new instruction for 30 seconds. Microphone off. See the previous result above")
            voice.phase == VoicePhase.Paused && voice.listeningTimedOut -> label("已达到 30 秒收听上限，麦克风已关闭。点击继续收听", "30-second listening limit reached. Microphone off. Tap Resume listening") + voice.transcript.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
            voice.phase == VoicePhase.Paused -> if (controller.state.confirmation != null) label("请先确认手机操作", "Confirm the phone operation first") else label("点击继续收听，可以回答追问或提出新任务", "Resume listening to answer or start a new task") + if (voice.transcriptPending) "\n${voice.transcript}" else ""
            voice.phase == VoicePhase.Preparing -> label("麦克风就绪后会提示可以说话", "Wait for the ready prompt before speaking")
            else -> voice.transcript
        }
        if (reviewing) {
            if (voice.editingTranscript) {
                val focus = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
                OutlinedTextField(value = voice.transcript, onValueChange = controller::editVoiceTranscript,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).focusRequester(focus), minLines = 2, maxLines = 6,
                    label = { Text(label("语音文字", "Transcript")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { controller.voicePrimaryAction() }))
            } else {
                Text(voice.transcript, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState())
                        .clickable(onClickLabel = label("修改文字", "Edit transcript"), onClick = controller::beginVoiceTranscriptEdit)
                        .padding(8.dp))
            }
        }
        if (detail.isNotBlank()) Text(detail, color = if (voice.error.isNotBlank()) colors.error else colors.onSurfaceVariant, fontSize = 13.sp, maxLines = if (reviewing) 4 else 3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (reviewing && !voice.editingTranscript) TextButton(onClick = controller::beginVoiceTranscriptEdit) { Text(label("修改", "Edit")) }
            TextButton(onClick = controller::voicePrimaryAction,
                enabled = !connecting && controller.state.confirmation == null && (!reviewing || voice.transcript.isNotBlank()) &&
                    (voice.phase != VoicePhase.Recognizing || voice.previewingTranscript)) {
                Text(if (voice.previewingTranscript) label("立即发送", "Send now") else when (voice.phase) {
                    VoicePhase.Listening -> if (voice.transcriptPending) label("发送", "Send") else label("暂停", "Pause")
                    VoicePhase.Preparing -> label("暂停", "Pause")
                    VoicePhase.Paused -> if (voice.transcriptPending) label("发送", "Send") else label("继续收听", "Resume listening")
                    VoicePhase.Error -> label("重试", "Retry")
                    VoicePhase.Recognizing -> label("正在转写", "Transcribing")
                    else -> label("说话", "Speak")
                })
            }
            TextButton(onClick = controller::endVoiceConversation, enabled = !connecting) { Text(if (reviewing) label("取消", "Cancel") else label("结束", "End"), color = colors.onSurfaceVariant) }
        }
    }
}

/** Shared state remains available from the chat header even outside voice mode. */
@Composable
internal fun SpeechPlaybackToggle(controller: ChatController) {
    val colors = MaterialTheme.colorScheme
    fun label(chinese: String, english: String) = if (controller.state.chinese) chinese else english
    IconToggleButton(checked = !controller.speechMuted,
        onCheckedChange = { controller.toggleSpeechPlayback() },
        modifier = Modifier.size(48.dp)) {
        Icon(painterResource(if (controller.speechMuted) R.drawable.ic_speaker_off else R.drawable.ic_speaker_on),
            contentDescription = if (controller.speechMuted) label("开启播报", "Enable speech") else label("关闭播报", "Mute speech"),
            tint = if (controller.speechMuted) colors.onSurfaceVariant else colors.primary,
            modifier = Modifier.size(24.dp))
    }
}
