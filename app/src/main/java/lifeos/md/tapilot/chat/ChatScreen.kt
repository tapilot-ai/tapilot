package lifeos.md.tapilot.chat

import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF386CF2)
private val Purple = Color(0xFF7950E8)
private val Brand = Brush.horizontalGradient(listOf(Blue, Purple))

@Composable
fun ChatScreen(controller: ChatController) {
    val state = controller.state
    val colors = if (state.dark) darkColorScheme(primary = Blue, secondary = Purple,
        background = Color(0xFF111827), surface = Color(0xFF1D2638)) else lightColorScheme(
        primary = Blue, secondary = Purple, background = Color(0xFFF5F8FF), surface = Color.White)
    MaterialTheme(colorScheme = colors) {
        var sessions by remember { mutableStateOf(false) }
        var attachments by remember { mutableStateOf(false) }
        val list = rememberLazyListState()
        val busy = state.running || controller.recording || controller.transcribing || controller.picking
        val zh = state.chinese
        fun label(chinese: String, english: String) = if (zh) chinese else english
        LaunchedEffect(state.activeConversationId, state.messages.size, state.messages.lastOrNull()?.text) {
            if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex)
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(colors.surface).statusBarsPadding().height(60.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable(enabled = !busy) { sessions = true }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.conversations.find { it.id == state.activeConversationId }?.title ?: "Tapilot",
                                fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (state.running) label("正在执行…", "Working…") else label("你的手机 AI 助手", "Your phone AI assistant"),
                                color = colors.onSurfaceVariant, fontSize = 11.sp)
                        }
                        ChatIcon("down", colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { controller.command("settings") }, enabled = !busy) {
                        ChatIcon("settings", colors.onSurface, label("设置", "Settings"))
                    }
                    IconButton(onClick = { controller.command("new") }, enabled = state.ready && !busy) {
                        ChatIcon("plus", colors.primary, label("新对话", "New conversation"))
                    }
                }
                if (state.needsAccessibility) {
                    TextButton(onClick = { controller.command("settings") }, modifier = Modifier.fillMaxWidth()) {
                        Text(label("开启设备控制，才能帮你操作手机", "Enable device control to operate your phone"))
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
                        Box(Modifier.size(64.dp).background(Brand, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            Text("T", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(label("你好，我是 Tapilot", "Hi, I’m Tapilot"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Text(label("说说你想在手机上完成什么", "What would you like to do on your phone?"),
                            color = colors.onSurfaceVariant, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(message, zh) { controller.command("report", id = it) }
                        }
                    }
                }
                if (controller.error.isNotBlank()) {
                    TextButton(onClick = { controller.dismissError() }, modifier = Modifier.fillMaxWidth()) {
                        Text(controller.error, color = colors.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                        AttachmentThumbnail(attachment.preview)
                        Text(attachment.name, Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 13.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { controller.command("removeAttachment") }, enabled = !busy) { Text(label("移除", "Remove")) }
                    }
                }
                ChatComposer(controller, onAttachments = { attachments = true })
            }
            if (controller.recording) {
                RecordingFeedback(controller.willCancel,
                    label("正在录音", "Recording"),
                    if (controller.willCancel) label("松开取消", "Release to cancel")
                    else if (controller.sendOnRelease) label("松开发送 · 上滑取消", "Release to send · Slide up to cancel")
                    else label("松开转写 · 上滑取消", "Release to transcribe · Slide up to cancel"),
                    Modifier.align(Alignment.Center).offset(y = maxHeight * -.08f)
                        .width(minOf(maxWidth * .78f, 300.dp)))
            }
        }
        if (sessions) AlertDialog(onDismissRequest = { sessions = false },
            title = { Text(label("对话", "Conversations")) }, text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        Text(conversation.title, Modifier.fillMaxWidth().clickable {
                            controller.command("select", id = conversation.id); sessions = false
                        }.padding(vertical = 14.dp), color = if (conversation.id == state.activeConversationId) Blue else colors.onSurface)
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
        state.confirmation?.let { instruction ->
            var revised by remember(instruction) { mutableStateOf(instruction) }
            AlertDialog(onDismissRequest = { controller.confirm(false, revised) }, title = { Text(label("确认手机操作", "Confirm phone operation")) },
                text = { OutlinedTextField(revised, { revised = it }, maxLines = 6) },
                confirmButton = { TextButton(onClick = { controller.confirm(true, revised) }, enabled = revised.isNotBlank()) { Text(label("执行", "Run")) } },
                dismissButton = { TextButton(onClick = { controller.confirm(false, revised) }) { Text(label("取消", "Cancel")) } })
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, zh: Boolean, report: (String) -> Unit) {
    val user = message.role == "user"
    val colors = MaterialTheme.colorScheme
    var details by remember(message.id) { mutableStateOf(false) }
    val quotaFailure = !user && message.status == "failed" && message.text.contains("Arrearage", ignoreCase = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 320.dp)
            .background(if (user) Brush.horizontalGradient(listOf(Blue.copy(alpha = .11f), Purple.copy(alpha = .10f)))
                else Brush.horizontalGradient(listOf(colors.surface, colors.surface)), RoundedCornerShape(20.dp))
            .padding(16.dp)) {
            SelectionContainer {
                Text(if (!user && message.status == "running") (if (zh) "正在执行…" else "Working…")
                    else if (quotaFailure && !details) (if (zh) "模型账户欠费，暂时无法回复。请充值或在设置中选择可用的模型 API。"
                        else "The model account is overdue. Add credit or choose an available model API in settings.")
                    else message.text.ifBlank { if (zh) "任务已完成" else "Task complete" }, fontSize = 16.sp, lineHeight = 25.sp)
            }
            if (quotaFailure) TextButton(onClick = { details = !details }, contentPadding = PaddingValues(top = 8.dp)) {
                Text(if (details) (if (zh) "收起详情" else "Hide details") else (if (zh) "查看详情" else "Show details"))
            }
            if (message.reportId.isNotEmpty()) TextButton(onClick = { report(message.reportId) }, contentPadding = PaddingValues(top = 10.dp)) {
                Text(if (zh) "查看报告" else "View report")
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(preview: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, preview) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(preview.substringAfter(','), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), "", Modifier.size(48.dp)) }
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
    Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { voiceMode = !voiceMode; if (voiceMode) editor?.let { field ->
            field.clearFocus(); field.context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
        } }, enabled = enabled && !controller.recording, modifier = Modifier.size(44.dp)) {
            ChatIcon(if (voiceMode) "keyboard" else "mic", colors.primary, if (state.chinese) "切换输入方式" else "Switch input mode")
        }
        AndroidView(modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 136.dp), factory = { context ->
            HoldToRecordEditor(context).apply {
                editor = this
                setTextSize(16f)
                setPadding((12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(), (10 * resources.displayMetrics.density).toInt())
                gravity = Gravity.CENTER_VERTICAL
                minLines = 1
                maxLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_SEND
                isLongClickable = false
                onHold = { send ->
                    clearFocus()
                    context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
                    controller.startRecording(send)
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
            field.hint = if (state.chinese) "长按语音输入" else "Hold to speak"
            val desired = if (voiceMode) (if (state.chinese) "按住说话" else "Hold to talk") else controller.draft
            if (field.text.toString() != desired) { field.setText(desired); field.setSelection(desired.length) }
            field.background = GradientDrawable().apply {
                cornerRadius = 16 * field.resources.displayMetrics.density
                setColor(if (state.dark) 0xFF273349.toInt() else 0xFFF2F5FC.toInt())
                if (controller.recording) setStroke((field.resources.displayMetrics.density * 2).toInt(),
                    if (controller.willCancel) 0xFFCA4568.toInt() else Blue.toArgb())
            }
        })
        IconButton(onClick = onAttachments, enabled = enabled && !controller.recording, modifier = Modifier.size(44.dp)) {
            ChatIcon("plus", colors.primary, if (state.chinese) "添加附件" else "Add attachment")
        }
        IconButton(onClick = { if (state.running) controller.command("stop") else controller.send() },
            enabled = state.running || (enabled && !controller.recording && (controller.draft.isNotBlank() || state.attachment != null)),
            modifier = Modifier.size(44.dp).background(Brand, RoundedCornerShape(15.dp))) {
            ChatIcon(if (state.running) "stop" else "send", Color.White, if (state.running) "停止" else "发送")
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
                drawCircle(color, 7 * unit, style = Stroke(2 * unit)); drawCircle(color, 2.5f * unit, style = Stroke(2 * unit))
                line(12f, 2f, 12f, 5f); line(12f, 19f, 12f, 22f); line(2f, 12f, 5f, 12f); line(19f, 12f, 22f, 12f)
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
