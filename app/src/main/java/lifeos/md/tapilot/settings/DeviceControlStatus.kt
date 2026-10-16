package lifeos.md.tapilot.settings

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService

internal enum class DeviceControlStatus { READY, NEEDS_RECONNECT, DISABLED }

private fun deviceControlStatus(context: Context): DeviceControlStatus {
    if (MidsceneAccessibilityService.current() != null) return DeviceControlStatus.READY
    val component = ComponentName(context, MidsceneAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
    return if (enabled) DeviceControlStatus.NEEDS_RECONNECT else DeviceControlStatus.DISABLED
}

@Composable
internal fun rememberDeviceControlStatus(context: Context): DeviceControlStatus {
    var status by remember(context) { mutableStateOf(deviceControlStatus(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
        val listener: (Boolean) -> Unit = { status = deviceControlStatus(context) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = deviceControlStatus(context)
        }
        lifecycle.addObserver(observer)
        MidsceneAccessibilityService.addConnectionListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            MidsceneAccessibilityService.removeConnectionListener(listener)
        }
    }
    return status
}
