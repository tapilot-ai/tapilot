package lifeos.md.tapilot

import android.app.Activity
import android.app.Application
import android.os.Bundle
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService

object AppForegroundTracker : Application.ActivityLifecycleCallbacks {
    private var startedActivityCount = 0

    @Volatile
    var isForeground: Boolean = false
        private set

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        publish(startedActivityCount > 0)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        publish(startedActivityCount > 0)
    }

    private fun publish(foreground: Boolean) {
        if (isForeground == foreground) return
        isForeground = foreground
        MidsceneAccessibilityService.current()?.setAppForeground(foreground)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
