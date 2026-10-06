package lifeos.md.tapilot.chat

import lifeos.md.tapilot.theme.LocalThemePalette
import lifeos.md.tapilot.theme.TapilotTheme
import lifeos.md.tapilot.theme.tapilotColors
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription


@Composable
fun ChatScreen(controller: ChatController) {
    val state = controller.state
    val colors = tapilotColors(state.dark, state.paletteId)
    TapilotTheme(state.dark, state.paletteId) {
      CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        var sessions by remember { mutableStateOf(false) }
        var attachments by remember { mutableStateOf(false) }
        val list = rememberLazyListState()
        val busy = state.running || controller.recording || controller.transcribing || controller.picking
        val zh = state.chinese
        fun label(chinese: String, english: String) = if (zh) chinese else english
        LaunchedEffect(state.activeConversationId, state.messages.size, state.messages.lastOrNull()?.text) {
            if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex)
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().background(colors.surfaceContainerLow).statusBarsPadding().height(56.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable(enabled = !busy) { sessions = true }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.conversations.find { it.id == state.activeConversationId }?.title ?: "Tapilot",
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.running) Text(label("正在处理…", "Working…"),
                                color = colors.onSurfaceVariant, fontSize = 12.sp)
                        }
                        ChatIcon("down", colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { controller.command("settings") }, enabled = !busy) {
                        ChatIcon("settings", colors.onSurface, label("设置", "Settings"))
                    }
                    IconButton(onClick = { controller.command("new") }, enabled = state.ready && !busy) {
                        ChatIcon("plus", colors.onSurface, label("新对话", "New conversation"))
                    }
                }
                if (state.needsAccessibility) {
                    TextButton(onClick = { controller.command("accessibility") }, modifier = Modifier.fillMaxWidth()) {
                        Text(label("设备控制未连接 · 前往设置", "Device control is off · Settings"), color = colors.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                if (!state.configured && state.ready) {
                    TextButton(onClick = { controller.command("settings") }, modifier = Modifier.fillMaxWidth()) {
                        Text(label("配置模型 API，开始对话", "Configure model API to start chatting"))
                    }
                }
                if (state.messages.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(36.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label("有什么需要帮忙？", "How can I help?"), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(10.dp))
                        Text(label("输入任务，或长按输入框说话", "Type a task, or hold the input to speak"),
                            color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(message, zh, controller, state.running)
                        }
                    }
                }
                state.progress?.let { TaskProgressView(it, controller, zh) }
                if (controller.error.isNotBlank()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(controller.error, color = colors.error, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Row {
                            if (controller.canRetrySpeech) TextButton(onClick = { controller.retrySpeech() }, enabled = !busy) {
                                Text(label("重试转写", "Retry transcription"))
                            }
                            TextButton(onClick = { controller.dismissError() }) { Text(label("关闭", "Dismiss")) }
                        }
                    }
                }
                if (controller.transcribing || controller.picking || !state.ready) {
                    Text(label(if (controller.picking) "正在读取附件…" else if (controller.transcribing) "正在转写…" else "正在加载…",
                        if (controller.picking) "Reading attachment…" else if (controller.transcribing) "Transcribing…" else "Loading…"),
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = colors.primary, fontSize = 13.sp)
                }
                state.attachment?.let { attachment ->
                    Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AttachmentThumbnail(attachment.preview, Modifier.clickable { controller.command("preview") })
                        Text(attachment.name, Modifier.weight(1f).clickable { controller.command("preview") }.padding(horizontal = 10.dp), fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { controller.command("removeAttachment") }, enabled = !busy) { Text(label("移除", "Remove")) }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
                ChatComposer(controller, onAttachments = { attachments = true })
            }
            run {
                RecordingFeedback(controller.willCancel, controller.willSend,
                    if (controller.transcribing) label("正在转文字…", "Transcribing…")
                    else if (controller.willCancel) label("松开取消", "Release to cancel")
                    else if (!controller.willSend) label("松手 填入输入框", "Release to insert text")
                    else label("松开发送", "Release to send"),
                    label("取消", "Cancel"), label("发送", "Send"),
                    controller.audioLevels, controller.recordingSeconds, controller.liveTranscript, controller.transcribing,
                    visible = controller.recording || controller.transcribing,
                    onCancel = controller::cancelRecording,
                    onSend = { controller.moveRecording(RecordingGesture.Send); controller.releaseRecording() })
            }
        }
        if (sessions) AlertDialog(onDismissRequest = { sessions = false },
            title = { Text(label("对话", "Conversations")) }, text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        Text(conversation.title, Modifier.fillMaxWidth().clickable {
                            controller.command("select", id = conversation.id); sessions = false
                        }.padding(vertical = 14.dp), color = if (conversation.id == state.activeConversationId) colors.primary else colors.onSurface)
                    }
                }
            }, confirmButton = { Row {
                if (state.advancedMode) TextButton(onClick = { sessions = false; controller.command("advanced") }) { Text("YAML") }
                TextButton(onClick = { sessions = false }) { Text(label("关闭", "Close")) }
            } })
        if (attachments) AlertDialog(onDismissRequest = { attachments = false }, title = { Text(label("添加附件", "Add attachment")) },
            text = { Column {
                listOf("photo" to label("照片", "Photos"), "camera" to label("拍摄", "Camera"), "file" to label("文件", "Files")).forEach { (kind, title) ->
                    TextButton(onClick = { attachments = false; controller.pick(kind) }, modifier = Modifier.fillMaxWidth()) { Text(title) }
                }
            } }, confirmButton = { TextButton(onClick = { attachments = false }) { Text(label("取消", "Cancel")) } })
        state.preview?.let { AttachmentPreview(it, zh) { controller.command("closePreview") } }
        state.confirmation?.let { instruction ->
            var revised by remember(instruction) { mutableStateOf(instruction) }
            AlertDialog(onDismissRequest = { controller.confirm(false, revised) }, title = { Text(label("确认手机操作", "Confirm phone operation")) },
                text = { OutlinedTextField(revised, { revised = it }, maxLines = 6) },
                confirmButton = { TextButton(onClick = { controller.confirm(true, revised) }, enabled = revised.isNotBlank()) { Text(label("执行", "Run")) } },
                dismissButton = { TextButton(onClick = { controller.confirm(false, revised) }) { Text(label("取消", "Cancel")) } })
        }
      }
    }
}

