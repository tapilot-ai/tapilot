package lifeos.md.tapilot.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lifeos.md.tapilot.conversation.NativeConversationCoordinator

@Composable
internal fun ChatGptSettingsSection(coordinator: NativeConversationCoordinator, zh: Boolean) {
    val connection = coordinator.chatGpt
    val context = LocalContext.current
    fun label(a: String, b: String) = if (zh) a else b
    var accountPicker by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf(false) }
    val busy = connection.busy || coordinator.settingsBusy
    Text(label("使用 ChatGPT 套餐处理对话与任务规划。手机操作继续使用上方配置的 Midscene 模型。",
        "Use your ChatGPT plan for conversation and task planning. Phone operations use the Midscene model configured above."), fontSize = 13.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label("用于对话与任务规划", "Use for conversation & planning"), modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(coordinator.data.optBoolean("chatgptConversation"), { coordinator.preference("chatgptConversation", it) },
            enabled = !busy && (connection.ready || coordinator.data.optBoolean("chatgptConversation")))
    }
    if (connection.accounts.isNotEmpty()) {
        TextButton(onClick = { accountPicker = true }, enabled = !busy) {
            Text(connection.accounts.firstOrNull { it.id == connection.activeId }?.label ?: label("选择账号", "Choose account"))
        }
        if (connection.accounts.any { it.id == connection.activeId && it.signedIn }) {
            TextButton(onClick = { if (connection.models.isEmpty()) connection.refreshModels() else modelPicker = true }, enabled = !busy) {
                Text(connection.models.firstOrNull { it.id == connection.model }?.label ?: connection.model.ifBlank { label("加载模型", "Load models") })
            }
            Row {
                TextButton(onClick = connection::refreshModels, enabled = !busy) { Text(label("刷新模型", "Refresh models")) }
                TextButton(onClick = coordinator::testChatGpt, enabled = !busy && connection.ready) { Text(label("检查连接", "Check connection")) }
                TextButton(onClick = connection::signOut, enabled = !busy) { Text(label("退出登录", "Sign out")) }
            }
        }
        TextButton(onClick = { connection.signIn(connection.activeId) }, enabled = !busy) { Text(label("重新登录当前账号", "Sign in again")) }
    }
    Button(onClick = { connection.signIn() }, enabled = !busy) { Text("Continue with ChatGPT") }
    if (connection.signingIn) TextButton(onClick = connection::cancelLogin) { Text(label("取消登录", "Cancel sign-in")) }
    if (connection.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (connection.status.isNotBlank()) Text(connection.status, fontSize = 13.sp)
    if (coordinator.settingsSection == "chatgpt" && coordinator.settingsStatus.isNotBlank()) Text(coordinator.settingsStatus, fontSize = 13.sp)
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage"))) }) {
        Text(label("管理 ChatGPT 用量与授权", "Manage ChatGPT usage & access"))
    }
    if (accountPicker || modelPicker) AlertDialog(onDismissRequest = { accountPicker = false; modelPicker = false },
        title = { Text(label(if (accountPicker) "选择账号" else "选择模型", if (accountPicker) "Choose account" else "Choose model")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (accountPicker) connection.accounts.forEach { account ->
                TextButton(onClick = { connection.selectAccount(account.id); accountPicker = false }) { Text(account.label) }
            } else connection.models.forEach { model ->
                TextButton(onClick = { connection.selectModel(model.id); modelPicker = false }) { Text(model.label) }
            }
        } }, confirmButton = { TextButton(onClick = { accountPicker = false; modelPicker = false }) { Text(label("关闭", "Close")) } })
}
