package lifeos.md.tapilot.wireless

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WirelessControlTest {
    @Test fun `pairing is one use expiring and revocable`() {
        var now = 0L; var stored = ""
        val pairing = WirelessPairing({ stored }, { stored = it }, { now })
        val code = pairing.offer()
        assertFalse(pairing.authenticate(code))
        val token = pairing.redeem(code)
        assertNotEquals(token, stored)
        assertTrue(pairing.authenticate(token))
        assertThrows(IllegalStateException::class.java) { pairing.redeem(code) }
        val second = pairing.offer(); now = 300_001
        assertThrows(IllegalStateException::class.java) { pairing.redeem(second) }
        pairing.revoke(); assertFalse(pairing.authenticate(token))
    }
    @Test fun `coordinates map image pixels and frame is consumed`() {
        val policy = WirelessActionPolicy { 0 }
        policy.observe(geometry())
        val action = policy.prepare(tap(500, 1000)).getJSONObject("action")
        assertEquals(250.0, action.getDouble("x"), 0.0)
        assertEquals(500.0, action.getDouble("y"), 0.0)
        assertThrows(IllegalStateException::class.java) { policy.prepare(tap(500, 1000)) }
    }
    @Test fun `reject old frame mismatched frame out of bounds and arbitrary launch`() {
        var now = 0L
        val policy = WirelessActionPolicy { now }
        policy.observe(geometry())
        assertThrows(IllegalStateException::class.java) { policy.prepare(tap(1, 1).put("frameId", "wrong")) }
        assertThrows(IllegalArgumentException::class.java) { policy.prepare(tap(1000, 1)) }
        now = 60_000
        assertThrows(IllegalStateException::class.java) { policy.prepare(tap(1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { policy.prepare(JSONObject().put("action", JSONObject().put("type", "launch").put("uri", "https://example.com"))) }
    }
    @Test fun `wireless lease prevents another session and cannot be released by it`() {
        DeviceControlLease.acquire("wireless-test")
        try {
            DeviceControlLease.checkAccess("wireless-test")
            DeviceControlLease.release("someone-else")
            assertThrows(IllegalStateException::class.java) { DeviceControlLease.checkAccess("local") }
            assertThrows(IllegalStateException::class.java) { DeviceControlLease.acquire("other") }
        } finally { DeviceControlLease.release("wireless-test") }
        DeviceControlLease.checkAccess("local")
    }
    @Test fun `transport rejects oversized and partial requests`() {
        assertEquals("{}", WirelessServer.readLine("{}\n".byteInputStream()))
        assertNull(WirelessServer.readLine("".byteInputStream()))
        assertThrows(IllegalStateException::class.java) { WirelessServer.readLine("{".byteInputStream()) }
        assertThrows(IllegalArgumentException::class.java) { WirelessServer.readLine("a".repeat(65_537).byteInputStream()) }
    }
    private fun geometry() = JSONObject("""{"frameId":"frame","imageWidth":1000,"imageHeight":2000,"screenWidth":500,"screenHeight":1000}""")
    private fun tap(x: Int, y: Int) = JSONObject().put("frameId", "frame").put("action", JSONObject().put("type", "tap").put("x", x).put("y", y))
}
