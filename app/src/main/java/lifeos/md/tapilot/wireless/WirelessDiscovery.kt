package lifeos.md.tapilot.wireless

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build

/** Advertises only public service identity; pairing secrets never enter DNS-SD. */
internal class WirelessDiscovery(context: Context) : AutoCloseable {
    private val manager = context.getSystemService(NsdManager::class.java)
    @Volatile private var closed = false
    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { if (closed) runCatching { manager.unregisterService(this) } }
        override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
        override fun onServiceUnregistered(info: NsdServiceInfo) {}
        override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
    }
    fun start(port: Int, fingerprint: String) {
        val info = NsdServiceInfo().apply {
            serviceName = "Tapilot-${fingerprint.take(8)}"
            serviceType = "_tapilot._tcp."
            this.port = port
            setAttribute("version", "1")
            setAttribute("fingerprint", fingerprint)
            setAttribute("model", Build.MODEL.take(80))
        }
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }
    override fun close() {
        // Unregister even if the asynchronous registration callback is pending.
        closed = true
        runCatching { manager.unregisterService(listener) }
    }
}
