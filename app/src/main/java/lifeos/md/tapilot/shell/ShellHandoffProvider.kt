package lifeos.md.tapilot.shell

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Only shell may hand over a helper; the one-use UI consent is checked separately. */
class ShellHandoffProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val caller = Binder.getCallingUid()
        require(caller == 2000 && method == "connect") { "Shell handoff only" }
        val binder = extras?.getBinder("helper")
        val task = FutureTask { ShellSession.accept(arg, binder, caller) }
        Handler(Looper.getMainLooper()).post(task)
        return Bundle().apply { putBoolean("accepted", task.get(5, TimeUnit.SECONDS)) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
