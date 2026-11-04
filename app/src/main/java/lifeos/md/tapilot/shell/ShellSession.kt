package lifeos.md.tapilot.shell

import android.content.Context
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.security.SecureRandom

/** App consent plus a live shell-owned Binder; never inferred from an enabled preference. */
internal object ShellSession {
    private val consent = ShellConsent()
    private val main = Handler(Looper.getMainLooper())
    private val lifetime = Binder()
    private var helper: IDisplayHelper? = null
    private var tokenFile: File? = null
    var changed: (() -> Unit)? = null
    var connectionChanged: (() -> Unit)? = null
    var conversationChanged: (() -> Unit)? = null
    private fun notifyChanged() { conversationChanged?.invoke(); connectionChanged?.invoke(); changed?.invoke() }
    val ready: Boolean get() = helper?.asBinder()?.isBinderAlive == true
    fun requireHelper(): IDisplayHelper = checkNotNull(helper?.takeIf { ready }) {
        "Authorize and connect the optional shell helper first"
    }
    fun pendingToken(): String = checkNotNull(consent.pending(SystemClock.elapsedRealtime())) {
        "Authorize the optional shell helper first; requests expire after five minutes"
    }
    fun authorize(context: Context) {
        revoke()
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        consent.arm(token, SystemClock.elapsedRealtime())
        tokenFile = File(context.filesDir, "shell-consent").apply { writeText(token) }
        main.postDelayed({ if (!ready) revoke() }, 300_000)
        notifyChanged()
    }
    fun accept(token: String?, binder: IBinder?, caller: Int): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (helper != null || binder == null || !consent.consume(token, caller, SystemClock.elapsedRealtime())) return false
        tokenFile?.delete(); tokenFile = null
        val candidate = IDisplayHelper.Stub.asInterface(binder)
        if (candidate.identity() != 2000) return false
        binder.linkToDeath({ main.post { if (helper?.asBinder() == binder) revoke() } }, 0)
        candidate.watchClient(lifetime)
        helper = candidate
        main.removeCallbacksAndMessages(null)
        notifyChanged()
        return true
    }
    fun revoke() {
        consent.clear(); tokenFile?.delete(); tokenFile = null
        main.removeCallbacksAndMessages(null)
        val previous = helper; helper = null
        runCatching { previous?.shutdown() }
        notifyChanged()
    }
}
