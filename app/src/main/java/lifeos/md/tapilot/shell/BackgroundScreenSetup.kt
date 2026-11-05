package lifeos.md.tapilot.shell

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import lifeos.md.tapilot.AppText.text as text
import lifeos.md.tapilot.conversation.PhoneResult
import org.json.JSONObject

/** A host-owned setup session. Only system-page navigation is delegated to the model. */
internal class BackgroundScreenSetup(
    private val activity: Context,
    private val onMain: (() -> Unit) -> Unit,
    private val progress: (String) -> Unit,
) {
    @Volatile var message = ""; private set
    @Volatile var cancelled = false; private set
    fun cancel() {
        if (current !== this) return
        cancelled = true
        lifeos.md.tapilot.bridge.ActiveTaskStopRegistry.stopActive()
    }

    fun run(stopped: () -> Boolean, beforeAction: () -> Unit = {}, operate: (String, String) -> PhoneResult): JSONObject {
        check(current == null) { "Another background-screen setup is running" }
        current = this
        val stages = BackgroundScreenSetupProgress()
        fun result(phase: String, message: String) = JSONObject().put("phase", phase).put("reply", message)
        fun checkActive() { check(!stopped()) { "task_stopped" } }
        fun navigate(step: SetupStep, instruction: String, condition: String) {
            val observed = operate(instruction, condition)
            if (!observed.success) {
                val setting = when (step) {
                    SetupStep.DEVELOPER -> Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
                    SetupStep.USB_DEBUGGING -> Settings.Global.ADB_ENABLED
                    SetupStep.WIRELESS_DEBUGGING -> "adb_wifi_enabled"
                    else -> null
                }
                var reached = false
                onMain { reached = setting != null && Settings.Global.getInt(activity.contentResolver, setting, 0) == 1 }
                if (!reached) throw SetupNavigationFailure(step, observed.result)
            }
        }
        try {
            if (android.os.Build.VERSION.SDK_INT < 31) return result("failed", text("后台屏幕需要 Android 12 或更高版本。", "Background screen requires Android 12 or later."))
            onMain {
                val preferences = ShellPreferences(activity)
                if (preferences.authorized) {
                    preferences.backgroundEnabled = true
                    ShellSetupService.start(activity)
                }
            }
            while (true) {
                checkActive()
                if (cancelled) return result("waiting_user", text("已停止自动设置。已完成的授权和配对会保留，你可以稍后继续。", "Automatic setup stopped. Existing consent and pairing are kept; you can continue later."))
                var snapshot: SetupSnapshot? = null
                onMain {
                    val preferences = ShellPreferences(activity)
                    snapshot = SetupSnapshot(preferences.authorized, preferences.backgroundEnabled,
                        ShellNotifications(activity).pairAllowed(),
                        Settings.Global.getInt(activity.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
                        Settings.Global.getInt(activity.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1,
                        Settings.Global.getInt(activity.contentResolver, "adb_wifi_enabled", 0) == 1,
                        ShellSession.ready, Settings.canDrawOverlays(activity),
                        activity.getSystemService(ConnectivityManager::class.java).let { manager ->
                            manager.allNetworks.any { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                        })
                }
                when (val decision = stages.advance(checkNotNull(snapshot), SystemClock.elapsedRealtime())) {
                    SetupDecision.Ready -> return result("completed", text(
                        "后台屏幕已配置好。下一条手机任务会在后台屏幕执行。",
                        "Background screen is configured. Your next phone task will run on it."))
                    is SetupDecision.TimedOut -> return result("waiting_user", text(
                        "自动设置已超时，当前步骤：", "Automatic setup timed out. Current step: ") + message + text(" 已完成的配置会保留。", " Existing setup is kept."))
                    SetupDecision.Revoked -> return result("waiting_user", text(
                        "后台屏幕已关闭或授权已撤销，自动设置已停止。", "Background screen was disabled or consent revoked. Automatic setup stopped."))
                    SetupDecision.Wait -> Unit
                    is SetupDecision.Enter -> {
                        val step = decision.step
                        message = if (step == SetupStep.PAIRING)
                            text("正在打开无线调试配对页面，请稍候。", "Opening the Wireless debugging pairing page. Please wait.") else step.message()
                        progress(message)
                        onMain { if (ShellSetupService.instance != null) ShellNotifications(activity).setupProgress(message) }
                        beforeAction(); checkActive()
                        when (step) {
                            SetupStep.CONSENT -> showSetup(ShellSetupActivity.REQUEST_CONSENT)
                            SetupStep.WIFI -> open(Settings.ACTION_WIFI_SETTINGS)
                            SetupStep.NOTIFICATIONS -> showSetup(ShellSetupActivity.REQUEST_NOTIFICATIONS)
                            SetupStep.DEVELOPER -> {
                                open(Settings.ACTION_DEVICE_INFO_SETTINGS, highlight = "build_number")
                                navigate(step, "在手机设置中找到版本号，连续点击以开启开发者选项。只操作这一项；如果出现锁屏密码、身份验证或系统确认，停止操作并保留页面让用户处理。不要输入或猜测密码。",
                                    "开发者选项已开启，或系统正在等待用户输入锁屏密码或确认开启开发者选项。")
                            }
                            SetupStep.USB_DEBUGGING -> {
                                open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, highlight = "enable_adb")
                                navigate(step, "在开发者选项中找到 USB 调试。如果已开启则不要切换；否则点击开启。遇到系统确认或身份验证时停止并让用户确认；不要更改其他设置。",
                                    "USB 调试已开启，或屏幕显示开启 USB 调试的系统确认或身份验证。")
                            }
                            SetupStep.WIRELESS_DEBUGGING -> {
                                onMain { activity.startActivity(ShellSetupActivity.wirelessIntent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                navigate(step, "在开发者选项中找到无线调试，打开其页面并开启。最多完整浏览一次相关列表；如果未提供无线调试入口，停止并报告找不到，不要反复上下翻页。遇到系统确认或身份验证时停止并让用户确认。不要打开配对码弹窗，不要更改 Wi-Fi 网络或其他设置。",
                                    "无线调试已开启，或屏幕显示开启无线调试的系统确认或身份验证。")
                            }
                            SetupStep.PAIRING -> {
                                onMain { activity.startActivity(ShellSetupActivity.wirelessIntent(activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                navigate(step, "仅导航到系统无线调试页面，显示“使用配对码配对设备”入口后停止。不要点击配对码或二维码入口，不要修改任何开关，不要读取或输入配对码；用户会通过通知输入。若已经在无线调试页面，保持当前页面。",
                                    "系统无线调试页面可见，并显示“使用配对码配对设备”或 Pair device with pairing code 入口。")
                                message = step.message(); progress(message)
                                onMain { ShellNotifications(activity).setupProgress(message) }
                            }
                            SetupStep.OVERLAY -> open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${activity.packageName}")
                            SetupStep.READY -> Unit
                        }
                        checkActive()
                    }
                }
                // Native state, not an AI report or Activity return, advances the flow.
                Thread.sleep(200)
            }
        } catch (failure: SetupNavigationFailure) {
            checkActive()
            if (failure.step == SetupStep.WIRELESS_DEBUGGING) return result("waiting_user", text(
                "未能自动开启无线调试。请在系统设置中搜索“无线调试”；如果系统没有提供这个入口，当前系统无法完成手机端自动配对。已完成的授权会保留，找到并开启后可以继续。",
                "Wireless debugging could not be enabled automatically. Search for Wireless debugging in system settings. If your system provides no entry, on-device automatic pairing cannot be completed on this system. Existing consent is kept; continue after finding and enabling it."))
            return result("waiting_user", text("自动导航未完成，当前步骤：", "Automatic navigation did not complete. Current step: ") + message + text(" 已完成的配置会保留，请稍后重新发起自动设置。", " Existing setup is kept. Start automatic setup again when ready."))
        } finally {
            if (current === this) current = null
            onMain { if (ShellSetupService.instance != null) ShellNotifications(activity).update(ShellSetupService.message(ShellSetupService.state)) }
        }
    }

    private fun showSetup(request: String) = onMain {
        activity.startActivity(Intent(activity, ShellSetupActivity::class.java)
            .putExtra(ShellSetupActivity.SETUP_REQUEST, request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    private fun open(action: String, uri: String? = null, highlight: String? = null) = onMain {
        activity.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
            if (uri != null) data = android.net.Uri.parse(uri)
            // Same best-effort preference hint as the existing wireless-settings shortcut.
            if (highlight != null) {
                putExtra(":settings:fragment_args_key", highlight)
                putExtra(":settings:show_fragment_args", android.os.Bundle().apply { putString(":settings:fragment_args_key", highlight) })
            }
        })
    }
    companion object { @Volatile var current: BackgroundScreenSetup? = null; private set }
}

private class SetupNavigationFailure(val step: SetupStep, message: String) : Exception(message)

internal data class SetupSnapshot(
    val authorized: Boolean, val enabled: Boolean, val notifications: Boolean,
    val developer: Boolean, val usb: Boolean, val wireless: Boolean,
    val connected: Boolean, val overlay: Boolean, val wifi: Boolean = true,
) {
    fun next(): SetupStep = when {
        !authorized || !enabled -> SetupStep.CONSENT
        // An existing helper needs no new debug setup or pairing notification.
        connected && overlay -> SetupStep.READY
        connected -> SetupStep.OVERLAY
        !wifi -> SetupStep.WIFI
        !notifications -> SetupStep.NOTIFICATIONS
        !developer -> SetupStep.DEVELOPER
        !usb -> SetupStep.USB_DEBUGGING
        !wireless -> SetupStep.WIRELESS_DEBUGGING
        else -> SetupStep.PAIRING
    }
}

internal enum class SetupStep {
    CONSENT, WIFI, NOTIFICATIONS, DEVELOPER, USB_DEBUGGING, WIRELESS_DEBUGGING, PAIRING, OVERLAY, READY;
    fun message(): String = when (this) {
        CONSENT -> text("请阅读并确认 Shell 辅助授权，随后会继续自动设置。", "Review and accept shell assistance consent to continue automatic setup.")
        WIFI -> text("请连接 Wi-Fi。连接后会自动继续，不会替你选择或切换网络。", "Connect to Wi-Fi to continue. Tapilot will not select or switch networks for you.")
        NOTIFICATIONS -> text("请允许配对通知，以便在通知里输入配对码。", "Allow pairing notifications so you can enter the code in the notification.")
        DEVELOPER -> text("正在开启开发者选项。如需锁屏密码，请在手机上输入。", "Enabling Developer options. Enter your lock-screen credential on the phone if requested.")
        USB_DEBUGGING -> text("正在开启 USB 调试。出现系统确认时，请自行确认。无需连接 USB 线。", "Enabling USB debugging. Confirm the system prompt if shown; no USB cable is needed.")
        WIRELESS_DEBUGGING -> text("正在开启无线调试。出现系统确认时，请自行确认。", "Enabling Wireless debugging. Confirm the system prompt if shown.")
        PAIRING -> text("请打开“使用配对码配对设备”，保持弹窗打开，在 Tapilot 通知里输入六位配对码。连接后会自动继续。", "Open Pair device with pairing code, keep the dialog open and enter the six digits in the Tapilot notification. Setup continues after connecting.")
        OVERLAY -> text("请允许 Tapilot 显示在其他应用上层，完成后会自动检查配置。", "Allow Tapilot to display over other apps. Setup will check the result automatically.")
        READY -> text("后台屏幕已配置。", "Background screen is configured.")
    }
}
