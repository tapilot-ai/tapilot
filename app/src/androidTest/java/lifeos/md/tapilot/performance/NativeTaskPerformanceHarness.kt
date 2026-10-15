package lifeos.md.tapilot.performance

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import lifeos.md.tapilot.MainActivity
import lifeos.md.tapilot.TapilotApplication
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.voice.VoiceOutputSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Test APK only. Submits a goal to the real product coordinator; never performs its actions. */
class NativeTaskPerformanceHarness : Instrumentation() {
    private var samples = 3
    private var runId = ""
    private var manualRebind = false
    private var screenshotAb = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        samples = arguments?.getString("samples")?.toInt() ?: 3
        runId = arguments?.getString("run_id") ?: java.util.UUID.randomUUID().toString()
        manualRebind = arguments?.getString("manual_accessibility_rebind") == "1"
        screenshotAb = arguments?.getString("screenshot_ab") == "1"
        require(samples in 1..10)
        start()
    }
    override fun onStart() {
        val output = File(targetContext.getExternalFilesDir(null), "performance").apply { mkdirs() }
        val fixture = File(targetContext.cacheDir, "performance-model.json")
        val result = JSONObject().put("schemaVersion", 1).put("modelBacked", true).put("runId", runId)
            .put("deviceModel", Build.MODEL).put("androidVersion", Build.VERSION.RELEASE)
            .put("abi", Build.SUPPORTED_ABIS.first()).put("samples", JSONArray())
        var code = 1
        var provisioned = false
        val voice = VoiceOutputSettings(targetContext)
        val previousAutoRead = voice.autoRead
        val experiment = targetContext.getSharedPreferences("tapilot-performance", android.content.Context.MODE_PRIVATE)
        val previousFactor = experiment.getString("screenshot-shrink-factor", null)
        val runtime = (targetContext.applicationContext as TapilotApplication).assistant
        val coordinator = runtime.coordinator
        var stage = "startup"
        try {
            check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "arm64-v8a device required" }
            if (screenshotAb) check(targetContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) { "Screenshot A/B requires a debug APK" }
            result.put("screenshotAb", screenshotAb)
            // Keep native AccessibilityService active for the entire instrumentation
            // session, including the manual setup path on restricted OEM devices.
            val automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            runOnMainSync { targetContext.startActivity(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            await(30_000) { coordinator.ready }
            stage = "accessibility"
            if (MidsceneAccessibilityService.current() == null && !manualRebind) {
                // Instrumentation restarts the target process; Android can leave its
                // already-enabled service in the crashed set. Rebind that service only.
                val component = "lifeos.md.tapilot/lifeos.md.tapilot.accessibility.MidsceneAccessibilityService"
                val enabled = Settings.Secure.getString(targetContext.contentResolver, "enabled_accessibility_services").orEmpty()
                check(component in enabled.split(':')) { "Enable Tapilot accessibility before testing" }
                require(enabled.matches(Regex("[A-Za-z0-9_./:]+")))
                val others = enabled.split(':').filterNot { it == component }.joinToString(":")
                fun setServices(value: String) {
                    // UiAutomation executes argv directly, not through a quoting shell.
                    val command = if (value.isEmpty()) "settings delete secure enabled_accessibility_services"
                        else "settings put secure enabled_accessibility_services $value"
                    val response = automation.executeShellCommand(command)
                        .use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().toString(Charsets.UTF_8) }
                    check(!response.contains("Exception") && !response.contains("Permission denial")) { "Accessibility rebind command failed" }
                }
                setServices(others)
                try {
                    // Wait for the system observer to consume removal before restoring it;
                    // immediate writes can otherwise coalesce into no service-state change.
                    await(10_000) {
                        targetContext.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                            .none { it.id == component || it.id == "lifeos.md.tapilot/.accessibility.MidsceneAccessibilityService" }
                    }
                } finally { setServices(enabled) }
            }
            if (manualRebind && MidsceneAccessibilityService.current() == null)
                sendStatus(1, Bundle().apply { putString("setup", "Re-enable the already-authorized Tapilot accessibility service in Android Settings") })
            await(if (manualRebind) 120_000 else 30_000) { MidsceneAccessibilityService.current() != null }
            runOnMainSync {
                val apps = MidsceneAccessibilityService.current()!!.capabilityDetails().getJSONArray("apps")
                val compact = JSONArray()
                for (i in 0 until apps.length()) apps.getJSONObject(i).let { app ->
                    compact.put(JSONArray().put(app.getString("label")).put(app.getString("component")))
                }
                result.put("launcherCatalogue", JSONObject().put("entries", apps.length())
                    .put("objectBytes", apps.toString().toByteArray().size)
                    .put("tupleBytes", compact.toString().toByteArray().size))
            }
            stage = "model"
            runOnMainSync {
                check(!runtime.state.running) { "Device already has an active task" }
                output.listFiles()?.forEach { it.delete() }
                voice.autoRead = false
                if (fixture.isFile) {
                    val config = JSONObject(fixture.readText())
                    fixture.delete()
                    coordinator.preference("chatgptConversation", false)
                    coordinator.selectPreset("custom")
                    coordinator.saveModel(config, config.getString("apiKey"))
                    provisioned = true
                }
                check(runtime.state.configured) { "Configure a native task model before benchmarking" }
            }
            repeat(samples) { index ->
                stage = "sample-$index"
                val factor = if (screenshotAb && index % 2 == 1) "1.5" else "1"
                check(experiment.edit().putString("screenshot-shrink-factor", factor).commit())
                // MainActivity is singleTask: startActivitySync would wait forever
                // when Android delivers onNewIntent instead of creating an activity.
                runOnMainSync { targetContext.startActivity(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                var taskId = ""
                val started = SystemClock.elapsedRealtime()
                runOnMainSync {
                    coordinator.command(JSONObject().put("action", "new"))
                    coordinator.command(JSONObject().put("action", "send").put("text",
                        if (screenshotAb) "Open Android Settings. Tap the Display & brightness entry and verify that page is visible. Return to the main Settings page and verify it is visible. Read only: do not change settings. No voice."
                        else "Open Android Settings and verify its main Settings page is visible. Read only. No voice."))
                    check(runtime.state.running) { "Task was not accepted" }
                    taskId = coordinator.data.getJSONArray("history").getJSONObject(0).getString("id")
                }
                var entry = JSONObject()
                await(10 * 60_000) {
                    entry = JSONObject(coordinator.data.getJSONArray("history").getJSONObject(0).toString())
                    entry.getString("id") == taskId && entry.optString("status") !in listOf("running", "queued")
                }
                val sample = JSONObject().put("index", index).put("wallMs", SystemClock.elapsedRealtime() - started)
                    .put("screenshotShrinkFactor", factor.toDouble())
                    .put("status", entry.optString("status")).put("modelTurns", entry.optInt("modelTurns"))
                    .put("phoneOperations", entry.optInt("phoneOperations"))
                    .put("performance", entry.optJSONObject("performance") ?: JSONObject())
                    .put("operations", entry.optJSONArray("completedOperations") ?: JSONArray())
                    .put("reply", entry.optString("reply"))
                result.getJSONArray("samples").put(sample)
                val reports = entry.optJSONArray("reports") ?: JSONArray()
                for (i in 0 until reports.length()) {
                    val id = reports.getJSONObject(i).getString("reportId")
                    require(id.matches(Regex("[A-Za-z0-9-]+")))
                    File(targetContext.filesDir, "reports/$id.html").copyTo(File(output, "$index-$id.html"), overwrite = true)
                }
                File(output, "summary.json").writeText(result.toString(2))
                check(entry.optString("status") == "succeeded") { "Native task failed" }
                check(entry.optString("reply").isNotBlank()) { "Missing final answer" }
                check(entry.optInt("phoneOperations") > 0 && reports.length() > 0) { "No native execution evidence" }
                val operations = entry.getJSONArray("completedOperations")
                check(operations.length() > 0 && (0 until operations.length()).all { operations.getJSONObject(it).optBoolean("success") }) { "Phone operation was not verified" }
            }
            result.put("status", "passed")
            code = 0
        } catch (error: Throwable) {
            result.put("status", "failed").put("failureStage", stage).put("failureType", error.javaClass.simpleName)
            if (stage in listOf("startup", "accessibility")) result.put("setupFailure", error.message)
            runOnMainSync { coordinator.command(JSONObject().put("action", "stop")) }
        } finally {
            val restored = experiment.edit().let { editor ->
                if (previousFactor == null) editor.remove("screenshot-shrink-factor") else editor.putString("screenshot-shrink-factor", previousFactor)
                editor.commit()
            }
            if (!restored) { result.put("status", "failed").put("failureStage", "screenshot-settings-cleanup"); code = 1 }
            fixture.delete()
            if (provisioned) lifeos.md.tapilot.bridge.ModelCredentialStore(targetContext).delete("custom")
            // finish() terminates the target process; flush restoration before it.
            if (!targetContext.getSharedPreferences("tapilot-voice-output", 0).edit()
                    .putBoolean("auto-read", previousAutoRead).commit()) {
                result.put("status", "failed").put("failureStage", "voice-settings-cleanup")
                code = 1
            }
            File(output, "summary.json").writeText(result.toString(2))
        }
        finish(code, Bundle().apply { putString("performance_status", result.getString("status")); putString("performance_path", output.absolutePath) })
    }
    private fun await(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            runOnMainSync { ready = predicate() }
            if (ready) return
            Thread.sleep(100)
        }
        error("Performance harness timed out")
    }
}
