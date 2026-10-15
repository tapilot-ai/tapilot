package lifeos.md.tapilot.wireless

import android.app.KeyguardManager
import lifeos.md.tapilot.device.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import lifeos.md.tapilot.TapilotApplication
import lifeos.md.tapilot.TaskExecutionStatus
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.bridge.ActiveTaskStopRegistry
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class WirelessDeviceSession(private val context: Context, private val disconnect: () -> Unit, private val device: DeviceService = AndroidDeviceService(context)) : AutoCloseable {
    private val id = "wireless-${UUID.randomUUID()}"
    private val handler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private var registration: ActiveTaskStopRegistry.Registration? = null
    private val actionPolicy = WirelessActionPolicy()
    private var executing = false

    fun acquire() = onMain<Unit> { done ->
        check(!context.getSystemService(KeyguardManager::class.java).isDeviceLocked) { "Unlock the phone first" }
        check(MidsceneAccessibilityService.current() != null) { "Enable Tapilot device control in Accessibility settings" }
        check(!(context.applicationContext as TapilotApplication).assistant.state.running) { "A local conversation is running. Stop it before connecting." }
        registration = ActiveTaskStopRegistry.register { close(); disconnect(); true }
        try { DeviceControlLease.acquire(id) } catch (error: Throwable) {
            ActiveTaskStopRegistry.unregister(registration); registration = null; throw error
        }
        done(Result.success(Unit))
    }

    fun call(method: String, params: JSONObject): Any? = try {
        callOnDevice(method, params)
    } finally {
        if (method == "device.execute") handler.post {
            if (!closed.get()) {
                executing = false
                MidsceneAccessibilityService.current()?.updateTaskStatus(TaskExecutionStatus.IDLE, null)
            }
        }
    }

    private fun callOnDevice(method: String, params: JSONObject): Any? {
        require(method in setOf("device.getCapabilities", "device.getSize", "device.capture", "device.prepareAction", "device.preview", "device.execute")) { "Unsupported wireless method" }
        val prepared = if (method == "device.execute") actionPolicy.prepare(params) else params
        val result = onMain<Any?> { done ->
            check(!context.getSystemService(KeyguardManager::class.java).isDeviceLocked) { "Phone is locked. Unlock it on the device." }
            if (method == "device.execute") {
                executing = true
                MidsceneAccessibilityService.current()?.updateTaskStatus(TaskExecutionStatus.RUNNING, null)
            }
            device.execute(id, DeviceProtocol.parse(method, prepared), done)
        }
        if (method in setOf("device.capture", "device.prepareAction", "device.preview")) {
            check(!context.getSystemService(KeyguardManager::class.java).isDeviceLocked) { "Phone is locked. Unlock it on the device." }
        }
        if (method == "device.capture" || method == "device.prepareAction") actionPolicy.observe((result as JSONObject).getJSONObject("geometry"))
        if (method == "device.prepareAction") return JSONObject().put("geometry", (result as JSONObject).getJSONObject("geometry"))
        return result
    }

    private fun <T> onMain(block: ((Result<T>) -> Unit) -> Unit): T {
        val result = CompletableFuture<T>()
        handler.post {
            runCatching {
                check(!closed.get()) { "Wireless session closed" }
                block { it.fold(result::complete, result::completeExceptionally) }
            }.onFailure(result::completeExceptionally)
        }
        return try { result.get(25, TimeUnit.SECONDS) } catch (error: java.util.concurrent.TimeoutException) {
            close(); disconnect(); throw IllegalStateException("Device action timed out; outcome unknown. Reconnect and inspect the screen before retrying.", error)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        handler.post {
            // Keep cancellation marked so delayed input/navigation callbacks cannot run after disconnect.
            MidsceneAccessibilityService.current()?.cancelSession(id)
            DeviceControlLease.release(id)
            if (registration != null) MidsceneAccessibilityService.current()?.updateTaskStatus(if (executing) TaskExecutionStatus.STOPPED else TaskExecutionStatus.IDLE, null)
            executing = false
            ActiveTaskStopRegistry.unregister(registration)
            registration = null
        }
    }
}
