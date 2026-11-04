package lifeos.md.tapilot.shell

import android.content.AttributionSource
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.UserHandle

/** A standalone shell process uses the external-provider API, not an unregistered app thread. */
internal object ShellProviderConnection {
    fun connect(packageName: String, appUid: Int, nonce: String, helper: IDisplayHelper): Boolean {
        if (Build.VERSION.SDK_INT < 31) error("Shell display experiments require Android 12 or later")
        val authority = "$packageName.shell-handoff"
        val user = UserHandle::class.java.getMethod("getIdentifier").invoke(UserHandle.getUserHandleForUid(appUid)) as Int
        val token = Binder()
        val manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
        val acquire = manager.javaClass.methods.single { it.name == "getContentProviderExternal" && it.parameterTypes.size == 4 }
        acquire.isAccessible = true
        val holder = checkNotNull(acquire.invoke(manager, authority, user, token, "tapilot-shell"))
        try {
            val provider = holder.javaClass.getField("provider").get(holder)
            val source = AttributionSource.Builder(2000).setPackageName("com.android.shell").build()
            val call = provider.javaClass.methods.single { it.name == "call" && it.parameterTypes.size == 5 }
            call.isAccessible = true
            val result = call.invoke(provider, source, authority, "connect", nonce,
                Bundle().apply { putBinder("helper", helper.asBinder()) }) as? Bundle
            return result?.getBoolean("accepted") == true
        } finally {
            manager.javaClass.methods.single { it.name == "removeContentProviderExternal" && it.parameterTypes.size == 2 }
                .apply { isAccessible = true }.invoke(manager, authority, token)
        }
    }
}
