package lifeos.md.tapilot

import android.app.Application

class TapilotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppForegroundTracker.install(this)
    }
}
