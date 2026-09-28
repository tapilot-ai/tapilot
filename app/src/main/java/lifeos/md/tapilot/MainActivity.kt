package lifeos.md.tapilot

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.util.Base64
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.bridge.NativeJavascriptBridge
import lifeos.md.tapilot.report.DeletedReportStore
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private lateinit var webView: WebView
    private lateinit var rootView: View
    private lateinit var nativeBridge: NativeJavascriptBridge
    private var statusBarInsetCssPx = 0
    private var navigationBarInsetCssPx = 0
    private var pageReady = false
    private var pendingTaskInputFocus = false
    private val accessibilityStateHandler = Handler(Looper.getMainLooper())
    private var lastAccessibilityState: Pair<Boolean, Boolean>? = null
    private val accessibilityStatePoll = object : Runnable {
        override fun run() {
            refreshAccessibilityState()
            accessibilityStateHandler.postDelayed(this, 2_000L)
        }
    }
    private val accessibilityConnectionListener: (Boolean) -> Unit = { _ ->
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                refreshAccessibilityState()
            }
        }
    }

    private fun refreshAccessibilityState(force: Boolean = false) {
        val state = Pair(
            MidsceneAccessibilityService.current() != null,
            isAccessibilityServiceEnabled(),
        )
        if (!force && state == lastAccessibilityState) return
        lastAccessibilityState = state
        if (::webView.isInitialized) {
            webView.evaluateJavascript(
                "window.__midsceneSetAccessibilityState?.(${state.first}, ${state.second})",
                null,
            )
            webView.evaluateJavascript("window.__midsceneRefreshCapabilities?.()", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingTaskInputFocus = intent.getBooleanExtra(EXTRA_FOCUS_TASK_INPUT, false)
        receiveExternalIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootView = root
        applyWindowTheme(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layout = SystemBarInsetsLayout.fromPixels(
                topInsetPx = systemBars.top,
                bottomInsetPx = systemBars.bottom,
                density = resources.displayMetrics.density,
            )
            statusBarInsetCssPx = layout.topInsetCssPx
            navigationBarInsetCssPx = layout.bottomInsetCssPx
            applySystemBarInsets()
            insets
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this@MainActivity))
                .build()
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.url.host != "appassets.androidplatform.net"

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    pageReady = true
                    applySystemBarInsets()
                    refreshAccessibilityState(force = true)
                    syncThemePreference()
                    notifyDeletedReports()
                    focusTaskInputIfRequested()
                    deliverLaunchContext()
                }
            }
            nativeBridge = NativeJavascriptBridge(
                webView = this,
                onLanguageChanged = { language ->
                    if (AppLanguage.set(this@MainActivity, language)) recreate()
                },
                onThemeChanged = { theme ->
                    AppTheme.set(this@MainActivity, theme)
                    applyWindowTheme(root)
                },
            )
            addJavascriptInterface(nativeBridge, "MidsceneNative")
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(root)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::rootView.isInitialized) applyWindowTheme(rootView)
        syncThemePreference()
        refreshAccessibilityState(force = true)
        notifyDeletedReports()
        focusTaskInputIfRequested()
        deliverLaunchContext()
        if (pageReady) webView.evaluateJavascript("window.__tapilotRefreshAssistantRole?.()", null)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveExternalIntent(intent)
        deliverLaunchContext()
        if (intent.getBooleanExtra(EXTRA_FOCUS_TASK_INPUT, false)) {
            pendingTaskInputFocus = true
            focusTaskInputIfRequested()
        }
    }

    override fun onStart() {
        super.onStart()
        MidsceneAccessibilityService.addConnectionListener(accessibilityConnectionListener)
        accessibilityStateHandler.post(accessibilityStatePoll)
    }

    override fun onStop() {
        accessibilityStateHandler.removeCallbacks(accessibilityStatePoll)
        MidsceneAccessibilityService.removeConnectionListener(accessibilityConnectionListener)
        super.onStop()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("MidsceneNative")
        if (::nativeBridge.isInitialized) nativeBridge.close()
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!::webView.isInitialized || !pageReady) {
            super.onBackPressed()
            return
        }
        webView.evaluateJavascript("window.__tapilotHandleBack?.() === true") { handled ->
            if (handled != "true" && !isFinishing && !isDestroyed) {
                super.onBackPressed()
            }
        }
    }

    private fun applySystemBarInsets() {
        if (!::webView.isInitialized) return
        webView.evaluateJavascript(
            "document.documentElement.style.setProperty(" +
                "'--native-safe-area-top', '${statusBarInsetCssPx}px');" +
                "document.documentElement.style.setProperty(" +
                "'--native-safe-area-bottom', '${navigationBarInsetCssPx}px')",
            null,
        )
    }

    private fun syncThemePreference() {
        if (!pageReady || !::webView.isInitialized) return
        webView.evaluateJavascript(
            "window.__tapilotSetThemePreference?.('${AppTheme.preference(this)}')",
            null,
        )
    }

    private fun focusTaskInputIfRequested() {
        if (!pendingTaskInputFocus || !pageReady || !::webView.isInitialized) return
        pendingTaskInputFocus = false
        intent.removeExtra(EXTRA_FOCUS_TASK_INPUT)
        // Give the WebView native focus before focusing the DOM editor. Doing this in the
        // evaluateJavascript callback immediately blurs the textarea again and exits the
        // focused composer layout when the app is opened from the floating ball.
        webView.requestFocus(View.FOCUS_DOWN)
        webView.evaluateJavascript("window.__tapilotFocusTaskInput?.()") {
            webView.postDelayed({
                getSystemService(InputMethodManager::class.java)
                    .showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            }, 120L)
        }
    }

    private fun receiveExternalIntent(incoming: Intent) {
        val action = incoming.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_PROCESS_TEXT &&
            action != Intent.ACTION_ASSIST) return
        val text = when (action) {
            Intent.ACTION_PROCESS_TEXT -> incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }?.take(20_000).orEmpty()
        val context = JSONObject().put("kind", "share").put("label", "分享内容")
            .put("text", text)
        if (action == Intent.ACTION_SEND && incoming.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            val uri = incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readNBytes(5 * 1024 * 1024 + 1)
                } ?: error("Image unavailable")
                require(bytes.size <= 5 * 1024 * 1024) { "Image too large" }
                val mime = incoming.type!!.takeIf { it.matches(Regex("image/[a-zA-Z0-9.+-]+")) }
                    ?: "image/jpeg"
                context.put("dataUrl", "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}")
                context.put("label", "分享图片")
            }
        }
        if (text.isNotBlank() || context.has("dataUrl") || action == Intent.ACTION_ASSIST) {
            LaunchContext.put(context)
            pendingTaskInputFocus = true
        }
    }

    private fun deliverLaunchContext() {
        if (!pageReady || !::webView.isInitialized) return
        // The bridge consumes the payload on the Web side; this event only triggers retrieval.
        webView.evaluateJavascript("window.__tapilotReceiveLaunchContext?.()", null)
    }

    private fun notifyDeletedReports() {
        if (!pageReady || !::webView.isInitialized) return
        val reportIds = DeletedReportStore.consume(this)
        if (reportIds.isEmpty()) return
        webView.evaluateJavascript(
            "window.__tapilotReportsDeleted?.(${JSONArray(reportIds.toList())})",
            null,
        )
    }

    @Suppress("DEPRECATION")
    private fun applyWindowTheme(root: View) {
        val dark = AppTheme.isDark(this)
        val background = if (dark) Color.rgb(17, 21, 34) else Color.rgb(247, 248, 252)
        root.setBackgroundColor(background)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(this, MidsceneAccessibilityService::class.java)
        return enabledServices.split(':').any { service ->
            ComponentName.unflattenFromString(service) == component
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }

    companion object {
        const val EXTRA_FOCUS_TASK_INPUT = "lifeos.md.tapilot.extra.FOCUS_TASK_INPUT"
        private const val NOTIFICATION_PERMISSION_REQUEST = 2108
    }
}
