package lifeos.md.tapilot

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewAssetLoader

/** Isolated WebView host. Its optional entry replaces only the speech transport. */
class SpeechWebVerificationActivity : ComponentActivity() {
    private lateinit var web: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        val assets = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = assets.shouldInterceptRequest(request.url)
        }
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/speechverification.html?startDelay=${intent.getLongExtra("startDelay", 0)}")
    }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
