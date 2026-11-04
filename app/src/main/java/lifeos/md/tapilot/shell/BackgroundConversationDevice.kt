package lifeos.md.tapilot.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import lifeos.md.tapilot.AppText.text as text
import lifeos.md.tapilot.accessibility.NativeAction
import lifeos.md.tapilot.bridge.ActiveTaskStopRegistry
import lifeos.md.tapilot.conversation.ConversationScreenDevice
import lifeos.md.tapilot.conversation.PhoneOperationRoute
import lifeos.md.tapilot.device.DeviceCommand
import lifeos.md.tapilot.device.DeviceService
import lifeos.md.tapilot.experiment.BackgroundDisplayProbe
import lifeos.md.tapilot.experiment.FloatingDisplayWindow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executors

/** Conversation-only backend. A failed/revoked background route can never become the primary route. */
class BackgroundConversationDevice(context: Context, private val primary: DeviceService) : ConversationScreenDevice {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences = ShellPreferences(context)
    private var task: String? = null
    @Volatile private var generation = 0
    @Volatile private var accepting = false
    private var manual = false
    private var inFlight = 0
    private var frame: String? = null
    private val cancelled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var window: FloatingDisplayWindow? = null
    private val probe = BackgroundDisplayProbe(this.context) { window?.render() }
    override var background = false; private set

