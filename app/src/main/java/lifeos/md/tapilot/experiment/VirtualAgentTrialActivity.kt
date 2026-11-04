package lifeos.md.tapilot.experiment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.R
import lifeos.md.tapilot.bridge.ActiveTaskStopRegistry
import lifeos.md.tapilot.bridge.NodeTestRunCoordinator
import lifeos.md.tapilot.report.ReportRepository
import lifeos.md.tapilot.shell.ShellSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** A deliberately bounded real-model task; normal conversation routing stays on the primary screen. */
class VirtualAgentTrialActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val cleanup = Executors.newSingleThreadExecutor()
    private val state = VirtualTrialState()
    private lateinit var probe: BackgroundDisplayProbe
    private lateinit var status: TextView
    private lateinit var steps: TextView
    private lateinit var start: Button
    private lateinit var pause: Button
    private var floating: FloatingDisplayWindow? = null
    private var runner: NodeTestRunCoordinator? = null
    private var registration: ActiveTaskStopRegistry.Registration? = null
    private var target = 1
    private var reports: ReportRepository? = null
    private val trace = ArrayDeque<String>()
    private val stepHistory = JSONObject()
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val colors = AppTheme.colors(this)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding); setBackgroundColor(colors.background) }
        fun label(id: Int, size: Float = 16f) = TextView(this).apply {
            setText(id); textSize = size; setTextColor(colors.text); setPadding(0, padding / 2, 0, padding / 2); body.addView(this)
        }
        fun button(id: Int, action: () -> Unit) = Button(this).apply {
            setText(id); isAllCaps = false; minHeight = padding * 3; setTextColor(colors.text)
            backgroundTintList = android.content.res.ColorStateList.valueOf(colors.surface)
            setOnClickListener { action() }; body.addView(this)
        }
        label(R.string.virtual_agent_title, 22f)
        label(R.string.virtual_agent_short_help)
        status = label(R.string.virtual_agent_ready).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        start = button(R.string.virtual_agent_start, ::begin)
        pause = button(R.string.virtual_agent_pause, ::takeOver)
        button(R.string.virtual_agent_stop) { closeTrial(); finish() }
        steps = label(R.string.virtual_agent_steps)
        label(R.string.virtual_agent_help)
        probe = BackgroundDisplayProbe(this) {
            floating?.render()
            if (state.phase != VirtualTrialState.Phase.CLOSED && !probe.active && runner != null) closeTrial()
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(colors.background); addView(body) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (AppTheme.isDark(this)) 0 else lightBars, lightBars)
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(scroll)
        ShellSession.changed = { if (!ShellSession.ready) closeTrial() }
        render()
    }
    private fun record(id: Int) {
        if (trace.size == 12) trace.removeFirst()
        trace.addLast(getString(id))
        renderSteps()
    }
    private fun renderSteps() {
        val zh = AppLanguage.resolved(this) == "zh"
        val rows = lifeos.md.tapilot.chat.ExecutionStep.from(stepHistory.optJSONArray("executionSteps"))
        steps.text = (trace.toList() + rows.takeLast(12).map { "${it.number}. ${it.label(zh)} — ${it.stateLabel(zh)}" }).joinToString("\n")
    }
    private fun begin() {
        runCatching {
            check(ShellSession.ready) { getString(R.string.shell_trial_disconnected) }
            check(Settings.canDrawOverlays(this)) { getString(R.string.shell_overlay_help) }
            val environment = VirtualTrialModel.environment(this)
            if (!probe.active) {
                probe.start(); target = probe.taps + 1
                floating = FloatingDisplayWindow(this, probe, AppLanguage.resolved(this) == "zh", { state.manual }) {
                    closeTrial()
                }.also { it.show(atBottom = true, maxWidthDp = 160) }
            }
            val run = state.begin()
            registration = ActiveTaskStopRegistry.register { main.post { closeTrial() }; true }
            val repository = reports ?: ReportRepository(File(filesDir, "reports")).also { reports = it }
            val coordinator = NodeTestRunCoordinator(this, repository,
                device = VirtualScreenDevice(probe) { state.permits(run) },
                onStep = { _, event -> main.post {
                    if (state.permits(run)) {
                        lifeos.md.tapilot.conversation.ExecutionSteps.update(stepHistory, event); renderSteps()
                    }
                } })
            runner = coordinator
            record(R.string.virtual_agent_observe); render()
            main.postDelayed({ if (state.permits(run)) { record(R.string.virtual_agent_timeout); closeTrial() } }, 180_000)
            // Wait for the actual Presentation frame, never substitute a primary-screen capture.
            fun launchWhenReady(attempt: Int) {
                if (!state.permits(run)) return
                if (probe.bitmap == null && attempt < 30) { main.postDelayed({ launchWhenReady(attempt + 1) }, 100); return }
                runCatching { coordinator.run(workflow(), "yaml", environment, reportTaskStatus = false, preparationSession = "virtual-trial-$run") { result -> main.post {
                    if (!state.permits(run)) return@post
                    state.finish(run, result.isSuccess && probe.taps == target)
                    lifeos.md.tapilot.conversation.ExecutionSteps.settle(stepHistory)
                    record(if (state.phase == VirtualTrialState.Phase.SUCCEEDED) R.string.virtual_agent_verified else R.string.virtual_agent_failed)
                    releaseRunner(coordinator); render()
                    result.exceptionOrNull()?.message?.lineSequence()?.firstOrNull()?.let { status.append("\n" + it.take(300)) }
                } } }.onFailure {
                    closeTrial(); status.text = it.message ?: getString(R.string.virtual_agent_failed)
                }
            }
            launchWhenReady(0)
        }.onFailure {
            closeTrial(); status.text = it.message ?: getString(R.string.virtual_agent_failed)
        }
    }
    private fun workflow(): String {
        val goal = getString(R.string.shell_fixture_count, target)
        return JSONObject().put("cases", JSONArray().put(JSONObject().put("name", "Virtual screen click trial")
            .put("steps", JSONArray()
                .put(JSONObject().put("aiAct", JSONObject().put("prompt",
                    "Inspect this test screen. The goal is exactly '$goal'. If that value is already visible, do nothing. Otherwise tap the test-click counter only until that value is reached. Never launch apps or use system navigation. Stop if the value is higher than the goal.")))
                .put(JSONObject().put("aiAssert", "The test screen visibly shows exactly '$goal'."))))).toString()
    }
    private fun takeOver() {
        if (state.phase != VirtualTrialState.Phase.RUNNING) return
        val ticket = state.pause()
        lifeos.md.tapilot.conversation.ExecutionSteps.settle(stepHistory); renderSteps()
        val previous = runner ?: return
        render()
        cleanup.execute {
            val stopped = runCatching { previous.stopAndWait() }
            main.post {
                if (state.phase != VirtualTrialState.Phase.PAUSING) return@post
                if (stopped.isFailure) { closeTrial(); return@post }
                fun drained() {
                    if (state.phase != VirtualTrialState.Phase.PAUSING) return
                    if (probe.gesturePending) { main.postDelayed(::drained, 50); return }
                    releaseRunner(previous); state.paused(ticket); record(R.string.virtual_agent_manual); render()
                }
                drained()
            }
        }
    }
    private fun releaseRunner(previous: NodeTestRunCoordinator) {
        if (runner === previous) runner = null
        ActiveTaskStopRegistry.unregister(registration); registration = null
        cleanup.execute { previous.close() }
    }
    private fun render() {
        val phase = state.phase
        status.setText(when (phase) {
            VirtualTrialState.Phase.READY -> R.string.virtual_agent_ready
            VirtualTrialState.Phase.RUNNING -> R.string.virtual_agent_running
            VirtualTrialState.Phase.PAUSING -> R.string.virtual_agent_pausing
            VirtualTrialState.Phase.PAUSED -> R.string.virtual_agent_manual
            VirtualTrialState.Phase.SUCCEEDED -> R.string.virtual_agent_verified
            VirtualTrialState.Phase.FAILED -> R.string.virtual_agent_failed
            VirtualTrialState.Phase.CLOSED -> R.string.virtual_agent_closed
        })
        start.setText(if (phase == VirtualTrialState.Phase.READY) R.string.virtual_agent_start else R.string.virtual_agent_resume)
        start.isEnabled = phase in setOf(VirtualTrialState.Phase.READY, VirtualTrialState.Phase.PAUSED, VirtualTrialState.Phase.FAILED)
        pause.isEnabled = phase == VirtualTrialState.Phase.RUNNING
    }
    private fun closeTrial() {
        if (state.phase == VirtualTrialState.Phase.CLOSED) return
        state.close(); main.removeCallbacksAndMessages(null)
        lifeos.md.tapilot.conversation.ExecutionSteps.settle(stepHistory); renderSteps()
        runner?.let(::releaseRunner)
        floating?.close(); floating = null
        if (::probe.isInitialized) probe.close()
        render()
    }
    override fun onDestroy() {
        ShellSession.changed = null; closeTrial()
        if (::probe.isInitialized) probe.dispose()
        cleanup.execute { reports?.close() }; cleanup.shutdown()
        super.onDestroy()
    }
}
