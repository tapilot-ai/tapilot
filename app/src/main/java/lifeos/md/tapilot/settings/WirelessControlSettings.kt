package lifeos.md.tapilot.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    var pairingBusy by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { qr ->
            pairingBusy = true
            status = label("正在向电脑发送配对信息…", "Sending pairing information…")
            runCatching {
                checkNotNull(WirelessControlService.current).pairComputer(qr) { outcome ->
                    pairingBusy = false
                    status = outcome.fold({ label("已发送，请在电脑配对窗口确认连接。", "Sent. Confirm on the computer's pairing window.") }, { it.message.orEmpty() })
                }
            }.onFailure { pairingBusy = false; status = it.message.orEmpty() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label("让电脑上的 ChatGPT 通过 Wi-Fi 查看屏幕、点击和输入。保持手机解锁；可随时从通知中停止。",
            "Let ChatGPT on your computer view and operate this phone over Wi-Fi. Keep the phone unlocked; stop at any time from the notification."), fontSize = 12.sp)
        Text(label("设备控制开启路径：系统辅助功能 → 已安装／已下载的应用 → Tapilot 设备控制 → 打开开关并确认，再返回此页。",
            "Enable device control: Accessibility → Installed/Downloaded apps → Tapilot device control → Enable and confirm, then return here."), fontSize = 12.sp)
        TextButton(onClick = {
            android.widget.Toast.makeText(context, label("找到 Tapilot 设备控制，打开开关并确认，再返回 Tapilot", "Enable Tapilot device control, confirm, then return to Tapilot"), android.widget.Toast.LENGTH_LONG).show()
            context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text(label("打开系统辅助功能", "Open Accessibility settings")) }
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
        TextButton(enabled = WirelessState.address.isNotEmpty() && !pairingBusy, onClick = {
                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(label("扫描电脑 Android Use 配对二维码", "Scan the Android Use pairing QR on your computer"))
                    .setBeepEnabled(false).setOrientationLocked(false))
        }) { Text(label("扫描电脑二维码配对", "Scan computer QR to pair")) }
        if (WirelessState.address.isEmpty()) Text(label("请先开启无线控制，再扫描电脑上的 Android Use 配对二维码。",
            "Enable wireless control first, then scan the Android Use pairing QR on your computer."), fontSize = 12.sp)
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
