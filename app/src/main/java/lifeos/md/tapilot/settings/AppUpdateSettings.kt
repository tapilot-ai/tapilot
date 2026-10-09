package lifeos.md.tapilot.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import lifeos.md.tapilot.update.AppRelease
import lifeos.md.tapilot.update.AppUpdate

@Composable
fun AppUpdateSettings(activity: Activity, zh: Boolean) {
    fun label(a: String, b: String) = if (zh) a else b
    val info = remember { activity.packageManager.getPackageInfo(activity.packageName, 0) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val alive = remember { AtomicBoolean(true) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var checking by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var status by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { alive.set(false); worker.shutdownNow() } }
    fun openDownload() {
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release?.downloadUrl ?: AppUpdate.DOWNLOAD_PAGE)))
        }.onFailure { status = label("无法打开下载页面，请安装浏览器或飞书。", "Install a browser or Feishu to open the download page.") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label("当前版本 ${info.versionName} (${info.longVersionCode})", "Current version ${info.versionName} (${info.longVersionCode})"),
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !checking, onClick = {
                checking = true; status = ""; release = null
                worker.execute {
                    val result = runCatching { AppUpdate.check() }
                    handler.post {
                        if (alive.get()) {
                            checking = false
                            result.fold(onSuccess = {
                                release = it
                                status = if (it.isNewerThan(info.longVersionCode)) label("发现新版本 ${it.version}", "New version ${it.version} available")
                                    else label("已是最新版本", "You’re up to date")
                            }, onFailure = {
                                Log.w("TapilotUpdate", "Public update check failed", it)
                                status = label("检查失败，请检查网络后重试，或打开下载页面。", "Could not check for updates. Retry or open the download page.")
                            })
                        }
                    }
                }
            }) { Text(if (checking) label("正在检查…", "Checking…") else label("检查更新", "Check for updates")) }
            TextButton(onClick = ::openDownload) {
                Text(if (release?.isNewerThan(info.longVersionCode) == true) label("下载新版 ↗", "Download update ↗") else label("下载页面 ↗", "Download page ↗"))
            }
        }
        if (status.isNotEmpty()) Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