@Composable
private fun ChatComposer(controller: ChatController, onAttachments: () -> Unit) {
    val state = controller.state
    val colors = MaterialTheme.colorScheme
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<HoldToRecordEditor?>(null) }
    val enabled = state.ready && !state.running && !controller.transcribing && !controller.picking
    LaunchedEffect(controller.focusRequest) {
        if (controller.focusRequest > 0) {
            voiceMode = false
            editor?.post { editor?.let { field -> field.requestFocus(); field.context.getSystemService(InputMethodManager::class.java)
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) } }
        }
    }
    Row(Modifier.fillMaxWidth().background(colors.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { voiceMode = controller.draft.isEmpty() && !voiceMode; if (voiceMode) editor?.let { field ->
            field.clearFocus(); field.context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
        } }, enabled = enabled && !controller.recording, modifier = Modifier.size(48.dp)) {
            ChatIcon(if (voiceMode) "keyboard" else "mic", colors.onSurfaceVariant, if (state.chinese) "切换输入方式" else "Switch input mode")
        }
        AndroidView(modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 136.dp), factory = { context ->
            HoldToRecordEditor(context).apply {
                editor = this
                setTextSize(15f)
                setPadding((12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt())
                gravity = Gravity.CENTER_VERTICAL
                minLines = 1
                maxLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_SEND
                isLongClickable = true
                onHold = { _ ->
                    clearFocus()
                    context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
                    controller.startRecording()
                }
                onMove = controller::moveRecording
                onRelease = controller::releaseRecording
                onCancel = controller::cancelRecording
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        if (!voiceMode && s.toString() != controller.draft) controller.edit(s.toString())
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEND) { controller.send(); true } else false
                }
            }
        }, update = { field ->
            field.holdEnabled = enabled
            field.isEnabled = enabled || controller.recording
            field.voiceMode = voiceMode
            field.isFocusableInTouchMode = !voiceMode
            field.isFocusable = !voiceMode
            field.setTextColor(colors.onSurface.toArgb())
            field.setHintTextColor(colors.onSurfaceVariant.copy(alpha = .55f).toArgb())
            field.typingHint = if (state.chinese) "输入消息" else "Type a message"
            field.speechHint = if (state.chinese) "按住说话" else "Hold to speak"
            val desired = controller.draft
            if (field.text.toString() != desired) { field.setText(desired); field.setSelection(desired.length) }
            field.refreshHint()
            field.background = GradientDrawable().apply {
                cornerRadius = 18 * field.resources.displayMetrics.density
                setColor(lifeos.md.tapilot.theme.ThemePalettes.get(state.paletteId, state.dark).input)
                if (controller.recording) setStroke((field.resources.displayMetrics.density * 2).toInt(),
                    if (controller.willCancel) colors.error.toArgb() else colors.primary.toArgb())
                else setStroke(field.resources.displayMetrics.density.toInt().coerceAtLeast(1), colors.outline.toArgb())
            }
        })
        IconButton(onClick = onAttachments, enabled = enabled && !controller.recording, modifier = Modifier.size(48.dp)) {
            ChatIcon("plus", colors.onSurfaceVariant, if (state.chinese) "添加附件" else "Add attachment")
        }
        val canSend = state.running || (enabled && !controller.recording && (controller.draft.isNotBlank() || state.attachment != null))
        IconButton(onClick = { if (state.running) controller.command("stop") else controller.send() },
            enabled = canSend,
            modifier = Modifier.size(48.dp).background(if (canSend) colors.primary else colors.surfaceVariant, RoundedCornerShape(18.dp))) {
            ChatIcon(if (state.running) "stop" else "send", if (canSend) colors.onPrimary else colors.onSurfaceVariant.copy(alpha = .5f), if (state.running) "停止" else "发送")
        }
    }
}

