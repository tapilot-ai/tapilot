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
    var waitingForWifi by mutableStateOf(false)
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
    private val listeners = WirelessListenerOwner<Pair<Network, Inet4Address>, WirelessServer>()
    private val networks = linkedMapOf<Network, Inet4Address>()
    private var tls: WirelessTls? = null
    private var discovery: WirelessDiscovery? = null
    private lateinit var pairing: WirelessPairing
    private var registered = false
    private var pairingAttempt = 0L
    @Volatile private var stopping = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetwork(network)
        override fun onLost(network: Network) {
            handler.post { networks.remove(network); reconcileNetwork() }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            updateNetwork(network, capabilities = capabilities)
        override fun onLinkPropertiesChanged(network: Network, properties: android.net.LinkProperties) =
            updateNetwork(network, properties = properties)
    }

    private fun updateNetwork(network: Network, capabilities: NetworkCapabilities? = null,
                              properties: android.net.LinkProperties? = null) {
        handler.post {
            if (stopping) return@post
            val connectivity = getSystemService(ConnectivityManager::class.java)
            val caps = capabilities ?: connectivity.getNetworkCapabilities(network)
            val physicalWifi = caps?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) } == true
            val address = if (physicalWifi) (properties ?: connectivity.getLinkProperties(network))?.linkAddresses
                ?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress } else null
            if (address == null) networks.remove(network) else networks[network] = address
            reconcileNetwork()
        }
    }

    // Runs again after an OEM unfreezes this process. It cannot defeat a system freeze.
    private val listenerHealth = object : Runnable {
        override fun run() {
            if (stopping || !WirelessState.enabled) return
            if (listeners.listener?.isRunning == false) {
                listeners.change(null)
                reconcileNetwork()
            }
            handler.postDelayed(this, 15_000)
        }
    }

    private fun reconcileNetwork() {
        if (stopping || !WirelessState.enabled) return
        val previous = listeners.target
        val target = networks.entries.firstOrNull { it.key == previous?.first }
            ?.let { it.key to it.value } ?: networks.entries.firstOrNull()?.let { it.key to it.value }
        WirelessState.waitingForWifi = target == null
        val ticket = listeners.change(target)
        if (ticket == null) { updateNotification(); return }
        discovery?.close(); discovery = null
        WirelessState.address = ""
        WirelessState.connected = false
        WirelessState.error = ""
        if (WirelessState.pairingStage in listOf(PairingStage.SENDING, PairingStage.WAITING)) {
            pairingAttempt++
            WirelessState.pairingStage = PairingStage.FAILED
            WirelessState.pairingError = label("网络已变化。已有电脑配对保留；正在进行的扫码请在 Wi-Fi 恢复后重试。",
                "Network changed. Existing pairing is kept; retry the pending scan after Wi-Fi returns.")
        }
        updateNotification()
        if (target == null) return
        worker.execute {
            runCatching {
                val identity = WirelessTls()
                val listener = WirelessServer(identity.context(), target.second, PORT, pairing,
                    { disconnect -> WirelessDeviceSession(applicationContext, disconnect) },
                    { connected -> handler.post {
                        if (listeners.isCurrent(ticket)) { WirelessState.connected = connected; updateNotification() }
                    } })
                handler.post {
                    if (listeners.install(ticket, listener)) {
                        tls = identity
                        WirelessState.address = target.second.hostAddress.orEmpty()
                        listener.start()
                        discovery = WirelessDiscovery(applicationContext).also { service ->
                            runCatching { service.start(listener.port, identity.fingerprint) }.onFailure { service.close() }
                        }
                        updateNotification()
                    }
                }
            }.onFailure { error -> handler.post {
                if (listeners.isCurrent(ticket)) {
                    listeners.change(null)
                    WirelessState.error = label("无线监听暂不可用，正在重试：", "Wireless listener unavailable, retrying: ") + error.message.orEmpty()
                    handler.postDelayed({ reconcileNetwork() }, 3000)
                }
            } }
        }
    }

    private fun updateNotification() {
        if (!stopping) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    override fun onCreate() {
        super.onCreate()
        current = this
        WirelessState.error = ""
        WirelessState.enabled = false
        WirelessState.address = ""
        WirelessState.connected = false
        WirelessState.waitingForWifi = false
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferences = getSharedPreferences("wireless-pairing", Context.MODE_PRIVATE)
        if (intent?.action == STOP) {
            stop(this)
            return START_NOT_STICKY
        }
        // A null intent is Android restarting an already enabled foreground service.
        // Recovery must not turn a deliberate stop back on, even if queued earlier.
        if (intent != null && intent.action != RESTORE) preferences.edit().putBoolean("enabled", true).apply()
        if (!preferences.getBoolean("enabled", false)) { stopSelf(); return START_NOT_STICKY }
        WirelessState.enabled = true
        handler.removeCallbacks(listenerHealth)
        handler.postDelayed(listenerHealth, 15_000)
        if (!registered) {
            val connectivity = getSystemService(ConnectivityManager::class.java)
            connectivity.registerNetworkCallback(NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networkCallback)
            registered = true
            // Registration delivers existing Wi-Fi networks as well as future changes.
        }
        reconcileNetwork()
        return START_STICKY
    }

    internal fun pairingLink(): String {
        check(!WirelessState.connected) { "Disconnect the controller before pairing another computer" }
        val identity = checkNotNull(tls) { "Wireless control is still starting" }
        val data = JSONObject().put("version", 1).put("host", WirelessState.address).put("port", checkNotNull(listeners.listener).port)
            .put("fingerprint", identity.fingerprint).put("token", pairing.offer())
            .put("language", AppLanguage.resolved(this))
            .put("name", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        return "tapilot://pair/" + Base64.getUrlEncoder().withoutPadding().encodeToString(data.toString().toByteArray())
    }

    internal fun pairComputer(qr: String) {
        check(WirelessState.pairingStage != PairingStage.SENDING &&
            WirelessState.pairingStage != PairingStage.WAITING) { "Pairing is already in progress" }
        WirelessState.pairingStage = PairingStage.SENDING
        WirelessState.pairingError = ""
        val attempt = ++pairingAttempt
        val pairingTarget = listeners.target
        val prepared = runCatching {
            checkNotNull(listeners.listener) { appText("请等待 Wi-Fi 连接恢复", "Wait for Wi-Fi to reconnect") }
            pairingLink()
        }
        fun fail(error: Throwable) {
            WirelessState.pairingStage = PairingStage.FAILED
            WirelessState.pairingError = error.message?.takeIf { it.isNotBlank() }
                ?: label("配对失败，请检查 Wi-Fi 并重新扫码。", "Pairing failed. Check Wi-Fi and scan again.")
        }
        prepared.onFailure { fail(it) }.onSuccess { link ->
            worker.execute {
                val result = runCatching { ComputerPairing.send(qr, link) }
                handler.post {
                    if (stopping || current !== this || listeners.target != pairingTarget || attempt != pairingAttempt || WirelessState.pairingStage != PairingStage.SENDING) return@post
                    result.fold({
                        WirelessState.pairingStage = PairingStage.WAITING
                        handler.postDelayed({
                            if (!stopping && current === this && attempt == pairingAttempt && WirelessState.pairingStage == PairingStage.WAITING) {
                                fail(IllegalStateException(label("等待电脑确认超时，请重新打开电脑配对窗口并扫码。",
                                    "Computer confirmation timed out. Open a new pairing window and scan again.")))
                            }
                        }, 300_000)
                    }, { fail(it) })
                }
            }
        }
    }

    internal fun revoke() { pairing.revoke(); stop(this) }

    override fun onDestroy() {
        stopping = true
        handler.removeCallbacks(listenerHealth)
        discovery?.close(); discovery = null
        listeners.close()
        worker.shutdownNow()
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        if (current === this) current = null
        WirelessState.enabled = false; WirelessState.connected = false; WirelessState.address = ""; WirelessState.waitingForWifi = false
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
            .setContentText(if (WirelessState.waitingForWifi) label("等待 Wi-Fi · 已有配对保留", "Waiting for Wi-Fi · Pairing kept") else if (WirelessState.connected) label("电脑正在控制手机", "Computer is controlling this phone") else label("等待已配对的电脑连接", "Waiting for a paired computer"))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, label("停止", "Stop"), stop).build()).build()
    }
    companion object {
        internal fun restoreIfEnabled(context: Context) {
            if (current != null || !context.getSharedPreferences("wireless-pairing", Context.MODE_PRIVATE)
                    .getBoolean("enabled", false)) return
            try {
                context.startForegroundService(Intent(context, WirelessControlService::class.java).setAction(RESTORE))
                android.util.Log.i("TapilotWireless", "Requested saved wireless service restoration")
            } catch (error: RuntimeException) {
                // OEM background policy may refuse this. Keep the preference for a foreground retry.
                android.util.Log.w("TapilotWireless", "Wireless restoration deferred until app opens", error)
            }
        }

        internal fun stop(context: Context) {
            context.getSharedPreferences("wireless-pairing", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            current?.let { service ->
                service.stopping = true
                service.listeners.close()
            }
            context.stopService(Intent(context, WirelessControlService::class.java))
        }

        internal var current: WirelessControlService? = null
            private set
        internal const val PORT = 39877
        private const val CHANNEL = "tapilot_wireless"
        private const val NOTIFICATION = 2115
        private const val STOP = "lifeos.md.tapilot.wireless.STOP"
        private const val RESTORE = "lifeos.md.tapilot.wireless.RESTORE"
    }
}
