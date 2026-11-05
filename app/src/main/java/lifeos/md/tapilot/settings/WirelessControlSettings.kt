package lifeos.md.tapilot.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.saveable.rememberSaveable
import lifeos.md.tapilot.wireless.PairingStage
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
    var status by rememberSaveable { mutableStateOf("") }
    var pairingDialog by rememberSaveable { mutableStateOf(false) }
    val deviceControl = rememberDeviceControlStatus(context)
    val unrestricted = rememberBackgroundAllowance(context)
    val detailInset = Modifier.padding(horizontal = 12.dp)
    val stage = WirelessState.pairingStage
    val pairingBusy = stage == PairingStage.SENDING || stage == PairingStage.WAITING
    val pairingMessage = when (stage) {
        PairingStage.SENDING -> label("正在向电脑发送配对信息…", "Sending pairing information to your computer…")
        PairingStage.WAITING -> label("扫码成功。请在 ChatGPT / Codex 的二维码页面点击“确认连接”，完成后这里会显示配对成功。", "QR received. Select Confirm connection on the QR page in ChatGPT / Codex; pairing success will appear here.")
        PairingStage.PAIRED -> label("配对成功。现在可以回到 ChatGPT 使用 Tapilot；电脑开始控制时会显示已连接。", "Paired successfully. Return to Tapilot in ChatGPT. Connected appears when the computer starts controlling.")
        PairingStage.FAILED -> label("配对失败：", "Pairing failed: ") + WirelessState.pairingError
        PairingStage.IDLE -> ""
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val qr = result.contents
        if (qr == null) {
            status = label("已取消扫码，未更改配对。", "Scan cancelled. Pairing unchanged.")
            android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            pairingDialog = true
            runCatching {
                checkNotNull(WirelessControlService.current) {
                    label("无线控制未开启，请开启后重新扫码。", "Enable wireless control, then scan again.")
                }.pairComputer(qr)
            }.onFailure {
                WirelessState.pairingError = it.message.orEmpty()
                WirelessState.pairingStage = PairingStage.FAILED
            }
        }
    }
    if (pairingDialog && pairingMessage.isNotEmpty()) AlertDialog(
        onDismissRequest = { pairingDialog = false },
        title = { Text(when (stage) {
            PairingStage.PAIRED -> label("配对成功", "Paired")
            PairingStage.FAILED -> label("配对失败", "Pairing failed")
            PairingStage.WAITING -> label("请在电脑确认", "Confirm on your computer")
            else -> label("正在配对", "Pairing")
        }) },
        text = { Text(pairingMessage) },
        confirmButton = { TextButton(onClick = { pairingDialog = false }) { Text(label("知道了", "OK")) } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label("让电脑上的 ChatGPT 通过 Wi-Fi 查看屏幕、点击和输入。保持手机解锁；可随时从通知中停止。",
            "Let ChatGPT on your computer view and operate this phone over Wi-Fi. Keep the phone unlocked; stop at any time from the notification."), fontSize = 12.sp)
        Text(label("设备控制（辅助功能）：", "Device control (Accessibility): ") + when (deviceControl) {
            DeviceControlStatus.READY -> label("已连接", "Connected")
            DeviceControlStatus.NEEDS_RECONNECT -> label("已授权 · 需要恢复连接", "Authorized · Reconnect required")
            DeviceControlStatus.DISABLED -> label("未开启", "Not enabled")
        }, fontSize = 12.sp)
        Text(label("设备控制开启路径：系统辅助功能 → 已安装／已下载的应用 → Tapilot 设备控制 → 打开开关并确认，再返回此页。",
            "Enable device control: Accessibility → Installed/Downloaded apps → Tapilot device control → Enable and confirm, then return here."), fontSize = 12.sp)
        if (deviceControl != DeviceControlStatus.READY) Text(
            if (deviceControl == DeviceControlStatus.NEEDS_RECONNECT)
                label("设备控制已授权，但服务未连接。请在系统辅助功能中将 Tapilot 设备控制关闭再开启，然后返回此页。无需重新配对。",
                    "Device control is authorized, but the service is not connected. Turn Tapilot device control off and on in Accessibility settings, then return here. No pairing reset is needed.")
            else label("设备控制未开启，暂时无法查看或操作屏幕。", "Enable device control to view and operate the screen."),
            modifier = detailInset, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        TextButton(onClick = {
            android.widget.Toast.makeText(context, if (deviceControl == DeviceControlStatus.NEEDS_RECONNECT) label("将 Tapilot 设备控制关闭再开启，然后返回 Tapilot", "Turn Tapilot device control off and on, then return to Tapilot") else label("找到 Tapilot 设备控制，打开开关并确认，再返回 Tapilot", "Enable Tapilot device control, confirm, then return to Tapilot"), android.widget.Toast.LENGTH_LONG).show()
            context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text(label("打开系统辅助功能", "Open Accessibility settings")) }
        Text(label("后台运行：", "Background operation: ") + if (unrestricted)
            label("系统电池优化已豁免", "Exempt from system battery optimization")
            else label("可能受系统电池优化限制", "May be restricted by system battery optimization"),
            modifier = detailInset, fontSize = 12.sp)
        Text(label("若切到其他应用后断线，请在 Tapilot 应用信息 → 电量与优化中允许后台运行，并取消后台断网。部分手机还需设置自启动；系统电池优化豁免不代表厂商限制已全部解除。无需重新配对。",
            "If the connection drops after switching apps, allow background operation and background network access in Tapilot app info → Battery. Some phones also require auto-start. A battery optimization exemption does not cover every manufacturer restriction. Keep the existing pairing."),
            modifier = detailInset, fontSize = 12.sp)
        TextButton(onClick = {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text(label("检查后台运行设置", "Check background settings")) }
        Text(label("无线控制：", "Wireless control: ") + (if (WirelessState.connected) label("已连接电脑", "Computer connected")
            else if (WirelessState.address.isNotEmpty()) (if (WirelessState.paired) label("已配对 · 等待电脑连接", "Paired · Waiting for computer") else label("未配对 · 请扫描电脑二维码", "Not paired · Scan computer QR")) + " · ${WirelessState.address}"
            else if (WirelessState.waitingForWifi) label("等待 Wi-Fi · 已有配对保留", "Waiting for Wi-Fi · Pairing kept")
            else if (WirelessState.enabled) label("正在开启…", "Starting…") else label("未开启", "Off")), modifier = detailInset)
        TextButton(onClick = {
            status = ""
            runCatching {
                if (WirelessState.enabled) WirelessControlService.stop(context)
                else ContextCompat.startForegroundService(context, Intent(context, WirelessControlService::class.java))
            }.onFailure { status = it.message.orEmpty() }
        }) { Text(if (WirelessState.enabled) label("停止无线控制", "Stop wireless control") else label("开启无线控制", "Enable wireless control")) }
        TextButton(enabled = WirelessState.address.isNotEmpty() && !WirelessState.connected && !pairingBusy, onClick = {
                scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(label("扫描电脑 Tapilot 配对二维码", "Scan the Tapilot pairing QR on your computer"))
                    .setCaptureActivity(PairingCaptureActivity::class.java)
                    .setBeepEnabled(false).setOrientationLocked(true))
        }) { Text(label("扫描电脑二维码配对", "Scan computer QR to pair")) }
        if (WirelessState.connected) Text(label("如需重新配对，请先在电脑端断开当前连接。", "Disconnect on the computer before pairing again."),
            modifier = detailInset, fontSize = 12.sp)
        if (WirelessState.address.isEmpty()) Text(if (WirelessState.enabled)
            label("等待 Wi-Fi 就绪后自动连接，已有电脑配对无需重新扫码。", "Connection resumes when Wi-Fi is ready. Paired computers do not need to scan again.")
            else label("请先开启无线控制，再扫描电脑上的 Tapilot 配对二维码。",
                "Enable wireless control first, then scan the Tapilot pairing QR on your computer."), fontSize = 12.sp)
        if (WirelessState.enabled && WirelessState.paired) TextButton(onClick = { WirelessControlService.current?.revoke(); status = label("已撤销配对", "Pairing revoked") }) {
            Text(label("撤销电脑配对", "Revoke computer pairing"))
        }
        if (pairingMessage.isNotEmpty()) Text(pairingMessage, modifier = detailInset, fontSize = 14.sp,
            color = if (stage == PairingStage.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (status.isNotEmpty()) Text(status, modifier = detailInset, fontSize = 12.sp)
        if (WirelessState.error.isNotEmpty()) Text(WirelessState.error, modifier = detailInset, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}
