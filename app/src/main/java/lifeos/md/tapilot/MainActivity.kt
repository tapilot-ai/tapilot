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
import android.util.Log
import android.provider.Settings
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import lifeos.md.tapilot.chat.ChatController
import lifeos.md.tapilot.chat.ChatScreen
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.FileProvider
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.bridge.AttachmentReader
import lifeos.md.tapilot.bridge.NativeJavascriptBridge
import lifeos.md.tapilot.report.DeletedReportStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private lateinit var webView: WebView
    private lateinit var rootView: View
    private lateinit var nativeBridge: NativeJavascriptBridge
    private lateinit var chatController: ChatController
    private lateinit var chatView: ComposeView
    private val attachmentWorker = Executors.newSingleThreadExecutor()
    private var attachmentCallback: ((Result<JSONObject?>) -> Unit)? = null
    private var cameraOutput: Uri? = null
    private var cameraFile: File? = null
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

        val root = FrameLayout(this)
        rootView = root
        applyWindowTheme(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboardBottom = if (insets.isVisible(WindowInsetsCompat.Type.ime()))
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
            val bottom = if (::chatController.isInitialized && chatController.state.webSurfaceOpen) keyboardBottom else 0
            if (root.paddingBottom != bottom) root.setPadding(0, 0, 0, bottom)
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
                onPickAttachment = ::pickAttachment,
            )
            addJavascriptInterface(nativeBridge, "MidsceneNative")
            chatController = ChatController(this@MainActivity, this, ::showSettingsSurface, ::pickAttachment)
            addJavascriptInterface(chatController, "TapilotChat")
            visibility = View.INVISIBLE
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        chatView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { ChatScreen(chatController) }
        }
        root.addView(chatView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (chatController.recording || chatController.transcribing) {
                    chatController.cancelRecording()
                    return
                }
                handleChatBack {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
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

    @Suppress("DEPRECATION")
    private fun pickAttachment(kind: String, callback: (Result<JSONObject?>) -> Unit) {
        if (attachmentCallback != null) {
            callback(Result.failure(IllegalStateException("An attachment picker is already open")))
            return
        }
        attachmentCallback = callback
        runCatching {
            val intent = if (kind == "camera") {
                val directory = File(cacheDir, "attachment-camera").apply { mkdirs() }
                cameraFile = File.createTempFile("photo-", ".jpg", directory)
                cameraOutput = FileProvider.getUriForFile(
                    this, "$packageName.reports", cameraFile!!,
                )
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, cameraOutput)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri("Tapilot photo", cameraOutput)
                }
            } else {
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (kind == "photo") "image/*" else "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivityForResult(intent, ATTACHMENT_REQUEST)
        }.onFailure {
            attachmentCallback = null
            cameraFile?.delete()
            cameraFile = null
            cameraOutput = null
            callback(Result.failure(it))
        }
    }

    @Deprecated("Activity result API is used for the native attachment picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ATTACHMENT_REQUEST) return
        val callback = attachmentCallback ?: return
        attachmentCallback = null
        val uri = if (resultCode == RESULT_OK) data?.data ?: cameraOutput else null
        val capturedFile = cameraFile
        cameraFile = null
        cameraOutput = null
        if (uri == null) {
            capturedFile?.delete()
            callback(Result.success(null))
            return
        }
        attachmentWorker.execute {
            val result = runCatching { AttachmentReader.read(this, uri) }
            capturedFile?.delete()
            callback(result)
        }
    }

    override fun onStart() {
        super.onStart()
        MidsceneAccessibilityService.addConnectionListener(accessibilityConnectionListener)
        accessibilityStateHandler.post(accessibilityStatePoll)
    }

    override fun onStop() {
        if (::chatController.isInitialized && (chatController.recording || chatController.transcribing)) chatController.cancelRecording()
        accessibilityStateHandler.removeCallbacks(accessibilityStatePoll)
        MidsceneAccessibilityService.removeConnectionListener(accessibilityConnectionListener)
        super.onStop()
    }

    override fun onDestroy() {
        attachmentCallback = null
        attachmentWorker.shutdown()
        webView.removeJavascriptInterface("MidsceneNative")
        webView.removeJavascriptInterface("TapilotChat")
        if (::chatController.isInitialized) chatController.close()
        if (::nativeBridge.isInitialized) nativeBridge.close()
        webView.destroy()
        super.onDestroy()
    }

    private fun handleChatBack(exit: () -> Unit) {
        if (!::webView.isInitialized || !pageReady) {
            exit()
            return
        }
        webView.evaluateJavascript("window.__tapilotHandleBack?.() === true") { handled ->
            if (handled != "true" && !isFinishing && !isDestroyed) {
                exit()
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
        chatController.requestFocus()
    }

    private fun showSettingsSurface(settings: Boolean) {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(rootView.windowToken, 0)
        chatView.visibility = if (settings) View.GONE else View.VISIBLE
        webView.visibility = if (settings) View.VISIBLE else View.INVISIBLE
        if (!settings) rootView.setPadding(0, 0, 0, 0)
        ViewCompat.requestApplyInsets(rootView)
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
                ?: incoming.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            if (uri != null) runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 5 * 1024 * 1024) { "Image too large" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Image unavailable")
                val mime = contentResolver.getType(uri)
                    ?.takeIf { it.matches(Regex("image/[a-zA-Z0-9.+-]+")) }
                    ?: incoming.type!!.takeIf { it.matches(Regex("image/[a-zA-Z0-9.+-]+")) }
                    ?: "image/jpeg"
                context.put("dataUrl", "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}")
                context.put("label", "分享图片")
            }.onFailure { Log.w("TapilotShare", "Unable to read shared image", it) }
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
        val background = if (dark) getColor(R.color.brand_dark_background) else getColor(R.color.brand_background)
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
        private const val ATTACHMENT_REQUEST = 2112
    }
}
