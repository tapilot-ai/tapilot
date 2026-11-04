package lifeos.md.tapilot.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

internal object ShellRecovery {
    fun available(context: Context) = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    fun restore(context: Context): Boolean {
        val policy = ShellPreferences(context)
        if (!policy.authorized || !policy.recovery || !available(context)) return false
        val network = context.getSystemService(ConnectivityManager::class.java)
        val wifi = network.allNetworks.any { network.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        if (!wifi || Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 0) return false
        return runCatching {
            if (Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 0) {
                Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)
            } else true
        }.getOrDefault(false)
    }
}

/** System broadcasts may restore an authorized connection, never a task or a display. */
class ShellRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val preferences = ShellPreferences(context)
        if (preferences.authorized && preferences.recovery) runCatching { ShellSetupService.start(context) }
    }
}
