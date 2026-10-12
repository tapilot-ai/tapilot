package lifeos.md.tapilot

import android.app.Application

class TapilotApplication : Application() {
    val assistant by lazy { lifeos.md.tapilot.voice.AssistantRuntime(this) }
    override fun onCreate() {
        super.onCreate()
        AppForegroundTracker.install(this)
    }
}
