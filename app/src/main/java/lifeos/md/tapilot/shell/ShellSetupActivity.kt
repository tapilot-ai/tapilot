package lifeos.md.tapilot.shell

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.R
import lifeos.md.tapilot.experiment.BackgroundDisplayTrialActivity

/** One resumable setup flow. Advanced ports are deliberately kept out of the normal path. */
class ShellSetupActivity : Activity() {
    private lateinit var preferences: ShellPreferences
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val palette get() = AppTheme.colors(this)
    private var advanced = false
    private var manualDeveloper = false
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ShellPreferences(this)
        advanced = savedInstanceState?.getBoolean("advanced") ?: false
        manualDeveloper = savedInstanceState?.getBoolean("developer") ?: false
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(palette.background); setPadding(dp(24), dp(24), dp(24), dp(32)) }
        scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(palette.background); addView(content) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (AppTheme.isDark(this)) 0 else lightBars, lightBars)
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(scroll)
    }
    override fun onResume() {
        super.onResume()
        ShellSetupService.changed = { render() }
        if (preferences.authorized) ShellSetupService.start(this)
        render()
    }
    override fun onPause() { ShellSetupService.changed = null; super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("advanced", advanced); outState.putBoolean("developer", manualDeveloper)
        super.onSaveInstanceState(outState)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(id: Int, heading: Boolean = false): TextView = TextView(this).apply {
        setText(id); setTextColor(palette.text); textSize = if (heading) 22f else 16f
        setPadding(0, dp(if (heading) 24 else 8), 0, dp(8))
        if (heading) setTypeface(typeface, android.graphics.Typeface.BOLD)
        content.addView(this)
    }
    private fun button(id: Int, enabled: Boolean = true, action: () -> Unit) = Button(this).apply {
        setText(id); setTextColor(palette.text); backgroundTintList = android.content.res.ColorStateList.valueOf(palette.surface); isAllCaps = false; minHeight = dp(48); isEnabled = enabled
        content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setOnClickListener { action() }
    }
    private fun render() {
        if (isFinishing || isDestroyed) return
        content.removeAllViews()
        label(R.string.shell_experimental)
        label(R.string.shell_title, true)
        val enabled = Switch(this).apply {
            setText(R.string.shell_chat_enabled); setTextColor(palette.text); textSize = 17f
            minHeight = dp(56); isChecked = preferences.backgroundEnabled
            content.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        label(R.string.shell_chat_help)
        enabled.setOnCheckedChangeListener { _, value ->
            if (!value) {
                preferences.backgroundEnabled = false
                BackgroundConversationDevice.current?.closeScreen()
                render()
            } else if (!preferences.authorized) {
                AlertDialog.Builder(this).setTitle(R.string.shell_title).setMessage(R.string.shell_consent)
                    .setPositiveButton(R.string.shell_agree) { _, _ ->
                        preferences.authorized = true; preferences.backgroundEnabled = true
                        ShellSetupService.start(this); render()
                    }.setNegativeButton(android.R.string.cancel) { _, _ -> render() }
                    .setOnCancelListener { render() }.show()
            } else { preferences.backgroundEnabled = true; ShellSetupService.start(this); render() }
        }
        if (!preferences.authorized) {
            label(R.string.shell_chat_off)
            button(R.string.shell_done) { finish() }
            return
        }
        label(ShellSetupService.message(ShellSetupService.state)).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        if (!Settings.canDrawOverlays(this)) {
            label(R.string.shell_overlay_help)
            button(R.string.shell_overlay_permission) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            }
        }
        if (ShellSession.ready) {
            label(if (preferences.backgroundEnabled) R.string.shell_chat_ready else R.string.shell_chat_off)
        } else {
            val developer = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
            if (!developer && !manualDeveloper) {
                label(R.string.shell_developer_title, true); label(R.string.shell_developer_help)
                button(R.string.shell_about) { open(Settings.ACTION_DEVICE_INFO_SETTINGS) }
                button(R.string.shell_developer) { open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
                button(R.string.shell_step_done) { manualDeveloper = true; render() }
            } else {
                label(R.string.shell_pair_title, true); label(R.string.shell_pair_help)
                if (!ShellNotifications(this).pairAllowed()) button(R.string.shell_notifications) { notifications() }
                button(R.string.shell_wireless) { wirelessSettings() }
                button(R.string.shell_retry, ShellSetupService.state !in listOf(ShellState.PAIRING, ShellState.CONNECTING)) {
                    ShellSetupService.instance?.retryNow() ?: ShellSetupService.start(this)
                }
            }
        }
        recoveryControl()
        button(if (advanced) R.string.shell_advanced_hide else R.string.shell_advanced) { advanced = !advanced; render() }
        if (advanced) {
            if (ShellSession.ready) {
                label(R.string.shell_trial_help)
                button(R.string.shell_trial) { startActivity(Intent(this, BackgroundDisplayTrialActivity::class.java)) }
                button(R.string.virtual_agent_title) { startActivity(Intent(this, lifeos.md.tapilot.experiment.VirtualAgentTrialActivity::class.java)) }
            } else manual()
            button(R.string.shell_revoke) { revoke(false) }
            button(R.string.shell_forget) {
                AlertDialog.Builder(this).setMessage(R.string.shell_forgotten)
                    .setPositiveButton(R.string.shell_forget) { _, _ -> revoke(true) }
                    .setNegativeButton(android.R.string.cancel, null).show()
            }
        }
        button(R.string.shell_done) { finish() }
    }
    private fun recoveryControl() {
        val check = CheckBox(this).apply { setText(R.string.shell_recovery); setTextColor(palette.text); buttonTintList = android.content.res.ColorStateList.valueOf(palette.text); isChecked = preferences.recovery; minHeight = dp(48) }
        content.addView(check)
        if (preferences.recovery) label(if (ShellRecovery.available(this)) R.string.shell_recovery_enabled else if (ShellSession.ready) R.string.shell_recovery_denied else R.string.shell_recovery_unavailable)
        check.setOnCheckedChangeListener { _, enabled ->
            if (!enabled) { preferences.recovery = false; render() }
            else AlertDialog.Builder(this).setTitle(R.string.shell_recovery).setMessage(R.string.shell_recovery_help)
                .setPositiveButton(R.string.shell_agree) { _, _ ->
                    preferences.recovery = true
                    // Re-bootstrap only on this explicit user action to obtain the recovery capability.
                    ShellSession.revoke(); ShellSetupService.instance?.retryNow(); render()
                }.setNegativeButton(android.R.string.cancel) { _, _ -> render() }
                .setOnCancelListener { render() }.show()
        }
    }
    private fun manual() {
        label(R.string.shell_manual_help)
        fun field(id: Int, secret: Boolean = false): EditText {
            label(id)
            return EditText(this).apply {
                setHint(id); setTextColor(palette.text); setHintTextColor(palette.muted); inputType = InputType.TYPE_CLASS_NUMBER or if (secret) InputType.TYPE_NUMBER_VARIATION_PASSWORD else 0
                isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                minHeight = dp(48); content.addView(this)
            }
        }
        val port = field(R.string.shell_pair_port)
        val code = field(R.string.shell_code, true)
        button(R.string.shell_pair) {
            val value = runCatching { LocalAdbInput.port(port.text.toString()) }.getOrNull()
            if (value == null) { port.error = getString(R.string.shell_invalid_port); return@button }
            if (runCatching { LocalAdbInput.code(code.text.toString()) }.isFailure) { code.error = getString(R.string.shell_pair_invalid); return@button }
            val secret = code.text.toString(); code.text.clear()
            ShellSetupService.instance?.pair(value, secret)
        }
        button(R.string.shell_computer) {
            ShellSetupService.instance?.prepareComputerBootstrap()
            AlertDialog.Builder(this).setMessage(getString(R.string.shell_computer_help) + "\n\npython3 scripts/start-shell-helper.py --serial <device> --package " + packageName)
                .setPositiveButton(android.R.string.ok, null).show()
        }
        val connect = field(R.string.shell_connect_port)
        button(R.string.shell_connect) {
            val value = runCatching { LocalAdbInput.port(connect.text.toString()) }.getOrNull()
            if (value == null) connect.error = getString(R.string.shell_invalid_port)
            else ShellSetupService.instance?.connect(value)
        }
    }
    private fun revoke(forget: Boolean) {
        val service = ShellSetupService.instance
        if (service != null) service.revoke(forget) else {
            preferences.revoke(); ShellSession.revoke()
            if (forget) { LocalAdbIdentity.forget(this); preferences.paired = false; preferences.port = 0 }
        }
        render()
    }
    private fun notifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 71)
        } else startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName).putExtra(Settings.EXTRA_CHANNEL_ID, ShellNotifications.PAIR_CHANNEL))
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); render()
    }
    private fun wirelessSettings() {
        val direct = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
        if (direct.resolveActivity(packageManager) != null) runCatching { startActivity(direct) }
            .onFailure { open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
        else runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", "toggle_adb_wireless") })) }
            .onFailure { open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
    }
    private fun open(action: String) {
        runCatching { startActivity(Intent(action)) }.onFailure {
            Toast.makeText(this, R.string.shell_settings_missing, Toast.LENGTH_LONG).show()
        }
    }
}
