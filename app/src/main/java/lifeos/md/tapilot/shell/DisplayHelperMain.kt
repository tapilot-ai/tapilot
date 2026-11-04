package lifeos.md.tapilot.shell

import android.content.Context
import android.os.Looper
import android.os.Process

/** Shell helper entry point. Started by an explicitly authorized ADB shell, never by the app UID. */
object DisplayHelperMain {
    @JvmStatic fun main(args: Array<String>) {
        check(Process.myUid() == 2000) { "This helper requires shell UID 2000" }
        require(args.size == 2)
        val packageName = args[0]
        require(packageName.matches(Regex("lifeos\\.md\\.tapilot(?:\\.[a-zA-Z0-9_]+)*")))
        val appUid = args[1].toInt()
        require(appUid >= 10000)
        val nonce = System.`in`.bufferedReader().readLine()
        require(nonce?.matches(Regex("[a-f0-9]{64}")) == true) { "Missing one-use consent" }
        Looper.prepareMainLooper()
        val thread = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null)
        val system = thread.javaClass.getMethod("getSystemContext").invoke(thread) as Context
        val context = system.createPackageContext("com.android.shell", 0)
        check(context.packageManager.getApplicationInfo(packageName, 0).uid == appUid)
        val helper = ShellDisplayService(context, appUid)
        // Handoff on a worker: the provider verifies our Binder while the main looper handles display calls.
        Thread {
            try {
                check(ShellProviderConnection.connect(packageName, appUid, nonce!!, helper)) { "App consent expired or was not granted" }
                println("Tapilot shell helper connected")
            } catch (failure: Throwable) {
                System.err.println("Shell helper handoff failed: ${failure.javaClass.simpleName}: ${(failure.cause?.message ?: failure.message)?.replace(nonce.orEmpty(), "[redacted]")}")
                Process.killProcess(Process.myPid())
            }
        }.start()
        Looper.loop()
    }
}
