package lifeos.md.tapilot

import lifeos.md.tapilot.theme.BrandColors

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import lifeos.md.tapilot.report.ReportAssetRoutes
import lifeos.md.tapilot.report.DeletedReportStore
import lifeos.md.tapilot.report.ReportReadinessTracker
import lifeos.md.tapilot.report.ReportExports
import lifeos.md.tapilot.report.ReportRepository
import java.io.ByteArrayInputStream
import java.io.File

class ReportActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private lateinit var exports: ReportExports
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
        reportId = intent.getStringExtra(EXTRA_REPORT_ID).orEmpty()
        val reportsDirectory = File(filesDir, "reports")
        val selected = runCatching { ReportRepository(reportsDirectory).use { it.reportFile(reportId) } }
            .getOrNull()?.takeIf { it.isFile }
        if (selected == null) {
            setContentView(TextView(this).apply {
                text = getString(R.string.report_unavailable)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setTextColor(reportPrimaryTextColor(initialDarkTheme))
                setBackgroundColor(reportBackgroundColor(initialDarkTheme))
            })
            return
        }
        report = selected
        exports = ReportExports(this, report)

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
                        mobileCompatibilityScript(initialDarkTheme, AppTheme.paletteId(this@ReportActivity)),
                        null,
                    )
                    probeReportReady()
                }
            }
            exports.installVideoExport(this, REPORT_HOST)
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
            elevation = 0f
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
            menu.add(R.string.report_share).setOnMenuItemClickListener { exports.shareReport(); true }
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
                exports.saveToDownloads()
                true
            }
            menu.add(R.string.report_open_external).setOnMenuItemClickListener {
                exports.openExternally()
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

    @Deprecated("Activity result API retained for this platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::exports.isInitialized && requestCode == SAVE_REPORT_REQUEST && resultCode == RESULT_OK)
            data?.data?.let(exports::saveToUri)
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
        if (dark) AppTheme.color(this, R.color.brand_dark_background) else AppTheme.color(this, R.color.brand_background)

    private fun reportChromeColor(dark: Boolean): Int =
        if (dark) AppTheme.color(this, R.color.brand_dark_header) else AppTheme.color(this, R.color.brand_header)

    private fun reportPrimaryTextColor(dark: Boolean): Int =
        if (dark) AppTheme.color(this, R.color.brand_dark_text) else AppTheme.color(this, R.color.brand_text)

    private fun reportSecondaryTextColor(dark: Boolean): Int =
        if (dark) AppTheme.color(this, R.color.brand_dark_muted) else AppTheme.color(this, R.color.brand_muted)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::exports.isInitialized) exports.close()
        if (::webView.isInitialized) webView.destroy()
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

        private fun mobileCompatibilityScript(dark: Boolean, paletteId: String) = """
            (function() {
              localStorage.setItem('midscene-dark-mode', '${dark}');
              document.documentElement.dataset.palette = '$paletteId';
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
                  'body,#root,.page-container,.ant-layout,.main,.main-right{background:var(--background)!important;color:var(--text)!important}' +
                  '.main-content,.main-left{background:var(--surface)!important;color:var(--text)!important}' +
                  '.page-nav{background:var(--header)!important;color:var(--text)!important}' +
                  '.mobile-report-tabs{padding:4px!important;border:1px solid var(--line)!important;border-radius:18px!important;box-shadow:none!important;background:var(--input)!important}' +
                  '.mobile-report-tabs button{border-radius:14px!important;min-height:40px!important;font-size:14px!important;box-shadow:none!important}' +
                  'html:not([data-theme="dark"]) .mobile-report-tabs button.is-active{' +
                  'color:var(--primary-dark)!important;background:var(--surface)!important}' +
                  'html[data-theme="dark"] .mobile-report-tabs button{' +
                  'color:var(--text-secondary)!important}' +
                  'html[data-theme="dark"] .mobile-report-tabs button.is-active{' +
                  'color:var(--text)!important;background:var(--primary-soft)!important;' +
                  'box-shadow:none!important}' +
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
