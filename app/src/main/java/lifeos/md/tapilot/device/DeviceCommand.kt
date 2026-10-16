package lifeos.md.tapilot.device

import lifeos.md.tapilot.accessibility.NativeAction
import lifeos.md.tapilot.bridge.NativeActionParser
import org.json.JSONObject

/** Commands shared by native callers and the stdio, wireless and legacy transports. */
sealed interface DeviceCommand {
    data object Capabilities : DeviceCommand
    data object Size : DeviceCommand
    data class Capture(val metadataOnly: Boolean = false, val preview: Boolean = false, val jpeg: Boolean = false) : DeviceCommand
    data class Execute(val action: NativeAction, val frameId: String?) : DeviceCommand
    data object Cancel : DeviceCommand
    data object Complete : DeviceCommand
}

/** Parse only at a transport boundary; device execution never dispatches on method strings. */
object DeviceProtocol {
    fun parse(method: String, params: JSONObject): DeviceCommand = when (method) {
        "device.getCapabilities" -> DeviceCommand.Capabilities
        "device.getSize" -> DeviceCommand.Size
        "device.capture" -> {
            val format = params.optString("format", "png")
            require(format in setOf("png", "jpeg")) { "Unsupported capture format" }
            DeviceCommand.Capture(jpeg = format == "jpeg")
        }
        "device.prepareAction" -> DeviceCommand.Capture(metadataOnly = true)
        "device.preview" -> DeviceCommand.Capture(preview = true, jpeg = params.optString("format") == "jpeg")
        "device.execute" -> DeviceCommand.Execute(NativeActionParser.parse(params.getJSONObject("action")), params.optString("frameId").takeIf { it.isNotBlank() })
        "task.cancel" -> DeviceCommand.Cancel
        "task.complete" -> DeviceCommand.Complete
        else -> throw UnsupportedOperationException("Unsupported device method: $method")
    }
}

fun interface DeviceService {
    /** Must run on the Android main thread. Completion may be asynchronous. */
    fun execute(sessionId: String, command: DeviceCommand, complete: (Result<Any?>) -> Unit)
}

class AccessibilityDisabledException : IllegalStateException("Enable Tapilot device control in Accessibility settings")
