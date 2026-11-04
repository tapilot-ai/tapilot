package lifeos.md.tapilot.shell

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import android.view.Surface
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit

/** Restricted operations on one owned display. No command strings or primary-display input API. */
internal class ShellDisplayService(context: Context, private val appUid: Int) : IDisplayHelper.Stub() {
    private val main = Handler(Looper.getMainLooper())
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var display: VirtualDisplay? = null
    private var width = 0
    private var height = 0
    private var client: IBinder? = null
    // The app Binder owns the lifetime. Client death or explicit revocation terminates us.
    private fun authenticate() { check(Binder.getCallingUid() == appUid) { "Wrong caller" } }
    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(block)
        main.post(task)
        return try {
            task.get(6, TimeUnit.SECONDS)
        } catch (failure: ExecutionException) {
            // Binder can marshal IllegalStateException, but not ExecutionException.
            throw IllegalStateException(failure.cause?.message ?: "Shell operation failed")
        } catch (failure: TimeoutException) {
            task.cancel(false)
            main.post { terminate() }
            throw IllegalStateException("Shell operation timed out; session is closing")
        }
    }
    override fun identity(): Int { authenticate(); return Process.myUid() }
    override fun watchClient(value: IBinder) {
        authenticate()
        onMain {
            check(client == null)
            value.linkToDeath({ main.post { terminate() } }, 0)
            client = value
        }
    }
    @SuppressLint("WrongConstant") // AOSP hidden display flags, used only by the shell process.
    override fun create(surface: Surface, width: Int, height: Int, density: Int): Int {
        authenticate()
        require(width in 240..2560 && height in 240..2560 && width.toLong() * height <= 4_194_304 && density in 80..800 && surface.isValid)
        return onMain {
            check(client?.isBinderAlive == true && display == null)
            // AOSP DisplayManager hidden flags: touch (6), destroy content (8), trusted (10),
            // own focus (14), and do not steal top focus (16). Fail closed if unavailable.
            var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or (1 shl 6) or (1 shl 8)
            if (Build.VERSION.SDK_INT >= 33) flags = flags or (1 shl 10)
            if (Build.VERSION.SDK_INT >= 34) flags = flags or (1 shl 14) or (1 shl 16)
            try {
                val created = checkNotNull(displays.createVirtualDisplay("Tapilot authorized shell display", width, height, density, surface, flags))
                check(created.display.displayId > 0)
                display = created; this.width = width; this.height = height
                created.display.displayId
            } finally { surface.release() }
        }
    }
    private fun owned(id: Int) {
        check(id > 0 && display?.display?.displayId == id) { "Display is not owned by this session" }
    }
    override fun gesture(displayId: Int, points: FloatArray, duration: Long): Boolean {
        authenticate()
        require(points.size in 4..512 && points.size % 2 == 0 && duration in 1..5000)
        // Dispatch is serialized with release/revoke, and bounded to five seconds.
        return onMain {
            owned(displayId)
            require(points.indices.all { i -> points[i].isFinite() && points[i] >= 0 && points[i] < if (i % 2 == 0) width else height })
            val manager = Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null)
            val inject = manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            val setDisplay = InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
            val start = SystemClock.uptimeMillis()
            fun send(action: Int, index: Int): Boolean {
                val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, points[index], points[index + 1], 0)
                return try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    setDisplay.invoke(event, displayId)
                    inject.invoke(manager, event, 2) as Boolean
                } finally { event.recycle() }
            }
            var pressed = false
            try {
                check(send(MotionEvent.ACTION_DOWN, 0)); pressed = true
                val count = points.size / 2
                for (i in 1 until count) {
                    val wait = start + duration * i / (count - 1) - SystemClock.uptimeMillis()
                    if (wait > 0) SystemClock.sleep(wait)
                    check(send(if (i == count - 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE, i * 2))
                }
                pressed = false
                true
            } finally { if (pressed) runCatching { send(MotionEvent.ACTION_CANCEL, points.size - 2) } }
        }
    }
    private fun sendKeys(displayId: Int, events: Array<android.view.KeyEvent>) {
        owned(displayId)
        val manager = Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null)
        val inject = manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        val setDisplay = InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
        for (event in events) {
            setDisplay.invoke(event, displayId)
            check(inject.invoke(manager, event, 2) as Boolean) { "Secondary-display key injection failed" }
        }
    }
    override fun key(displayId: Int, keyCode: Int) {
        authenticate()
        require(keyCode in setOf(4, 19, 20, 21, 22, 23, 61, 66, 67, 111, 112, 122, 123))
        onMain {
            val now = SystemClock.uptimeMillis()
            sendKeys(displayId, arrayOf(android.view.KeyEvent(now, now, 0, keyCode, 0), android.view.KeyEvent(now, now, 1, keyCode, 0)))
        }
    }
    override fun text(displayId: Int, value: String) {
        authenticate()
        require(value.length <= 2000)
        onMain {
            owned(displayId)
            val events = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(value.toCharArray())
            checkNotNull(events) { "This text needs display-scoped accessibility input" }
            sendKeys(displayId, events)
        }
    }
    override fun launch(displayId: Int, component: String) {
        authenticate()
        val target = requireNotNull(ComponentName.unflattenFromString(component))
        require(target.packageName.matches(Regex("[a-zA-Z0-9_.]+")) && target.className.matches(Regex("[a-zA-Z0-9_.$]+")))
        onMain {
            owned(displayId)
            // Argument array only; no shell interpolation and no launch without an explicit display.
            val process = ProcessBuilder("/system/bin/am", "start", "--display", displayId.toString(),
                "-f", "0x18000000", "-n", target.flattenToString()).redirectErrorStream(true).start()
            check(process.waitFor(4, TimeUnit.SECONDS)) { process.destroyForcibly(); "Launch timed out" }
            val output = process.inputStream.bufferedReader().readText().take(2048)
            check(process.exitValue() == 0 && !output.contains("Error:") && !output.contains("Exception")) { "System refused secondary-display launch" }
        }
    }
    override fun release(displayId: Int) { authenticate(); onMain { if (display == null) return@onMain; owned(displayId); releaseOwned() } }
    private fun releaseOwned() { display?.release(); display = null }
    override fun shutdown() { authenticate(); onMain { releaseOwned(); main.postDelayed({ terminate() }, 100) } }
    private fun terminate() { releaseOwned(); Process.killProcess(Process.myPid()) }
}
