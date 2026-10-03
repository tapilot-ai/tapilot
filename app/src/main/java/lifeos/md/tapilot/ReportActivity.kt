package lifeos.md.tapilot

import lifeos.md.tapilot.theme.BrandColors

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.Context
import android.content.ClipData
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import lifeos.md.tapilot.report.VideoExportStore
import org.json.JSONObject
import java.util.concurrent.Executors
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import lifeos.md.tapilot.report.ReportAssetRoutes
import lifeos.md.tapilot.report.DeletedReportStore
import lifeos.md.tapilot.report.ReportReadinessTracker
import lifeos.md.tapilot.report.ReportRepository
import java.io.ByteArrayInputStream
import java.io.File

class ReportActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private val fileWorker = Executors.newSingleThreadExecutor()
    private lateinit var videoExport: VideoExportStore
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: TextView
    private lateinit var reportContent: FrameLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarTitle: TextView
    private lateinit var rootContent: LinearLayout
    private val toolbarButtons = mutableListOf<ImageButton>()
    private lateinit var report: File
    private lateinit var reportId: String
    private val handler = Handler(Looper.getMainLooper())
    private val readiness = ReportReadinessTracker(MAX_READINESS_ATTEMPTS)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        val initialDarkTheme = AppTheme.isDark(this)
        applySystemBarTheme(initialDarkTheme)
        videoExport = VideoExportStore(applicationContext)
        reportId = intent.getStringExtra(EXTRA_REPORT_ID)
            ?: throw IllegalArgumentException("Missing report id")
        val reportsDirectory = File(filesDir, "reports")
        report = ReportRepository(reportsDirectory).reportFile(reportId)
        check(report.isFile) { "Report does not exist: $reportId" }

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(REPORT_HOST)
            .addPathHandler(
                REPORT_PATH,
                WebViewAssetLoader.InternalStoragePathHandler(this, reportsDirectory),
            )
            .build()
        progressBar = ProgressBar(this)
        errorView = TextView(this).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(reportSecondaryTextColor(initialDarkTheme))
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        webView = WebView(this).apply {
            setBackgroundColor(reportBackgroundColor(initialDarkTheme))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        readiness.recordConsoleError(consoleMessage.message())
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            installInitialReportTheme(this, initialDarkTheme)
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    if (ReportAssetRoutes.isMidsceneImage(request.url.toString())) {
                        // The report currently loads its original upstream logo. Set
                        // REPLACE_REPORT_LOGO to true to restore Tapilot branding without
                        // rewriting the generated HTML. Known logo URLs are otherwise the only
                        // external resources allowed through this offline report WebView.
                        return if (REPLACE_REPORT_LOGO) {
                            WebResourceResponse(
                                "image/png",
                                null,
                                assets.open("tapilot-icon.png"),
                            )
                        } else {
                            null
                        }
                    }
                    val isLocalReport = request.url.host == REPORT_HOST &&
                        request.url.path.orEmpty().startsWith(REPORT_PATH)
                    if (isLocalReport) return assetLoader.shouldInterceptRequest(request.url)
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.url.host != REPORT_HOST ||
                    !request.url.path.orEmpty().startsWith(REPORT_PATH)

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceErrorCompat,
                ) {
                    if (request.isForMainFrame) {
                        val detail = if (
                            WebViewFeature.isFeatureSupported(
                                WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION,
                            )
                        ) {
                            error.description.toString()
                        } else {
                            getString(R.string.report_load_failed_generic)
                        }
                        showLoadError(getString(R.string.report_load_failed, detail))
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(assets.open("report-export.js").bufferedReader().use { it.readText() }, null)
                    view.evaluateJavascript(
                        mobileCompatibilityScript(initialDarkTheme),
                        null,
                    )
                    probeReportReady()
                }
            }
            installVideoExport(this)
            installReportThemeBridge(this)
            loadUrl("https://$REPORT_HOST$REPORT_PATH${report.name}")
        }
        reportContent = FrameLayout(this).apply {
            setBackgroundColor(reportBackgroundColor(initialDarkTheme))
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                progressBar,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            addView(
                errorView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(reportChromeColor(initialDarkTheme))
            elevation = dp(2).toFloat()
            addView(
                ImageButton(context).apply {
                    setImageResource(R.drawable.ic_arrow_back)
                    setBackgroundColor(Color.TRANSPARENT)
                    contentDescription = getString(R.string.navigate_back)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    setOnClickListener { finish() }
                    toolbarButtons += this
                },
                LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT),
            )
            addView(
                TextView(context).apply {
                    text = getString(R.string.html_report_title)
                    textSize = 18f
                    setTextColor(reportPrimaryTextColor(initialDarkTheme))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), 0, dp(20), 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
            )
            toolbarTitle = getChildAt(1) as TextView
            addView(
                ImageButton(context).apply {
                    setImageResource(R.drawable.ic_more_vert)
                    setBackgroundColor(Color.TRANSPARENT)
                    contentDescription = getString(R.string.report_more_actions)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    setOnClickListener(::showReportMenu)
                    toolbarButtons += this
                },
                LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
        rootContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(reportChromeColor(initialDarkTheme))
                ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                    val bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                    )
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    insets
                }
                addView(
                    toolbar,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56),
                    ),
                )
                addView(
                    reportContent,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            }
        setContentView(rootContent)
        applyNativeReportTheme(initialDarkTheme)
    }

    private fun showReportMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.report_share).setOnMenuItemClickListener { shareReport(); true }
            menu.add(R.string.report_save_as).setOnMenuItemClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/html"
                        putExtra(Intent.EXTRA_TITLE, "tapilot-replay-$reportId.html")
                    },
                    SAVE_REPORT_REQUEST,
                )
                true
            }
            menu.add(R.string.report_save_downloads).setOnMenuItemClickListener {
                saveToDownloads()
                true
            }
            menu.add(R.string.report_open_external).setOnMenuItemClickListener {
                openExternally()
                true
            }
            menu.add(R.string.report_delete).setOnMenuItemClickListener {
                confirmDeleteReport()
                true
            }
            show()
        }
    }

    private fun confirmDeleteReport() {
        AlertDialog.Builder(this)
            .setTitle(R.string.report_delete_title)
            .setMessage(R.string.report_delete_description)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.report_delete) { _, _ ->
                runCatching {
                    check(report.delete()) { "Unable to delete report" }
                    DeletedReportStore.markDeleted(this, reportId)
                }.fold(
                    onSuccess = {
                        Toast.makeText(this, R.string.report_deleted, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onFailure = {
                        Toast.makeText(this, R.string.report_delete_failed, Toast.LENGTH_LONG).show()
                    },
                )
            }
            .show()
    }

    private fun reportUri() = FileProvider.getUriForFile(this, "$packageName.reports", report)

    private fun shareReport() {
        val uri = reportUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, report.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.report_share)))
    }

    @Deprecated("Activity result API retained for this platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAVE_REPORT_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        fileWorker.execute {
            val saved = runCatching {
                contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    report.inputStream().use { it.copyTo(output) }
                } ?: error("Unable to open destination")
            }
            runOnUiThread {
                if (!isDestroyed) Toast.makeText(
                    this,
                    if (saved.isSuccess) getString(R.string.report_saved, report.name)
                    else getString(R.string.report_save_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun installVideoExport(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            view.setDownloadListener { _, _, _, _, _ ->
                Toast.makeText(this, getString(R.string.video_save_failed, "Update Android System WebView"), Toast.LENGTH_LONG).show()
            }
            return
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.addWebMessageListener(
            view, "TapilotVideo", setOf("https://$REPORT_HOST"),
        ) { _, message, _, isMainFrame, reply ->
            if (!isMainFrame || isDestroyed) return@addWebMessageListener
            val payload = message.data ?: return@addWebMessageListener
            if (payload.length > 100_000) return@addWebMessageListener
            fileWorker.execute {
                var id = 0
                var completed = false
                var failure: String? = null
                val result = runCatching {
                    val request = JSONObject(payload)
                    id = request.getInt("id")
                    when (request.getString("method")) {
                        "begin" -> videoExport.begin(request.getLong("size"), request.getString("mime"))
                        "append" -> videoExport.append(request.getString("data"))
                        "finish" -> { videoExport.finish(); completed = true }
                        "abort" -> {
                            videoExport.close()
                            failure = request.optString("error", "Video export cancelled")
                        }
                        else -> error("Unknown video export operation")
                    }
                }
                if (result.isFailure) {
                    failure = result.exceptionOrNull()?.message ?: "Video export failed"
                    runCatching { videoExport.close() }
                }
                val response = JSONObject().put("id", id)
                if (result.isFailure) response.put("error", failure)
                val notice = failure
                runOnUiThread {
                    if (!isDestroyed) {
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                            reply.postMessage(response.toString())
                        }
                        if (completed || notice != null) Toast.makeText(
                            this,
                            if (completed) getString(R.string.video_saved)
                            else getString(R.string.video_save_failed, notice),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun openExternally() {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(reportUri(), "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(view, getString(R.string.report_open_external)))
    }

    private fun saveToDownloads() {
        runCatching {
            val fileName = "midscene-report-${System.currentTimeMillis()}.html"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/html")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create Downloads report")
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    report.inputStream().use { it.copyTo(output) }
                } ?: error("Unable to open Downloads report")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                contentResolver.delete(uri, null, null)
                throw error
            }
            fileName
        }.fold(
            onSuccess = {
                Toast.makeText(this, getString(R.string.report_saved, it), Toast.LENGTH_LONG).show()
            },
            onFailure = {
                Toast.makeText(this, it.message ?: getString(R.string.report_save_failed), Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun probeReportReady() {
        webView.evaluateJavascript(REPORT_READY_SCRIPT) { result ->
            val readinessResult = readiness.evaluate(result == "true")
            when (readinessResult.decision) {
                ReportReadinessTracker.Decision.READY -> progressBar.visibility = View.GONE
                ReportReadinessTracker.Decision.RETRY ->
                    handler.postDelayed(::probeReportReady, READINESS_INTERVAL_MS)
                ReportReadinessTracker.Decision.FAILED ->
                    showLoadError(checkNotNull(readinessResult.errorMessage))
            }
        }
    }

    private fun showLoadError(message: String) {
        handler.removeCallbacksAndMessages(null)
        progressBar.visibility = View.GONE
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun installInitialReportTheme(view: WebView, dark: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        WebViewCompat.addDocumentStartJavaScript(
            view,
            initialReportThemeScript(dark),
            setOf("https://$REPORT_HOST"),
        )
    }

    private fun installReportThemeBridge(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            view,
            REPORT_THEME_BRIDGE,
            setOf("https://$REPORT_HOST"),
        ) { _, message, _, isMainFrame, _ ->
            if (!isMainFrame || isDestroyed) return@addWebMessageListener
            when (message.data) {
                "dark" -> runOnUiThread {
                    AppTheme.set(this, "dark")
                    applyNativeReportTheme(true)
                }
                "light" -> runOnUiThread {
                    AppTheme.set(this, "light")
                    applyNativeReportTheme(false)
                }
            }
        }
    }

    private fun applySystemBarTheme(dark: Boolean) {
        val background = reportChromeColor(dark)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun applyNativeReportTheme(dark: Boolean) {
        applySystemBarTheme(dark)
        if (::webView.isInitialized) webView.setBackgroundColor(reportBackgroundColor(dark))
        if (::reportContent.isInitialized) {
            reportContent.setBackgroundColor(reportBackgroundColor(dark))
        }
        if (::rootContent.isInitialized) rootContent.setBackgroundColor(reportChromeColor(dark))
        if (::toolbar.isInitialized) toolbar.setBackgroundColor(reportChromeColor(dark))
        if (::toolbarTitle.isInitialized) {
            toolbarTitle.setTextColor(reportPrimaryTextColor(dark))
        }
        if (::errorView.isInitialized) {
            errorView.setTextColor(reportSecondaryTextColor(dark))
        }
        val tint = ColorStateList.valueOf(reportPrimaryTextColor(dark))
        toolbarButtons.forEach { it.imageTintList = tint }
    }

    private fun reportBackgroundColor(dark: Boolean): Int =
        if (dark) getColor(R.color.brand_dark_background) else getColor(R.color.brand_background)

    private fun reportChromeColor(dark: Boolean): Int =
        if (dark) getColor(R.color.brand_dark_surface) else getColor(R.color.brand_surface)

    private fun reportPrimaryTextColor(dark: Boolean): Int =
        if (dark) getColor(R.color.brand_dark_text) else getColor(R.color.brand_text)

    private fun reportSecondaryTextColor(dark: Boolean): Int =
        if (dark) getColor(R.color.brand_dark_muted) else getColor(R.color.brand_muted)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        fileWorker.execute { runCatching { videoExport.close() } }
        fileWorker.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        // Keep the interception implementation available, but disabled. This makes restoring
        // the custom report logo an explicit one-line change without rewriting exported HTML.
        private const val REPLACE_REPORT_LOGO = false
        private const val SAVE_REPORT_REQUEST = 3101
        const val EXTRA_REPORT_ID = "lifeos.md.tapilot.extra.REPORT_ID"
        private const val REPORT_HOST = "midscene-report.local"
        private const val REPORT_PATH = "/reports/"
        private const val REPORT_THEME_BRIDGE = "TapilotReportTheme"
        private const val READINESS_INTERVAL_MS = 250L
        private const val MAX_READINESS_ATTEMPTS = 48
        private const val REPORT_READY_SCRIPT =
            "Boolean(document.querySelector('#root')?.childElementCount)"
        private fun initialReportThemeScript(dark: Boolean) =
            "localStorage.setItem('midscene-dark-mode','${dark}');"

        private fun mobileCompatibilityScript(dark: Boolean) = """
            (function() {
              localStorage.setItem('midscene-dark-mode', '${dark}');
              document.documentElement.dataset.theme = '${if (dark) "dark" else "light"}';

              var id = 'midscene-android-mobile-fixes';
              if (!document.getElementById(id)) {
                var style = document.createElement('style');
                style.id = id;
                style.textContent = '${BrandColors.reportCss}' + '@media (max-width:1024px),(pointer:coarse){' +
                  '.page-nav-version,.page-nav .theme-divider{display:none!important}' +
                  '.page-nav-left{overflow:hidden!important}' +
                  '.mobile-player-fullscreen-button{display:none!important}' +
                  '.main-right .main-content{box-sizing:border-box!important;' +
                  'padding-bottom:max(20px,env(safe-area-inset-bottom))!important}' +
                  'html:not([data-theme="dark"]) .mobile-report-tabs{' +
                  'background:var(--primary-soft)!important}' +
                  'html:not([data-theme="dark"]) .mobile-report-tabs button.is-active{' +
                  'color:var(--primary-dark)!important;background:var(--brand-bubble)!important}' +
                  'html[data-theme="dark"] .mobile-report-tabs{background:var(--surface)!important}' +
                  'html[data-theme="dark"] .mobile-report-tabs button{' +
                  'color:rgba(248,250,253,.62)!important}' +
                  'html[data-theme="dark"] .mobile-report-tabs button.is-active{' +
                  'color:var(--text)!important;background:var(--primary-soft)!important;' +
                  'box-shadow:0 2px 8px rgba(0,0,0,.28)!important}' +
                  '}';
                document.head.appendChild(style);
              }

              var attempts = 0;
              function openPlayerByDefault() {
                var player = Array.from(document.querySelectorAll('button')).find(function(button) {
                  return button.textContent.trim() === 'Player';
                });
                if (player) {
                  if (!player.classList.contains('is-active')) player.click();
                  return;
                }
                if (attempts++ < 80) setTimeout(openPlayerByDefault, 100);
              }
              openPlayerByDefault();

              var lastTheme = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
              new MutationObserver(function() {
                var theme = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
                if (theme === lastTheme) return;
                lastTheme = theme;
                window.$REPORT_THEME_BRIDGE?.postMessage(theme);
              }).observe(document.documentElement, {
                attributes: true,
                attributeFilter: ['data-theme']
              });
            })();
        """.trimIndent()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
