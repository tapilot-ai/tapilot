package lifeos.md.tapilot.device

import android.content.Context
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.accessibility.deviceDisplayMetrics
import lifeos.md.tapilot.wireless.DeviceControlLease
import org.json.JSONObject

/** Owns device access policy and action execution, independently of any UI or transport. */
class AndroidDeviceService(context: Context) : DeviceService {
    private val context = context.applicationContext
    override fun execute(sessionId: String, command: DeviceCommand, complete: (Result<Any?>) -> Unit) {
        runCatching {
            if (command != DeviceCommand.Cancel && command != DeviceCommand.Complete) DeviceControlLease.checkAccess(sessionId)
            val service = MidsceneAccessibilityService.current()
            when (command) {
                DeviceCommand.Capabilities -> complete(Result.success((service?.capabilityDetails() ?: JSONObject())
                    .put("protocolVersion", 1).put("canCapture", service != null)
                    .put("canPerformGestures", service != null).put("canInputText", service != null)))
                DeviceCommand.Size -> {
                    val size = context.deviceDisplayMetrics()
                    complete(Result.success(JSONObject().put("width", size.widthPixels).put("height", size.heightPixels)))
                }
                is DeviceCommand.Capture -> (service ?: throw AccessibilityDisabledException()).capture(sessionId,
                    metadataOnly = command.metadataOnly, recordFrame = !command.preview, jpeg = command.jpeg) { result ->
                    complete(result.map { frame ->
                        if (command.preview) JSONObject().put("dataUrl", frame.dataUrl).put("capturedAt", frame.capturedAt)
                        else JSONObject().put("dataUrl", frame.dataUrl).put("geometry", frame.geometry())
                    })
                }
                is DeviceCommand.Execute -> {
                    val device = service ?: throw AccessibilityDisabledException()
                    command.frameId?.let(device::validateFrame)
                    device.execute(sessionId, command.action) { complete(it.map { null }) }
                }
                DeviceCommand.Cancel -> { service?.cancelSession(sessionId); complete(Result.success(null)) }
                DeviceCommand.Complete -> { service?.completeSession(sessionId); complete(Result.success(null)) }
            }
        }.onFailure { complete(Result.failure(it)) }
    }
}

private fun MidsceneAccessibilityService.CapturePayload.geometry() = JSONObject()
    .put("frameId", frameId).put("capturedAt", capturedAt).put("displayId", 0).put("rotation", rotation)
    .put("screenWidth", screenWidth).put("screenHeight", screenHeight)
    .put("imageWidth", imageWidth).put("imageHeight", imageHeight)