    init {
        ShellSession.conversationChanged = { if (background && !ShellSession.ready) closeScreen() }
        current = this
    }
    override fun beginTask(id: String, continuing: Boolean) {
        if (!continuing || !preferences.backgroundEnabled) releaseDisplay()
        task = id; generation++; accepting = true; manual = false; frame = null; cancelled.clear()
        background = preferences.backgroundEnabled
    }
    override fun endTask(id: String, retainForTakeover: Boolean) {
        if (task != id) return
        accepting = false; generation++; frame = null; task = null
        if (background && retainForTakeover && probe.active && ShellSession.ready && preferences.backgroundEnabled) {
            fun drained() {
                if (task != null || !probe.active) return
                if (probe.gesturePending || inFlight > 0) { main.postDelayed(::drained, 50); return }
                manual = true
            }
            drained()
        } else releaseDisplay()
    }
    override fun closeScreen() {
        accepting = false; generation++; frame = null; manual = false
        if (task != null && background) ActiveTaskStopRegistry.stopActive()
        releaseDisplay()
    }
    private fun releaseDisplay() { window?.close(); window = null; probe.close(); frame = null; manual = false }
    private fun available() = preferences.authorized && preferences.backgroundEnabled && ShellSession.ready && Settings.canDrawOverlays(context)
    private fun checkReady() {
        check(accepting && available()) {
            text("后台屏幕不可用。请到设置恢复 Shell 连接和悬浮窗权限，或关闭后台屏幕后重新发送任务。不会改为操作主屏。",
                "Background screen unavailable. Restore the shell connection and overlay permission in Settings, or turn background screen off and send a new task. The primary screen will not be used.")
        }
    }
    private fun apps(): JSONArray {
        val pm = context.packageManager
        return JSONArray(pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }.map {
                JSONObject().put("label", it.loadLabel(pm).toString()).put("packageName", it.activityInfo.packageName)
                    .put("component", ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString())
            })
    }
    override fun execute(sessionId: String, command: DeviceCommand, complete: (Result<Any?>) -> Unit) {
        if (!background) { primary.execute(sessionId, command, complete); return }
        runCatching {
            if (command == DeviceCommand.Cancel || command == DeviceCommand.Complete) {
                if (command == DeviceCommand.Cancel) { cancelled.add(sessionId); accepting = false }
                frame = null; complete(Result.success(null)); return
            }
            if (command == DeviceCommand.Capabilities) {
                complete(Result.success(JSONObject().put("protocolVersion", 1).put("backgroundScreen", true)
                    .put("canCapture", available()).put("canPerformGestures", available()).put("canInputText", available()).put("apps", apps())))
                return
            }
            checkReady()
            check(sessionId !in cancelled) { "Background operation was cancelled" }
            when (command) {
                DeviceCommand.Size -> complete(Result.success(JSONObject().put("width", probe.width).put("height", probe.height)))
                is DeviceCommand.Capture -> {
                    val bitmap = checkNotNull(probe.bitmap) {
                        text("后台屏幕尚无应用画面，请先指定要打开的应用。", "No app is visible on the background screen. Specify an app to open first.")
                    }
                    frame = UUID.randomUUID().toString()
                    val bytes = ByteArrayOutputStream().use { out ->
                        check(bitmap.compress(if (command.jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 90, out)); out.toByteArray()
                    }
                    val geometry = JSONObject().put("frameId", frame).put("capturedAt", System.currentTimeMillis())
                        .put("displayId", probe.displayId).put("rotation", 0).put("screenWidth", bitmap.width).put("screenHeight", bitmap.height)
                        .put("imageWidth", bitmap.width).put("imageHeight", bitmap.height)
                    complete(Result.success(JSONObject().put("geometry", geometry).put("dataUrl",
                        "data:image/${if (command.jpeg) "jpeg" else "png"};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))))
                }
                is DeviceCommand.Execute -> executeAction(sessionId, command, complete)
                else -> error("Unsupported background screen command")
            }
        }.onFailure { complete(Result.failure(it)) }
    }
    private fun executeAction(session: String, command: DeviceCommand.Execute, complete: (Result<Any?>) -> Unit) {
        val action = command.action
        val run = generation
        val allowed = { accepting && generation == run && ShellSession.ready && session !in cancelled }
        if (action is NativeAction.Launch) {
            val component = PhoneOperationRoute.resolveApp(apps(), action.uri)
            check(component.isNotBlank()) { "Choose an installed app for the background screen" }
            if (!probe.active) {
                probe.start(conversation = true)
                window = FloatingDisplayWindow(context, probe, lifeos.md.tapilot.AppLanguage.resolved(context) == "zh",
                    { manual && !probe.gesturePending && runCatching { lifeos.md.tapilot.wireless.DeviceControlLease.checkAccess("local-conversation") }.isSuccess }) { closeScreen() }.also { it.show(atBottom = false, maxWidthDp = 160) }
            }
            // Clear the previous frame before launch; wait for actual pixels from the target app.
            probe.clearFrame()
            val display = checkNotNull(probe.displayId)
            runWorker(allowed, { ShellSession.requireHelper().launch(display, component) }) { result ->
                if (result.isFailure) { closeScreen(); complete(result); return@runWorker }
                fun waitForFrame(attempt: Int) {
                    if (!allowed()) { complete(Result.failure(IllegalStateException("Background task stopped"))); return }
                    if (probe.bitmap != null) { complete(Result.success(null)); return }
                    if (attempt >= 50) { closeScreen(); complete(Result.failure(IllegalStateException("App did not render on the background screen"))); return }
                    main.postDelayed({ waitForFrame(attempt + 1) }, 100)
                }
                waitForFrame(0)
            }
            return
        }
        check(probe.active && frame != null && frame == command.frameId) { "Capture a fresh background frame before acting" }
        frame = null
        val display = checkNotNull(probe.displayId)
        fun gesture(x: Float, y: Float, endX: Float = x, endY: Float = y, duration: Long = 80, done: (Result<Any?>) -> Unit = complete) {
            require(listOf(x, endX).all { it.isFinite() && it >= 0 && it < probe.width } &&
                listOf(y, endY).all { it.isFinite() && it >= 0 && it < probe.height })
            require(duration in 1..5000)
            probe.dispatchPath(Path().apply { moveTo(x, y); lineTo(endX, endY) }, duration, allowed) { done(it.map { null }) }
        }
        fun input(value: String, replace: Boolean, dismiss: Boolean) {
            check(allowed())
            if (BackgroundTextInput.set(display, value, replace)) {
                if (dismiss) runWorker(allowed, { ShellSession.requireHelper().key(display, 111) }, complete)
                else complete(Result.success(null))
            } else {
                check(!replace) { "Replacing text on this background app requires device control accessibility" }
                runWorker(allowed, {
                    ShellSession.requireHelper().text(display, value)
                    if (dismiss) ShellSession.requireHelper().key(display, 111)
                }, complete)
            }
        }
        when (action) {
            is NativeAction.Tap -> gesture(action.x, action.y, duration = action.duration ?: 80)
            is NativeAction.LongPress -> gesture(action.x, action.y, duration = action.duration ?: 800)
            is NativeAction.Swipe -> gesture(action.startX, action.startY, action.endX, action.endY, action.duration ?: 300)
            is NativeAction.DoubleTap -> gesture(action.x, action.y) { first ->
                if (first.isFailure) complete(first) else runCatching { gesture(action.x, action.y) }.onFailure { complete(Result.failure(it)) }
            }
            NativeAction.Back -> runWorker(allowed, { ShellSession.requireHelper().key(display, 4) }, complete)
            is NativeAction.Key -> {
                val code = backgroundKeyCode(action.keyName)
                runWorker(allowed, { ShellSession.requireHelper().key(display, code) }, complete)
            }
            is NativeAction.Input -> {
                if (action.x != null && action.y != null) gesture(action.x, action.y) { tapped ->
                    if (tapped.isFailure) complete(tapped)
                    else main.postDelayed({ runCatching { input(action.text, action.replace, action.autoDismissKeyboard) }.onFailure { complete(Result.failure(it)) } }, 150)
                } else input(action.text, action.replace, action.autoDismissKeyboard)
            }
            is NativeAction.ClearInput -> {
                if (action.x != null && action.y != null) gesture(action.x, action.y) { tapped ->
                    if (tapped.isFailure) complete(tapped)
                    else main.postDelayed({ runCatching { input("", true, false) }.onFailure { complete(Result.failure(it)) } }, 150)
                } else input("", true, false)
            }
            else -> throw UnsupportedOperationException(text("后台屏幕暂不支持此操作，请关闭后台屏幕后重新发送任务。", "This action is unavailable on the background screen. Turn background screen off before sending a new task."))
        }
    }
    private fun runWorker(allowed: () -> Boolean, action: () -> Unit, complete: (Result<Any?>) -> Unit) {
        inFlight++
        worker.execute {
            val result = runCatching { check(allowed()) { "Background task stopped" }; action(); null }
            main.post { inFlight--; complete(if (allowed()) result else Result.failure(IllegalStateException("Background task stopped"))) }
        }
    }
    companion object { internal var current: BackgroundConversationDevice? = null; private set }
}

internal fun backgroundKeyCode(name: String): Int = when (name.lowercase().removePrefix("keycode_")) {
    "back" -> 4; "enter", "return" -> 66; "backspace", "del" -> 67; "delete", "forward_del" -> 112
    "tab" -> 61; "escape", "esc" -> 111; "arrowup", "dpad_up" -> 19; "arrowdown", "dpad_down" -> 20
    "arrowleft", "dpad_left" -> 21; "arrowright", "dpad_right" -> 22; "move_home" -> 122; "move_end" -> 123
    else -> error("Unsupported background key: $name")
}
