package lifeos.md.tapilot.wireless

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import lifeos.md.tapilot.MainActivity
import java.io.File

/** Debug test APK only. Bootstraps pairing without putting credentials into test logs. */
class WirelessAcceptanceHarness : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val pairingFile = File(targetContext.cacheDir, "wireless-test-pairing")
        val done = File(targetContext.cacheDir, "wireless-test-done")
        done.delete(); pairingFile.delete()
        var code = 0
        val result = Bundle()
        try {
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            runOnMainSync { targetContext.startForegroundService(Intent(targetContext, WirelessControlService::class.java)) }
            val deadline = System.currentTimeMillis() + 20_000
            var link: String? = null
            while (System.currentTimeMillis() < deadline && link == null) {
                runOnMainSync { if (WirelessState.address.isNotEmpty()) link = WirelessControlService.current?.pairingLink() }
                if (link == null) Thread.sleep(100)
            }
            check(link != null) { "Wireless service did not become ready: ${WirelessState.error}" }
            pairingFile.writeText(link!!)
            val end = System.currentTimeMillis() + 300_000
            while (!done.exists() && System.currentTimeMillis() < end) Thread.sleep(200)
            check(done.exists()) { "Desktop acceptance did not finish within five minutes" }
            result.putString("result", "Wireless bootstrap completed")
        } catch (error: Throwable) {
            code = 1; result.putString("error", error.message)
        } finally {
            pairingFile.delete(); done.delete()
            targetContext.stopService(Intent(targetContext, WirelessControlService::class.java))
        }
        finish(code, result)
    }
}
