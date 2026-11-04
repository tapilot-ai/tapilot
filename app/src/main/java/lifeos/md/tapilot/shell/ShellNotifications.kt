package lifeos.md.tapilot.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.R

internal class ShellNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private fun text(id: Int) = AppLanguage.wrap(context).getString(id)
    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, text(R.string.shell_channel), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(PAIR_CHANNEL, text(R.string.shell_pair_channel), NotificationManager.IMPORTANCE_HIGH))
    }
    private fun open() = PendingIntent.getActivity(context, 40, Intent(context, ShellSetupActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun ongoing(message: Int): Notification = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
        .setContentTitle(text(R.string.shell_title)).setContentText(text(message))
        .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(true)
        .addAction(0, text(R.string.shell_revoke), PendingIntent.getService(context, 41,
            Intent(context, ShellSetupService::class.java).setAction(ShellSetupService.REVOKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
    fun showPair(error: Boolean = false, invalid: Boolean = false) {
        // Explicit component + mutable token solely for system RemoteInput. No code in extras/logs.
        val intent = Intent(context, ShellSetupService::class.java).setAction(ShellSetupService.PAIR)
        val reply = PendingIntent.getForegroundService(context, 42, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val action = NotificationCompat.Action.Builder(0, text(R.string.shell_enter_code), reply)
            .addRemoteInput(RemoteInput.Builder(CODE).setLabel(text(R.string.shell_code)).build()).build()
        val message = text(if (invalid) R.string.shell_pair_invalid else if (error) R.string.shell_pair_failed else R.string.shell_code_help)
        runCatching { manager.notify(PAIR_ID, NotificationCompat.Builder(context, PAIR_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setContentTitle(text(R.string.shell_enter_code))
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open()).setOngoing(true).setOnlyAlertOnce(!error).addAction(action).build()) }
    }
    fun pairingProgress() {
        // Update before cancelling: SystemUI otherwise retains an active inline-reply history.
        manager.notify(PAIR_ID, NotificationCompat.Builder(context, PAIR_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(text(R.string.shell_pairing)).setContentText(text(R.string.shell_pairing))
            .setContentIntent(open()).setRemoteInputHistory(null).setOnlyAlertOnce(true).build())
    }
    fun cancelPair() { manager.cancel(PAIR_ID) }
    fun update(message: Int) { manager.notify(ID, ongoing(message)) }
    fun pairAllowed(): Boolean = manager.areNotificationsEnabled() && manager.getNotificationChannel(PAIR_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    companion object {
        const val ID = 7201
        const val PAIR_ID = 7202
        const val CODE = "pair-code"
        const val CHANNEL = "shell-assistance"
        const val PAIR_CHANNEL = "shell-pairing"
    }
}
