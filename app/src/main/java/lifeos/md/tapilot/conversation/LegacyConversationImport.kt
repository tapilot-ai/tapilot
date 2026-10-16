package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.AppText.text as appText
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import lifeos.md.tapilot.bridge.ModelCredentialStore
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The old origin is read once. The conversation runtime itself is never loaded. */
class LegacyConversationImport(private val activity: Activity, private val store: ConversationStore) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val finished = AtomicBoolean(false)
    private var view: WebView? = null
    private var result: ((Result<Unit>) -> Unit)? = null
    @SuppressLint("SetJavaScriptEnabled")
    fun start(done: (Result<Unit>) -> Unit) {
        result = done
        if (store.exists()) { complete(Result.success(Unit)); return }
        val bridge = object {
            @JavascriptInterface fun receive(payload: String) {
                worker.execute {
                    val outcome = runCatching {
                        val values = JSONObject(payload)
                        require(values.toString().length < 4 * 1024 * 1024) { "旧会话数据过大" }
                        val keys = ModelCredentialStore(activity)
                        val existing = keys.getAll()
                        ModelProfiles.ids.forEach { id ->
                            val old = values.optString("midscene.modelPresetConfig.$id.apiKey")
                                .ifBlank { if (id == "custom") values.optString("midscene.apiKey") else "" }
                            if (old.isNotBlank() && existing[id].isNullOrBlank()) keys.set(id, old)
                        }
                        store.save(ConversationStore.importLegacy(values))
                    }
                    handler.post { complete(outcome) }
                }
            }
        }
        view = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(bridge, "TapilotImport")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String) {
                    v.evaluateJavascript("""(() => { const data = {}; for (const key of Object.keys(localStorage)) {
                        if (key.startsWith('midscene.') || ['tapilot.activeConversation','tapilot.speech.provider','tapilot.confirmEveryStep'].includes(key))
                            data[key] = localStorage.getItem(key);
                    } TapilotImport.receive(JSON.stringify(data)); })()""", null)
                }
                override fun shouldOverrideUrlLoading(v: WebView, url: String) = true
            }
            loadDataWithBaseURL("https://appassets.androidplatform.net/assets/legacy-import.html", "<html><body></body></html>", "text/html", "UTF-8", null)
        }
        handler.postDelayed({ complete(Result.failure(IllegalStateException(appText("旧数据导入超时，请重试；原数据已保留", "Import timed out. Try again; original data has been kept")))) }, 15_000)
    }
    private fun complete(outcome: Result<Unit>) {
        if (!finished.compareAndSet(false, true)) return
        view?.removeJavascriptInterface("TapilotImport")
        view?.destroy(); view = null
        handler.removeCallbacksAndMessages(null)
        result?.invoke(outcome)
        result = null
        worker.shutdown()
    }
    override fun close() { complete(Result.failure(IllegalStateException(appText("导入已关闭", "Import closed")))) }
}
