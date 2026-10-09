package lifeos.md.tapilot.chat

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatComposer(controller: ChatController) {
    val state = controller.state
    val colors = MaterialTheme.colorScheme
    var attachments by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<HoldToRecordEditor?>(null) }
    val keyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(keyboardVisible) {
        // Return an empty editor to hold-to-record when the keyboard is dismissed.
        if (!keyboardVisible && controller.draft.isEmpty()) editor?.clearFocus()
    }
    val enabled = state.ready && !state.running && !controller.transcribing && !controller.picking
    LaunchedEffect(controller.focusRequest) {
        if (controller.focusRequest > 0) {
            editor?.post { editor?.let { field -> field.requestFocus(); field.context.getSystemService(InputMethodManager::class.java)
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) } }
        }
    }
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
        .clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainerLow)
        .border(1.dp, colors.outlineVariant, RoundedCornerShape(22.dp))
        .heightIn(min = 42.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            IconButton(onClick = { attachments = true }, enabled = enabled && !controller.recording,
                modifier = Modifier.size(44.dp)) {
                ChatIcon("plus", colors.onSurfaceVariant, if (state.chinese) "添加附件" else "Add attachment", Modifier.size(20.dp))
            }
            AttachmentDropdown(controller, attachments, onDismiss = { attachments = false })
        }
        AndroidView(modifier = Modifier.weight(1f).heightIn(min = 34.dp, max = 126.dp), factory = { context ->
            HoldToRecordEditor(context).apply {
                editor = this
                setTextSize(15f)
                setPadding((4 * resources.displayMetrics.density).toInt(), (7 * resources.displayMetrics.density).toInt(),
                    (4 * resources.displayMetrics.density).toInt(), (7 * resources.displayMetrics.density).toInt())
                gravity = Gravity.TOP
                minLines = 1
                maxLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_SEND
                isLongClickable = true
                onPrepare = controller::prepareRecording
                onDiscardPreparation = controller::discardPreparedRecording
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
                        if (s.toString() != controller.draft) controller.edit(s.toString())
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
            field.voiceMode = false
            field.isFocusableInTouchMode = true
            field.isFocusable = true
            field.setTextColor(colors.onSurface.toArgb())
            field.setHintTextColor(colors.onSurfaceVariant.copy(alpha = .55f).toArgb())
            field.typingHint = if (state.chinese) "输入消息" else "Type a message"
            field.speechHint = if (state.chinese) "长按语音输入" else "Hold to speak"
            val desired = controller.draft
            if (field.text.toString() != desired) { field.setText(desired); field.setSelection(desired.length) }
            field.refreshHint()
            field.background = GradientDrawable().apply {
                cornerRadius = 18 * field.resources.displayMetrics.density
                setColor(android.graphics.Color.TRANSPARENT)
                if (controller.recording) setStroke((field.resources.displayMetrics.density * 2).toInt(),
                    if (controller.willCancel) colors.error.toArgb() else colors.primary.toArgb())
            }
        })
        val canSend = state.running || (enabled && !controller.recording && (controller.draft.isNotBlank() || state.attachment != null))
        if (canSend) {
            IconButton(onClick = { if (state.running) controller.command("stop") else controller.send() },
                modifier = Modifier.size(44.dp)) {
                ChatIcon(if (state.running) "stop" else "send", colors.onSurface,
                    if (state.chinese) (if (state.running) "停止" else "发送") else (if (state.running) "Stop" else "Send"), Modifier.size(20.dp))
            }
        }
    }
}
