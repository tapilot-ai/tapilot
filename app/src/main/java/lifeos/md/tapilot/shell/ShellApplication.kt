package lifeos.md.tapilot.shell

import android.app.Activity
import android.app.Application
import android.os.Bundle
import lifeos.md.tapilot.TapilotApplication

/** Authorized foreground recovery after Android stopped the service. Never starts a display. */
class ShellApplication : TapilotApplication() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (ShellPreferences(this@ShellApplication).authorized && ShellSetupService.instance == null) {
                    runCatching { ShellSetupService.start(activity) }
                }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