@Composable
private fun ChatIcon(kind: String, color: Color, description: String? = null, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        val unit = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, point(x, y), point(x2, y2), 2f * unit, StrokeCap.Round)
        when (kind) {
            "plus" -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            "send" -> { line(12f, 19f, 12f, 5f); line(6f, 11f, 12f, 5f); line(18f, 11f, 12f, 5f) }
            "stop" -> drawRect(color, point(6f, 6f), androidx.compose.ui.geometry.Size(12 * unit, 12 * unit))
            "down" -> { line(7f, 9f, 12f, 14f); line(12f, 14f, 17f, 9f) }
            "mic" -> {
                drawRoundRect(color, point(9f, 3f), androidx.compose.ui.geometry.Size(6 * unit, 12 * unit),
                    androidx.compose.ui.geometry.CornerRadius(3 * unit), style = Stroke(2 * unit))
                val path = Path().apply { moveTo(5 * unit, 11 * unit); cubicTo(5 * unit, 22 * unit, 19 * unit, 22 * unit, 19 * unit, 11 * unit) }
                drawPath(path, color, style = Stroke(2 * unit)); line(12f, 19f, 12f, 22f)
            }
            "settings" -> {
                line(4f, 7f, 7f, 7f); line(13f, 7f, 20f, 7f)
                drawCircle(color, 3 * unit, point(10f, 7f), style = Stroke(2 * unit))
                line(4f, 17f, 11f, 17f); line(17f, 17f, 20f, 17f)
                drawCircle(color, 3 * unit, point(14f, 17f), style = Stroke(2 * unit))
            }
            "keyboard" -> {
                drawRoundRect(color, point(2f, 5f), androidx.compose.ui.geometry.Size(20 * unit, 14 * unit),
                    androidx.compose.ui.geometry.CornerRadius(2 * unit), style = Stroke(2 * unit))
                for (x in listOf(6f, 10f, 14f, 18f)) line(x, 9f, x, 10f)
                line(6f, 15f, 18f, 15f)
            }
        }
    }
}
