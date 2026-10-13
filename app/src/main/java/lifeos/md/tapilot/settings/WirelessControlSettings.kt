package lifeos.md.tapilot.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import lifeos.md.tapilot.wireless.WirelessControlService
import lifeos.md.tapilot.wireless.WirelessState

@Composable
internal fun WirelessControlSettings(context: Context, zh: Boolean) {
    fun label(a: String, b: String) = if (zh) a else b
    var status by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label("让电脑上的 ChatGPT 通过 Wi-Fi 查看屏幕、点击和输入。保持手机解锁；可随时从通知中停止。",
            "Let ChatGPT on your computer view and operate this phone over Wi-Fi. Keep the phone unlocked; stop at any time from the notification."), fontSize = 12.sp)
        Text(if (WirelessState.connected) label("已连接电脑", "Computer connected")
            else if (WirelessState.address.isNotEmpty()) label("等待连接", "Waiting for connection") + " · ${WirelessState.address}"
            else if (WirelessState.enabled) label("正在开启…", "Starting…") else label("未开启", "Off"))
        TextButton(onClick = {
            status = ""
            runCatching {
                if (WirelessState.enabled) context.stopService(Intent(context, WirelessControlService::class.java))
                else ContextCompat.startForegroundService(context, Intent(context, WirelessControlService::class.java))
            }.onFailure { status = it.message.orEmpty() }
        }) { Text(if (WirelessState.enabled) label("停止无线控制", "Stop wireless control") else label("开启无线控制", "Enable wireless control")) }
        if (WirelessState.address.isNotEmpty() && !WirelessState.connected) {
            TextButton(onClick = {
                runCatching {
                    val link = checkNotNull(WirelessControlService.current).pairingLink()
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tapilot pairing", link))
                    status = label("已复制。请在电脑的配对窗口粘贴，5 分钟内有效；不要发到聊天中。", "Copied. Paste into the computer's pairing prompt within 5 minutes, not into a chat.")
                }.onFailure { status = it.message.orEmpty() }
            }) { Text(label("复制配对链接", "Copy pairing link")) }
        }
        if (WirelessState.enabled) TextButton(onClick = { WirelessControlService.current?.revoke(); status = label("已撤销配对", "Pairing revoked") }) {
            Text(label("撤销电脑配对", "Revoke computer pairing"))
        }
        if (status.isNotEmpty()) Text(status, fontSize = 12.sp)
        if (WirelessState.error.isNotEmpty()) Text(WirelessState.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}
