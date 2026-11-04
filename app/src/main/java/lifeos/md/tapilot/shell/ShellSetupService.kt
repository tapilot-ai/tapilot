package lifeos.md.tapilot.shell

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.RemoteInput
import lifeos.md.tapilot.R
import java.util.concurrent.Executors

/** Owns connection independently of the settings Activity. All state transitions run on main. */
class ShellSetupService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cleanup = Executors.newSingleThreadExecutor()
    private lateinit var preferences: ShellPreferences
    private lateinit var notices: ShellNotifications
    private var discovery: LocalAdbDiscovery? = null
    private val retry = ShellRetry()
    private var connectionPort = 0
    private var pairingPort = 0
    private var busy = false
    @Volatile private var generation = 0
    private var arming = false
    private var externalBootstrap = false
    @Volatile private var stopped = false
    @Volatile private var client: LocalAdbClient? = null
    private val reconnect = Runnable { searchAndConnect() }
    private val deadline = Runnable { disconnect(); ShellSession.revoke(); setState(ShellState.REPAIR); scheduleRetry() }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { main.post { if (!stopped) { retry.reset(); scheduleRetry() } } }
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = ShellPreferences(this)
        notices = ShellNotifications(this)
        startForeground(ShellNotifications.ID, notices.ongoing(R.string.shell_searching))
        ShellSession.connectionChanged = {
            if (ShellSession.ready) {
                preferences.paired = true
                main.removeCallbacks(deadline); retry.reset(); notices.cancelPair(); setState(ShellState.CONNECTED)
            } else if (!stopped && !arming) {
                // A dead Binder invalidates the display. Never recreate it or resume a task here.
                disconnect(); setState(ShellState.REPAIR); scheduleRetry()
            }
        }
        runCatching { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == REVOKE) { revoke(false); return START_NOT_STICKY }
        if (!preferences.authorized) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            PAIR -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ShellNotifications.CODE)?.toString().orEmpty()
                pair(pairingPort, code)
            }
            else -> { retry.reset(); if (!busy && !ShellSession.ready) searchAndConnect() }
        }
        return START_STICKY
    }
    private fun discovery() {
        if (discovery != null) return
        discovery = LocalAdbDiscovery(this, { pairing, port ->
            if (pairing) {
                pairingPort = port
                if (!busy && !ShellSession.ready) { setState(ShellState.PAIR_CODE); notices.showPair() }
            } else {
                connectionPort = port
                if (preferences.paired && !busy && !ShellSession.ready) connect(port)
            }
        }, { pairing, port ->
            if (pairing && pairingPort == port) { pairingPort = 0; notices.cancelPair() }
            if (!pairing && connectionPort == port) connectionPort = 0
        }).also { it.start() }
    }
    private fun searchAndConnect() {
        if (stopped || !preferences.authorized || busy || ShellSession.ready || externalBootstrap) return
        discovery()
        ShellRecovery.restore(this)
        if (preferences.paired) {
            val port = connectionPort.takeIf { it > 0 } ?: preferences.port
            if (port > 0) { connect(port); return }
        }
        setState(if (pairingPort > 0) ShellState.PAIR_CODE else ShellState.SEARCHING)
        scheduleRetry()
    }
    fun pair(port: Int, raw: String) {
        if (stopped || !preferences.authorized || busy || ShellSession.ready || externalBootstrap) return
        val code = runCatching { LocalAdbInput.code(raw) }.getOrNull()
        if (code == null) { notices.pairingProgress(); notices.showPair(true, invalid = true); setState(ShellState.PAIR_FAILED); return }
        if (port !in 1..65535) { setState(ShellState.PAIR_FAILED); notices.cancelPair(); return }
        notices.pairingProgress()
        runConnection(ShellState.PAIRING) { connection -> connection.pair(port, code) }
    }
    fun connect(port: Int) {
        if (stopped || !preferences.authorized || busy || ShellSession.ready || externalBootstrap || port !in 1..65535) return
        // Mark busy before arming: the session callback must not schedule a competing connection.
        arming = true
        try { ShellSession.authorize(this) } finally { arming = false }
        val token = ShellSession.pendingToken()
        preferences.port = port
        runConnection(ShellState.CONNECTING) { connection -> connection.start(applicationContext, port, token) }
    }
    private fun runConnection(operation: ShellState, action: (LocalAdbClient) -> Unit) {
        busy = true
        val run = ++generation
        main.removeCallbacks(reconnect)
        setState(operation)
        main.postDelayed(deadline, 35_000)
        worker.execute {
            var connection: LocalAdbClient? = null
            val result = runCatching {
                connection = LocalAdbClient(applicationContext)
                if (!attach(run, connection!!)) return@runCatching
                action(connection!!)
            }
            connection?.close()
            main.post {
                if (stopped || generation != run) return@post
                client = null; busy = false; main.removeCallbacks(deadline)
                if (operation == ShellState.PAIRING) {
                    if (result.isSuccess) {
                        preferences.paired = true; notices.cancelPair(); retry.reset(); searchAndConnect()
                    } else { setState(ShellState.PAIR_FAILED); notices.showPair(true) }
                } else if (ShellSession.ready) {
                    // A detached helper can outlive the bootstrap stream; its Binder is authoritative.
                    setState(ShellState.CONNECTED)
                } else { setState(ShellState.REPAIR); scheduleRetry() }
            }
        }
    }
    private fun scheduleRetry() {
        if (stopped || !preferences.authorized || ShellSession.ready) return
        main.removeCallbacks(reconnect)
        main.postDelayed(reconnect, retry.nextDelay())
    }
    fun prepareComputerBootstrap() {
        if (!preferences.authorized || ShellSession.ready) return
        disconnect(); main.removeCallbacks(reconnect); externalBootstrap = true
        arming = true
        try { ShellSession.authorize(this) } finally { arming = false }
        main.postDelayed({ externalBootstrap = false; retryNow() }, 300_000)
    }
    fun retryNow() { retry.reset(); main.removeCallbacks(reconnect); searchAndConnect() }
    fun revoke(forget: Boolean) {
        // Persist revocation before touching transport, so late callbacks cannot restore it.
        preferences.revoke(); stopped = true
        disconnect(); ShellSession.revoke(); notices.cancelPair()
        if (forget) { LocalAdbIdentity.forget(this); preferences.paired = false; preferences.port = 0 }
        setState(ShellState.OFF); stopSelf()
    }
    @Synchronized private fun attach(run: Int, connection: LocalAdbClient): Boolean {
        if (stopped || generation != run) return false
        client = connection
        return true
    }
    @Synchronized private fun disconnect() {
        generation++; busy = false; main.removeCallbacks(deadline)
        val previous = client; client = null
        if (previous != null) cleanup.execute { previous.close() }
    }
    private fun setState(value: ShellState) {
        if (state == value) return
        state = value; notices.update(message(value)); changed?.invoke()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        stopped = true; main.removeCallbacksAndMessages(null)
        discovery?.close(); discovery = null
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        ShellSession.connectionChanged = null; ShellSession.revoke(); disconnect()
        notices.cancelPair(); worker.shutdownNow(); cleanup.shutdown()
        instance = null; state = ShellState.OFF; changed?.invoke()
        super.onDestroy()
    }
    companion object {
        const val PAIR = "lifeos.md.tapilot.shell.PAIR"
        const val REVOKE = "lifeos.md.tapilot.shell.REVOKE"
        internal var instance: ShellSetupService? = null; private set
        internal var state = ShellState.OFF; private set
        internal var changed: (() -> Unit)? = null
        fun start(context: Context) { context.startForegroundService(Intent(context, ShellSetupService::class.java)) }
        internal fun message(state: ShellState): Int = when (state) {
            ShellState.OFF -> R.string.shell_off
            ShellState.SEARCHING -> R.string.shell_searching
            ShellState.PAIR_CODE -> R.string.shell_code_help
            ShellState.PAIRING -> R.string.shell_pairing
            ShellState.CONNECTING -> R.string.shell_connecting
            ShellState.CONNECTED -> R.string.shell_connected
            ShellState.REPAIR -> R.string.shell_repair
            ShellState.PAIR_FAILED -> R.string.shell_pair_failed
        }
    }
}
