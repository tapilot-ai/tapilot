package lifeos.md.tapilot.wireless

import org.junit.Assert.*
import org.junit.Test

class WirelessListenerOwnerTest {
    private class Listener : AutoCloseable {
        var closed = false
        override fun close() { closed = true }
    }

    @Test fun `Wi-Fi loss releases connection and reconnect accepts original pairing`() {
        var stored = ""
        val pairing = WirelessPairing({ stored }, { stored = it })
        val credential = pairing.redeem(pairing.offer())
        val owner = WirelessListenerOwner<String, Listener>()
        val original = Listener()
        assertTrue(owner.install(owner.change("wifi:192.168.1.10")!!, original))
        owner.change(null)
        assertTrue(original.closed)
        assertNull(owner.listener)
        assertTrue(pairing.authenticate(credential))
        val restored = Listener()
        assertTrue(owner.install(owner.change("wifi:192.168.1.20")!!, restored))
        assertFalse(restored.closed)
        // Service recreation reads the same persisted credential hash.
        assertTrue(WirelessPairing({ stored }, { stored = it }).authenticate(credential))
    }

    @Test fun `rapid network changes cannot install an obsolete async listener`() {
        val owner = WirelessListenerOwner<String, Listener>()
        val obsoleteTicket = owner.change("wifi-A")!!
        owner.change(null)
        val currentTicket = owner.change("wifi-B")!!
        val current = Listener()
        assertTrue(owner.install(currentTicket, current))
        val late = Listener()
        assertFalse(owner.install(obsoleteTicket, late))
        assertTrue(late.closed)
        assertSame(current, owner.listener)
        assertFalse(current.closed)
        assertFalse(owner.isCurrent(obsoleteTicket))
    }

    @Test fun `unchanged network keeps connection while changed address releases it`() {
        val owner = WirelessListenerOwner<String, Listener>()
        val old = Listener()
        val ticket = owner.change("wifi:old-address")!!
        owner.install(ticket, old)
        assertNull(owner.change("wifi:old-address"))
        assertFalse(old.closed)
        assertNotNull(owner.change("wifi:new-address"))
        assertTrue(old.closed)
        assertFalse(owner.isCurrent(ticket))
    }

    @Test fun `explicit stop blocks network callbacks and closes an in-flight listener`() {
        val owner = WirelessListenerOwner<String, Listener>()
        val ticket = owner.change("wifi")!!
        owner.close()
        assertNull(owner.change("wifi-restored"))
        val late = Listener()
        assertFalse(owner.install(ticket, late))
        assertTrue(late.closed)
        assertNull(owner.listener)
    }
}
