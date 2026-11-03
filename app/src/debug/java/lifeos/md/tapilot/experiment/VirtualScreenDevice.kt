package lifeos.md.tapilot.experiment

import android.graphics.Bitmap
import android.graphics.Path
import android.util.Base64
import lifeos.md.tapilot.accessibility.NativeAction
import lifeos.md.tapilot.device.DeviceCommand
import lifeos.md.tapilot.device.DeviceService
import lifeos.md.tapilot.shell.ShellSession
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/** A run-scoped adapter with no reference to AndroidDeviceService or the primary display. */
internal class VirtualScreenDevice(
    private val probe: BackgroundDisplayProbe,
    private val allowed: () -> Boolean,
) : DeviceService {
    private val display = checkNotNull(probe.displayId).also { check(it > 0) }
    private var frame: String? = null
    override fun execute(sessionId: String, command: DeviceCommand, complete: (Result<Any?>) -> Unit) {
        runCatching {
            if (command == DeviceCommand.Cancel || command == DeviceCommand.Complete) {
                frame = null; complete(Result.success(null)); return
            }
            check(allowed() && ShellSession.ready && probe.displayId == display) { "Virtual screen task is stopped or paused" }
            when (command) {
                DeviceCommand.Capabilities -> complete(Result.success(JSONObject().put("protocolVersion", 1)
                    .put("canCapture", true).put("canPerformGestures", true).put("canInputText", false)))
                DeviceCommand.Size -> complete(Result.success(JSONObject().put("width", BackgroundDisplayProbe.WIDTH).put("height", BackgroundDisplayProbe.HEIGHT)))
                is DeviceCommand.Capture -> {
                    val bitmap = checkNotNull(probe.bitmap) { "Waiting for the virtual screen frame" }
                    frame = UUID.randomUUID().toString()
                    val bytes = ByteArrayOutputStream().use { out ->
                        check(bitmap.compress(if (command.jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 90, out))
                        out.toByteArray()
                    }
                    val geometry = JSONObject().put("frameId", frame).put("capturedAt", System.currentTimeMillis())
                        .put("displayId", display).put("rotation", 0)
                        .put("screenWidth", bitmap.width).put("screenHeight", bitmap.height)
                        .put("imageWidth", bitmap.width).put("imageHeight", bitmap.height)
                    complete(Result.success(JSONObject().put("geometry", geometry).put("dataUrl",
                        "data:image/${if (command.jpeg) "jpeg" else "png"};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))))
                }
                is DeviceCommand.Execute -> {
                    check(frame != null && frame == command.frameId) { "Capture this virtual screen before acting" }
                    val action = command.action
                    val path = Path()
                    val duration = when (action) {
                        is NativeAction.Tap -> { point(action.x, action.y); path.moveTo(action.x, action.y); action.duration ?: 80 }
                        is NativeAction.Swipe -> {
                            point(action.startX, action.startY); point(action.endX, action.endY)
                            path.moveTo(action.startX, action.startY); path.lineTo(action.endX, action.endY); action.duration ?: 300
                        }
                        else -> throw UnsupportedOperationException("This experimental screen supports taps and swipes only; no main-screen fallback")
                    }
                    require(duration in 1..5000)
                    frame = null
                    probe.dispatchPath(path, duration, allowed) { complete(it.map { null }) }
                }
                else -> error("Unsupported virtual screen command")
            }
        }.onFailure { complete(Result.failure(it)) }
    }
    private fun point(x: Float, y: Float) {
        require(x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x < BackgroundDisplayProbe.WIDTH && y < BackgroundDisplayProbe.HEIGHT)
    }
}
