package lifeos.md.tapilot.conversation

import android.content.Context
import lifeos.md.tapilot.shell.BackgroundConversationDevice
import lifeos.md.tapilot.device.DeviceService

/** A screen is pinned for an entire conversation attempt, including all phone operations. */
interface ConversationScreenDevice : DeviceService {
    val background: Boolean
    fun beginTask(id: String, continuing: Boolean)
    fun endTask(id: String, retainForTakeover: Boolean)
    fun closeScreen()
    fun usePrimaryForSetup()
}

internal fun conversationScreenDevice(context: Context, primary: DeviceService): DeviceService =
    BackgroundConversationDevice(context, primary)
