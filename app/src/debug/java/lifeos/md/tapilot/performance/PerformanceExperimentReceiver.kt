package lifeos.md.tapilot.performance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lifeos.md.tapilot.TapilotApplication
import org.json.JSONObject

/** Debug APK only, shell-permission protected. Uses the real product task coordinator. */
class PerformanceExperimentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val factor = intent.getStringExtra("screenshot_shrink_factor")
        if (factor !in listOf("1", "1.5", "reset")) {
            setResult(1, "Expected screenshot_shrink_factor: 1, 1.5 or reset", null)
            return
        }
        val runtime = (context.applicationContext as TapilotApplication).assistant
        if (runtime.state.running) {
            setResult(1, "Wait for the active task to finish", null)
            return
        }
        val runBenchmark = intent.getBooleanExtra("run_benchmark", false)
        if (runBenchmark && (!runtime.coordinator.ready || !runtime.state.configured)) {
            setResult(1, "Open Tapilot and configure the model first", null)
            return
        }
        val editor = context.getSharedPreferences("tapilot-performance", Context.MODE_PRIVATE).edit()
        if (factor == "reset") editor.remove("screenshot-shrink-factor")
        else editor.putString("screenshot-shrink-factor", factor)
        val saved = editor.commit()
        if (saved && runBenchmark) {
            // Fixed read-only scenario, no arbitrary commands or gesture injection.
            runtime.coordinator.command(JSONObject().put("action", "new"))
            runtime.coordinator.command(JSONObject().put("action", "send").put("text",
                "Open Settings, open Display and brightness, and read the current auto-brightness switch state. Do not change any setting. No voice."))
            setResult(if (runtime.state.running) 0 else 1,
                if (runtime.state.running) "Native benchmark accepted: $factor" else "Native benchmark rejected", null)
            return
        }
        setResult(if (saved) 0 else 1, if (saved) "Screenshot experiment: $factor" else "Save failed", null)
    }
}
