package lifeos.md.tapilot.chatgpt

import lifeos.md.tapilot.AppText.text as appText
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import lifeos.md.tapilot.MainActivity

/** Keeps the loopback OAuth receiver alive while the user is in the browser. */
class ChatGptLoginService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate() {
        super.onCreate()
        val channel = "chatgpt-login"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, appText("ChatGPT 登录", "ChatGPT sign-in"), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(7413, Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(appText("等待 ChatGPT 授权", "Waiting for ChatGPT authorization")).setContentText(appText("请在浏览器完成登录，然后返回 Tapilot", "Finish signing in in your browser, then return to Tapilot"))
            .setContentIntent(open).setOngoing(true).build())
        handler.postDelayed({ stopSelf() }, 15 * 60_000L)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    companion object {
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, ChatGptLoginService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, ChatGptLoginService::class.java)) }
    }
}
