package lifeos.md.tapilot.shell

import android.content.Context

/** A user's durable choice is separate from each short-lived Binder handoff. */
internal class ShellPreferences(context: Context) {
    private val store = context.getSharedPreferences("shell-assistance", Context.MODE_PRIVATE)
    var backgroundEnabled: Boolean
        get() = store.getBoolean("background-enabled", false)
        set(value) { store.edit().putBoolean("background-enabled", value).commit() }
    var authorized: Boolean
        get() = store.getBoolean("authorized", false)
        set(value) { store.edit().putBoolean("authorized", value).commit() }
    var recovery: Boolean
        get() = store.getBoolean("recovery", false)
        set(value) { store.edit().putBoolean("recovery", value).commit() }
    var paired: Boolean
        get() = store.getBoolean("paired", false)
        set(value) { store.edit().putBoolean("paired", value).commit() }
    var port: Int
        get() = store.getInt("port", 0)
        set(value) { store.edit().putInt("port", value).apply() }
    fun revoke() { store.edit().putBoolean("authorized", false).putBoolean("background-enabled", false).putBoolean("recovery", false).commit() }
}
