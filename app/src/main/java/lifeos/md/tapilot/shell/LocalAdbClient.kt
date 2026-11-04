package lifeos.md.tapilot.shell

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.util.concurrent.TimeUnit

/** Loopback only. The public UI exposes pairing and one fixed helper bootstrap, not a terminal. */
internal class LocalAdbClient(context: Context) : AutoCloseable {
    private val identity = LocalAdbIdentity(context)
    private val manager = object : AbsAdbConnectionManager() {
        override fun getPrivateKey() = identity.key
        override fun getCertificate() = identity.certificate
        override fun getDeviceName() = "Tapilot"
    }.apply { setApi(Build.VERSION.SDK_INT); setTimeout(15, TimeUnit.SECONDS); setThrowOnUnauthorised(true) }
    @Volatile private var stream: AdbStream? = null
    @Volatile private var pairing: PairingDeadline? = null
    @Volatile private var closed = false
    fun pair(port: Int, code: String) {
        check(!closed)
        PairingDeadline(port).use { bridge ->
            pairing = bridge
            if (closed) error("Pairing cancelled")
            try { check(manager.pair("127.0.0.1", bridge.port, code)) }
            finally { pairing = null }
        }
        check(!closed)
    }
    fun start(context: Context, port: Int, consent: String) {
        check(!closed)
        check(manager.connect("127.0.0.1", port))
        check(!closed)
        if (ShellPreferences(context).recovery && context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Fixed capability, only after explicit recovery consent; never accepts a user command.
            manager.openStream("shell:pm grant ${LocalAdbInput.quote(context.packageName)} android.permission.WRITE_SECURE_SETTINGS").use { grant ->
                grant.openInputStream().use { it.readBytes() }
            }
        }
        val command = "CLASSPATH=${LocalAdbInput.quote(context.applicationInfo.sourceDir)} exec app_process /system/bin " +
            "lifeos.md.tapilot.shell.DisplayHelperMain ${LocalAdbInput.quote(context.packageName)} ${android.os.Process.myUid()}"
        val opened = manager.openStream("shell:$command")
        stream = opened
        if (closed) { opened.close(); error("Connection cancelled") }
        opened.openOutputStream().apply { write((consent + "\n").toByteArray()); flush() }
        // Drain output without logging it; the authenticated Binder handoff is the success signal.
        opened.openInputStream().use { input ->
            val buffer = ByteArray(1024)
            while (!closed && input.read(buffer) >= 0) { }
        }
    }
    override fun close() {
        closed = true
        pairing?.close(); pairing = null
        runCatching { stream?.close() }; stream = null
        runCatching { manager.close() }
    }
}
