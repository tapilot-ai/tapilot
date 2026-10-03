package lifeos.md.tapilot.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(message: ChatMessage, zh: Boolean, controller: ChatController, busy: Boolean) {
    val user = message.role == "user"
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var menu by remember(message.id) { mutableStateOf(false) }
    var details by remember(message.id) { mutableStateOf(false) }
    var rerun by remember(message.id) { mutableStateOf(false) }
    val failed = message.status == "failed" || message.status == "stopped"
    val quotaFailure = !user && failed && message.text.contains("Arrearage", true)
    fun copy() {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tapilot", message.text))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = if (user) 300.dp else 560.dp)
            .background(if (user) colors.surfaceVariant else Color.Transparent, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = {}, onLongClick = { menu = true })
            .padding(horizontal = if (user) 14.dp else 0.dp, vertical = if (user) 12.dp else 4.dp)) {
            val text = if (!user && message.status == "running") (if (zh) "正在处理…" else "Working…")
                else if (!user && message.status == "stopped") (if (zh) "任务已停止" else "Task stopped")
                else if (quotaFailure && !details) (if (zh) "模型账户欠费，请充值或在设置中选择可用的模型 API。" else "The model account is overdue. Check your model settings.")
                else message.text.ifBlank { if (zh) "任务已结束" else "Task finished" }
            if (user) Text(text, fontSize = 16.sp, lineHeight = 24.sp)
            else SelectionContainer { Text(text, fontSize = 16.sp, lineHeight = 24.sp) }
            message.attachment?.let {
                TextButton(onClick = { controller.command("preview", id = message.entryId) }) {
                    Text((if (it.name == "当前屏幕") "引用：" else "附件：") + it.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!user && message.status != "running") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { copy() }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (zh) "复制" else "Copy", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    TextButton(onClick = { if (failed) controller.command("retry", id = message.entryId) else rerun = true },
                        enabled = !busy, contentPadding = PaddingValues(0.dp)) {
                        Text(if (zh) (if (failed) "重试" else "重新执行") else "Retry", fontSize = 12.sp)
                    }
                    if (message.reportId.isNotEmpty()) TextButton(onClick = { controller.command("report", id = message.reportId) }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (zh) "执行记录 →" else "Activity →", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (quotaFailure) TextButton(onClick = { details = !details }) { Text(if (details) "收起详情" else "查看详情") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(if (zh) "复制" else "Copy") }, onClick = { copy(); menu = false })
                if (user) DropdownMenuItem(text = { Text(if (zh) "编辑后重发" else "Edit and resend") }, enabled = !busy,
                    onClick = { menu = false; controller.command("editMessage", id = message.entryId) })
            }
        }
    }
    if (rerun) AlertDialog(onDismissRequest = { rerun = false }, title = { Text(if (zh) "重新执行？" else "Run again?") },
        text = { Text(if (zh) "将重新发送原消息和附件，手机操作可能再次执行。" else "Resend the original message and attachment. Phone actions may run again.") },
        confirmButton = { TextButton(onClick = { rerun = false; controller.command("retry", id = message.entryId) }) { Text(if (zh) "执行" else "Run") } },
        dismissButton = { TextButton(onClick = { rerun = false }) { Text(if (zh) "取消" else "Cancel") } })
}

@Composable
internal fun TaskProgressView(progress: ChatProgress, controller: ChatController, zh: Boolean) {
    var seconds by remember(progress.startedAt) { mutableStateOf(0L) }
    LaunchedEffect(progress.startedAt) {
        while (true) { seconds = ((System.currentTimeMillis() - progress.startedAt) / 1000).coerceAtLeast(0); delay(1000) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(progress.title + " · ${seconds}s", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { controller.command("stop") }, enabled = progress.title != "正在停止", contentPadding = PaddingValues(0.dp)) {
                Text(if (zh) "停止" else "Stop", fontSize = 13.sp)
            }
        }
        if (progress.detail.isNotBlank()) Text(progress.detail, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (progress.operation.isNotBlank()) Text("操作 ${progress.step} · ${progress.operation}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AttachmentThumbnail(preview: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, preview) {
        value = withContext(Dispatchers.IO) { runCatching {
            val bytes = Base64.decode(preview.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), "附件预览", modifier.size(48.dp)) }
}

@Composable
internal fun AttachmentPreview(attachment: ChatAttachment, zh: Boolean, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(attachment.name, Modifier.weight(1f).padding(vertical = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = close) { Text(if (zh) "关闭" else "Close") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                    if (attachment.pageCount > attachment.images.size) item { Text("共 ${attachment.pageCount} 页 · 当前提供前 ${attachment.images.size} 页", fontSize = 12.sp) }
                    attachment.images.forEachIndexed { index, image -> item { PreviewImage(image, index + 1) } }
                    if (attachment.text.isNotEmpty()) item { SelectionContainer { Text(attachment.text, fontSize = 15.sp, lineHeight = 23.sp) } }
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(data: String, page: Int) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, data) {
        value = withContext(Dispatchers.IO) { runCatching {
            val bytes = Base64.decode(data.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), "预览 $page", Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height).padding(vertical = 8.dp)) }
}
