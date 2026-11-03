package lifeos.md.tapilot.experiment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.R
import lifeos.md.tapilot.shell.ShellSession
import lifeos.md.tapilot.shell.ShellSetupActivity

/** A small, observable first success; diagnostic controls remain in the separate developer probe. */
class BackgroundDisplayTrialActivity : Activity() {
    private lateinit var probe: BackgroundDisplayProbe
    private lateinit var result: TextView
    private var floating: FloatingDisplayWindow? = null
    private var attempted = false
    private var passed = false
    private lateinit var overlayPermission: Button
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        attempted = savedInstanceState?.getBoolean("attempted") ?: false
        passed = savedInstanceState?.getBoolean("passed") ?: false
        val colors = AppTheme.colors(this)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding)
            setBackgroundColor(colors.background)
        }
        fun text(id: Int, size: Float = 16f) = TextView(this).apply {
            setText(id); textSize = size; setTextColor(colors.text); setPadding(0, padding / 2, 0, padding / 2); body.addView(this)
        }
        text(R.string.shell_experimental)
        text(R.string.shell_trial, 24f)
        text(R.string.shell_trial_help)
        result = text(R.string.shell_trial_waiting)
        fun button(id: Int, action: () -> Unit): Button {
            val view = Button(this).apply {
                setText(id); isAllCaps = false; minHeight = padding * 2
                setTextColor(colors.text); backgroundTintList = android.content.res.ColorStateList.valueOf(colors.surface)
                setOnClickListener { action() }
            }
            body.addView(view, LinearLayout.LayoutParams(-1, -2))
            return view
        }
        probe = BackgroundDisplayProbe(this, ::render)
        button(R.string.shell_trial) { attempted = false; begin() }
        overlayPermission = button(R.string.shell_overlay_permission) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button(R.string.shell_done) { finish() }
        button(R.string.shell_manage) { startActivity(Intent(this, ShellSetupActivity::class.java)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(colors.background); addView(body) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (AppTheme.isDark(this)) 0 else lightBars, lightBars)
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(scroll)
        ShellSession.changed = {
            if (!ShellSession.ready) {
                probe.close(); result.setText(R.string.shell_trial_disconnected)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        overlayPermission.visibility = if (Settings.canDrawOverlays(this)) android.view.View.GONE else android.view.View.VISIBLE
        if (attempted) render() else begin()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("attempted", attempted); outState.putBoolean("passed", passed)
        super.onSaveInstanceState(outState)
    }
    private fun begin() {
        if (attempted || !::probe.isInitialized) return
        if (!ShellSession.ready) { result.setText(R.string.shell_trial_disconnected); return }
        if (!Settings.canDrawOverlays(this)) { result.setText(R.string.shell_overlay_help); return }
        attempted = true; passed = false
        runCatching {
            floating?.close(); probe.close(); probe.start()
            floating = FloatingDisplayWindow(this, probe, AppLanguage.resolved(this) == "zh") { probe.close() }.also { it.show(atBottom = true) }
        }.onFailure {
            probe.close(); result.setText(R.string.shell_trial_failed)
        }
    }
    private fun render() {
        if (!::probe.isInitialized) return
        floating?.render()
        if (probe.taps > 0 && probe.frames > 0) passed = true
        result.setText(when {
            passed -> R.string.shell_trial_success
            probe.active -> R.string.shell_trial_waiting
            else -> R.string.shell_trial_stopped
        })
    }
    override fun onDestroy() {
        ShellSession.changed = null
        floating?.close()
        if (::probe.isInitialized) probe.dispose()
        super.onDestroy()
    }
}
