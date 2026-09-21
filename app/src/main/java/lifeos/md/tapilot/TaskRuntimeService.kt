package lifeos.md.tapilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.bridge.ActiveTaskStopRegistry

class TaskRuntimeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stopAfterTerminal = Runnable {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.task_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.task_notification_channel_description)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(stopAfterTerminal)
        if (intent?.action == ACTION_STOP_TASK) {
            TaskExecutionTracker.update(TaskExecutionStatus.STOPPED)
            MidsceneAccessibilityService.current()?.refreshTaskRunning()
            ActiveTaskStopRegistry.stopActive()
            startForeground(NOTIFICATION_ID, notification(TaskExecutionStatus.STOPPED))
            handler.postDelayed(stopAfterTerminal, TERMINAL_DISPLAY_MILLIS)
            return START_NOT_STICKY
        }
        val status = intent?.getStringExtra(EXTRA_STATUS)
            ?.let { runCatching { TaskExecutionStatus.valueOf(it) }.getOrNull() }
            ?: TaskExecutionStatus.RUNNING
        TaskExecutionTracker.update(status)
        MidsceneAccessibilityService.current()?.refreshTaskRunning()
        startForeground(NOTIFICATION_ID, notification(status))
        if (status == TaskExecutionStatus.SUCCEEDED ||
            status == TaskExecutionStatus.FAILED ||
            status == TaskExecutionStatus.STOPPED
        ) {
            handler.postDelayed(stopAfterTerminal, TERMINAL_DISPLAY_MILLIS)
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopAfterTerminal)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(status: TaskExecutionStatus): Notification {
        val title = when (status) {
            TaskExecutionStatus.STARTING -> getString(R.string.task_notification_starting)
            TaskExecutionStatus.RUNNING -> getString(R.string.task_notification_running)
            TaskExecutionStatus.FINALIZING -> getString(R.string.task_notification_finalizing)
            TaskExecutionStatus.SUCCEEDED -> getString(R.string.task_notification_succeeded)
            TaskExecutionStatus.FAILED -> getString(R.string.task_notification_failed)
            TaskExecutionStatus.STOPPED -> getString(R.string.task_notification_stopped)
            TaskExecutionStatus.IDLE -> getString(R.string.app_name)
        }
        val active = status == TaskExecutionStatus.STARTING ||
            status == TaskExecutionStatus.RUNNING ||
            status == TaskExecutionStatus.FINALIZING
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(title)
            .setContentText(getString(R.string.task_notification_description))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(active)
            .setOnlyAlertOnce(true)
        if (active) {
            val stopIntent = PendingIntent.getService(
                this,
                STOP_REQUEST_CODE,
                Intent(this, TaskRuntimeService::class.java).setAction(ACTION_STOP_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        this,
                        android.R.drawable.ic_menu_close_clear_cancel,
                    ),
                    getString(R.string.task_notification_stop),
                    stopIntent,
                ).build(),
            )
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "midscene_task_runtime"
        private const val NOTIFICATION_ID = 2107
        private const val EXTRA_STATUS = "task_status"
        private const val ACTION_STOP_TASK = "lifeos.md.tapilot.action.STOP_TASK"
        private const val STOP_REQUEST_CODE = 2109
        private const val TERMINAL_DISPLAY_MILLIS = 3_000L

        fun report(context: Context, status: TaskExecutionStatus) {
            val intent = Intent(context, TaskRuntimeService::class.java)
                .putExtra(EXTRA_STATUS, status.name)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
