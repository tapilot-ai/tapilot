package lifeos.md.tapilot.wireless

import lifeos.md.tapilot.AppText.text as appText
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.MainActivity
import org.json.JSONObject
import java.net.Inet4Address
import java.util.Base64
import java.util.concurrent.Executors

internal object WirelessState {
    var enabled by mutableStateOf(false)
    var address by mutableStateOf("")
    var connected by mutableStateOf(false)
    var error by mutableStateOf("")
    var paired by mutableStateOf(false)
    var pairingStage by mutableStateOf(PairingStage.IDLE)
    var pairingError by mutableStateOf("")
}

internal enum class PairingStage { IDLE, SENDING, WAITING, PAIRED, FAILED }

class WirelessControlService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val serverLock = Any()
    private var server: WirelessServer? = null
    private var tls: WirelessTls? = null
    private var discovery: WirelessDiscovery? = null
    private lateinit var pairing: WirelessPairing
    private var wifi: Network? = null
    @Volatile private var stopping = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) { if (network == wifi) handler.post { stopSelf() } }
        override fun onLinkPropertiesChanged(network: Network, properties: android.net.LinkProperties) {
            if (network == wifi && WirelessState.address.isNotEmpty() && properties.linkAddresses.none {
                it.address.hostAddress == WirelessState.address
            }) handler.post { stopSelf() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        current = this
        WirelessState.error = ""
        WirelessState.enabled = true
        val preferences = getSharedPreferences("wireless-pairing", Context.MODE_PRIVATE)
        WirelessState.paired = preferences.getString("credentialHash", "").orEmpty().isNotEmpty()
        WirelessState.pairingStage = PairingStage.IDLE
        WirelessState.pairingError = ""
        pairing = WirelessPairing({ preferences.getString("credentialHash", "").orEmpty() }, {
            check(preferences.edit().putString("credentialHash", it).commit()) { "Unable to save pairing" }
            val paired = it.isNotEmpty()
            handler.post {
                if (!stopping && current === this) {
                    WirelessState.paired = paired
                    WirelessState.pairingStage = if (paired) PairingStage.PAIRED else PairingStage.IDLE
                }
            }
        })
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            CHANNEL, label("无线控制", "Wireless control"), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification())
        val connectivity = getSystemService(ConnectivityManager::class.java)
        // VPN capabilities can also include WIFI for their underlying network.
        // Pairing and the LAN listener must use the physical Wi-Fi interface.
        wifi = connectivity.allNetworks.firstOrNull {
            connectivity.getNetworkCapabilities(it)?.let { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        }
        val address = wifi?.let { connectivity.getLinkProperties(it) }?.linkAddresses
            ?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress }
        if (address == null) {
            WirelessState.error = label("请先连接 Wi-Fi，再开启无线控制。", "Connect to Wi-Fi before enabling wireless control.")
            stopSelf(); return
        }
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), networkCallback)
        worker.execute {
            runCatching {
                val identity = WirelessTls()
                val listener = synchronized(serverLock) {
                    if (stopping) return@execute
                    WirelessServer(identity.context(), address, PORT, pairing,
                    { disconnect -> WirelessDeviceSession(applicationContext, disconnect) },
                    { connected -> handler.post {
                        if (!stopping) { WirelessState.connected = connected; getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification()) }
                    } }).also { server = it }
                }
                handler.post {
                    if (stopping) listener.close() else {
                        tls = identity
                        WirelessState.address = address.hostAddress.orEmpty()
                        listener.start()
                        discovery = WirelessDiscovery(applicationContext).also { discovery ->
                            runCatching { discovery.start(listener.port, identity.fingerprint) }
                                .onFailure { discovery.close() }
                        }
                    }
                }
            }.onFailure { error -> handler.post {
                if (!stopping && current === this) {
                    WirelessState.error = error.message ?: "Unable to start wireless control"
                    stopSelf()
                }
            } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stopSelf()
        return START_NOT_STICKY
    }

    internal fun pairingLink(): String {
        check(!WirelessState.connected) { "Disconnect the controller before pairing another computer" }
        val identity = checkNotNull(tls) { "Wireless control is still starting" }
        val data = JSONObject().put("version", 1).put("host", WirelessState.address).put("port", checkNotNull(server).port)
            .put("fingerprint", identity.fingerprint).put("token", pairing.offer())
            .put("language", AppLanguage.resolved(this))
        return "tapilot://pair/" + Base64.getUrlEncoder().withoutPadding().encodeToString(data.toString().toByteArray())
    }

    internal fun pairComputer(qr: String) {
        check(WirelessState.pairingStage != PairingStage.SENDING &&
            WirelessState.pairingStage != PairingStage.WAITING) { "Pairing is already in progress" }
        WirelessState.pairingStage = PairingStage.SENDING
        WirelessState.pairingError = ""
        val prepared = runCatching { pairingLink() to checkNotNull(wifi) { appText("请先连接 Wi-Fi", "Connect to Wi-Fi first") } }
        fun fail(error: Throwable) {
            WirelessState.pairingStage = PairingStage.FAILED
            WirelessState.pairingError = error.message?.takeIf { it.isNotBlank() }
                ?: label("配对失败，请检查 Wi-Fi 并重新扫码。", "Pairing failed. Check Wi-Fi and scan again.")
        }
        prepared.onFailure { fail(it) }.onSuccess { (link, network) ->
            worker.execute {
                val result = runCatching { ComputerPairing.send(qr, link, network) }
                handler.post {
                    if (stopping || current !== this || WirelessState.pairingStage == PairingStage.PAIRED) return@post
                    result.fold({
                        WirelessState.pairingStage = PairingStage.WAITING
                        handler.postDelayed({
                            if (!stopping && current === this && WirelessState.pairingStage == PairingStage.WAITING) {
                                fail(IllegalStateException(label("等待电脑确认超时，请重新打开电脑配对窗口并扫码。",
                                    "Computer confirmation timed out. Open a new pairing window and scan again.")))
                            }
                        }, 300_000)
                    }, { fail(it) })
                }
            }
        }
    }

    internal fun revoke() { pairing.revoke(); stopSelf() }

    override fun onDestroy() {
        stopping = true
        discovery?.close(); discovery = null
        synchronized(serverLock) { server?.close(); server = null }
        worker.shutdownNow()
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        if (current === this) current = null
        WirelessState.enabled = false; WirelessState.connected = false; WirelessState.address = ""
        if (WirelessState.pairingStage == PairingStage.SENDING || WirelessState.pairingStage == PairingStage.WAITING) {
            WirelessState.pairingStage = PairingStage.FAILED
            WirelessState.pairingError = label("无线控制已停止，请重新开启后配对。", "Wireless control stopped. Enable it and pair again.")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun label(zh: String, en: String) = if (AppLanguage.resolved(this) == "zh") zh else en
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 0, Intent(this, WirelessControlService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(label("Tapilot 无线控制", "Tapilot wireless control"))
            .setContentText(if (WirelessState.connected) label("电脑正在控制手机", "Computer is controlling this phone") else label("等待已配对的电脑连接", "Waiting for a paired computer"))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, label("停止", "Stop"), stop).build()).build()
    }
    companion object {
        internal var current: WirelessControlService? = null
            private set
        internal const val PORT = 39877
        private const val CHANNEL = "tapilot_wireless"
        private const val NOTIFICATION = 2115
        private const val STOP = "lifeos.md.tapilot.wireless.STOP"
    }
}
