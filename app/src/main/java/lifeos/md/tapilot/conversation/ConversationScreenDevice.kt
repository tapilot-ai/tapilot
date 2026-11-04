package lifeos.md.tapilot.conversation

import android.content.Context
import android.content.pm.ApplicationInfo
import lifeos.md.tapilot.device.DeviceService

/** A screen is pinned for an entire conversation attempt, including all phone operations. */
interface ConversationScreenDevice : DeviceService {
    val background: Boolean
    fun beginTask(id: String, continuing: Boolean)
    fun endTask(id: String, retainForTakeover: Boolean)
    fun closeScreen()
}

internal fun conversationScreenDevice(context: Context, primary: DeviceService): DeviceService {
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return primary
    // The shell backend is deliberately absent from Beta and Release artifacts.
    return Class.forName("lifeos.md.tapilot.shell.BackgroundConversationDevice")
        .getConstructor(Context::class.java, DeviceService::class.java)
        .newInstance(context, primary) as DeviceService
}
