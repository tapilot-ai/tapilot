package lifeos.md.tapilot.performance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import lifeos.md.tapilot.TapilotApplication
import org.json.JSONObject

/** Debug APK only, shell-permission protected. Uses the real product task coordinator. */
class PerformanceExperimentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fixture = intent.getStringExtra("fixture") ?: "read-auto-brightness"
        if (fixture !in listOf("read-auto-brightness", "read-android-version", "calculate-expression", "read-world-clock", "locate-calendar-date", "accept-cross-app", "accept-calendar-next-year", "accept-clock-tabs", "accept-clarification")) {
            setResult(1, "Unsupported benchmark fixture", null)
            return
        }
        val format = intent.getStringExtra("capture_format") ?: "jpeg"
        if (format !in listOf("png", "jpeg")) {
            setResult(1, "Expected capture_format: png or jpeg", null)
            return
        }
        val factor = intent.getStringExtra("screenshot_shrink_factor")
        if (factor !in listOf("1", "1.5", "reset")) {
            setResult(1, "Expected screenshot_shrink_factor: 1, 1.5 or reset", null)
            return
        }
        val preload = intent.getStringExtra("runtime_preload") ?: "reset"
        if (preload !in listOf("on", "off", "reset")) {
            setResult(1, "Unsupported runtime preload experiment", null)
            return
        }
        val runtime = (context.applicationContext as TapilotApplication).assistant
        if (runtime.state.running) {
            setResult(1, "Wait for the active task to finish", null)
            return
        }
        val controlled = intent.getBooleanExtra("controlled", false)
        val runBenchmark = controlled || intent.getBooleanExtra("run_benchmark", false)
        if (runBenchmark && (!runtime.coordinator.ready || !runtime.state.configured)) {
            setResult(1, "Open Tapilot and configure the model first", null)
            return
        }
        val editor = context.getSharedPreferences("tapilot-performance", Context.MODE_PRIVATE).edit()
        if (factor == "reset") editor.remove("screenshot-shrink-factor").remove("capture-format")
        else editor.putString("screenshot-shrink-factor", factor).putString("capture-format", format)
        if (preload == "reset") editor.remove("runtime-preload")
        else editor.putBoolean("runtime-preload", preload == "on")
        val saved = editor.commit()
        if (controlled && fixture.startsWith("accept-")) {
            setResult(1, "Acceptance scenarios require the full conversation agent", null)
            return
        }
        if (saved && runBenchmark) {
            // Benchmarks bypass AssistantRuntime.command, so textual "No voice"
            // cannot disable automatic reply playback. Mute this run without
            // changing the user's preference; their next normal send unmutes it.
            runtime.stopAudio()
            // Fixed scenarios only; no arbitrary commands or gesture injection.
            runtime.coordinator.command(JSONObject().put("action", "new"))
            if (controlled) {
                val source = context.assets.open("performance/$fixture.json").bufferedReader().use { it.readText() }
                runtime.coordinator.executeYaml(source)
            } else {
                val tasks = context.assets.open("performance/full-tasks.json").bufferedReader().use { JSONObject(it.readText()) }
                runtime.coordinator.command(JSONObject().put("action", "send").put("text", tasks.getString(fixture)))
            }
            setResult(if (runtime.state.running) 0 else 1,
                if (runtime.state.running) "Native benchmark accepted: $factor" else "Native benchmark rejected", null)
            return
        }
        setResult(if (saved) 0 else 1, if (saved) "Screenshot experiment: $factor" else "Save failed", null)
    }
}
