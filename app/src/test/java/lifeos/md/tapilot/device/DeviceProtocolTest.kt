package lifeos.md.tapilot.device

import lifeos.md.tapilot.accessibility.NativeAction
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceProtocolTest {
    @Test fun parsesSharedWireFixturesIntoTypedCommands() {
        val fixture = JSONObject(checkNotNull(javaClass.getResource("/device.json")).readText())
        assertEquals(1, fixture.getInt("protocolVersion"))
        val actions = fixture.getJSONArray("actions")
        for (i in 0 until actions.length()) {
            val command = DeviceProtocol.parse("device.execute", JSONObject().put("action", actions.getJSONObject(i)).put("frameId", fixture.getString("frameId"))) as DeviceCommand.Execute
            assertEquals("contract-frame", command.frameId)
            if (i == 0) assertEquals(NativeAction.Tap(12f, 34f, null), command.action)
            if (i == 1) assertEquals(NativeAction.Input("你好 Tapilot", null, null, false, false), command.action)
        }
    }
    @Test fun previewAndPreparedActionKeepSeparateFrameSemantics() {
        assertEquals(DeviceCommand.Capture(preview = true, jpeg = true), DeviceProtocol.parse("device.preview", JSONObject().put("format", "jpeg")))
        assertEquals(DeviceCommand.Capture(metadataOnly = true), DeviceProtocol.parse("device.prepareAction", JSONObject()))
        assertEquals(DeviceCommand.Cancel, DeviceProtocol.parse("task.cancel", JSONObject()))
    }
    @Test fun fullFrameCaptureCodecIsExplicitAndValidated() {
        assertEquals(DeviceCommand.Capture(), DeviceProtocol.parse("device.capture", JSONObject()))
        assertEquals(DeviceCommand.Capture(jpeg = true), DeviceProtocol.parse("device.capture", JSONObject().put("format", "jpeg")))
        assertThrows(IllegalArgumentException::class.java) { DeviceProtocol.parse("device.capture", JSONObject().put("format", "gif")) }
    }
    @Test fun rejectsUnknownOperationsAtTheBoundary() {
        assertThrows(UnsupportedOperationException::class.java) { DeviceProtocol.parse("device.shell", JSONObject()) }
        assertThrows(Exception::class.java) { DeviceProtocol.parse("device.execute", JSONObject()) }
    }
}
