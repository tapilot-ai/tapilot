package lifeos.md.tapilot.chat

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ConversationDropdown(controller: ChatController, expanded: Boolean, onDismiss: () -> Unit) {
    val state = controller.state
    fun label(zh: String, en: String) = if (state.chinese) zh else en
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
        modifier = Modifier.width(280.dp).heightIn(max = 400.dp),
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 3.dp) {
        DropdownMenuItem(text = { Text(label("新对话", "New conversation")) }, enabled = state.ready,
            leadingIcon = { ChatIcon("plus", MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { onDismiss(); controller.command("new") })
        if (state.advancedMode) {
            DropdownMenuItem(text = { Text("YAML") }, onClick = { onDismiss(); controller.command("advanced") })
        }
        if (state.conversations.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        state.conversations.forEach { conversation ->
            val selected = conversation.id == state.activeConversationId
            DropdownMenuItem(text = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                trailingIcon = if (selected) ({ Text("✓", color = MaterialTheme.colorScheme.primary) }) else null,
                onClick = { onDismiss(); controller.command("select", id = conversation.id) })
        }
    }
}

@Composable
internal fun AttachmentDropdown(controller: ChatController, expanded: Boolean, onDismiss: () -> Unit) {
    val zh = controller.state.chinese
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 3.dp) {
        listOf("photo" to if (zh) "照片" else "Photos", "camera" to if (zh) "拍摄" else "Camera",
            "file" to if (zh) "文件" else "Files").forEach { (kind, title) ->
            DropdownMenuItem(text = { Text(title) }, onClick = { onDismiss(); controller.pick(kind) })
        }
    }
}
