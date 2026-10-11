package lifeos.md.tapilot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import lifeos.md.tapilot.bridge.AttachmentReader
import lifeos.md.tapilot.bridge.NativeJavascriptBridge
import lifeos.md.tapilot.chat.ChatController
import lifeos.md.tapilot.chat.ChatScreen
import lifeos.md.tapilot.conversation.ConversationStore
import lifeos.md.tapilot.conversation.LegacyConversationImport
import lifeos.md.tapilot.conversation.NativeConversationCoordinator
import lifeos.md.tapilot.report.DeletedReportStore
import lifeos.md.tapilot.settings.NativeSettingsScreen
import lifeos.md.tapilot.settings.NativeAdvancedScreen
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguage.wrap(newBase)) }
    private var globalPanel by mutableStateOf(false)
    private lateinit var rootView: ComposeView
    private lateinit var chatController: ChatController
    private lateinit var coordinator: NativeConversationCoordinator
    private lateinit var nativeBridge: NativeJavascriptBridge
    private var importer: LegacyConversationImport? = null
    private val attachmentWorker = Executors.newSingleThreadExecutor()
    private var attachmentCallback: ((Result<JSONObject?>) -> Unit)? = null
    private var cameraOutput: Uri? = null
    private var cameraFile: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { if (::coordinator.isInitialized) coordinator.publish(); handler.postDelayed(this, 2_000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        rootView = ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed) }
        configureGlobalPanel(savedInstanceState?.takeIf { it.containsKey(EXTRA_GLOBAL_PANEL) }?.getBoolean(EXTRA_GLOBAL_PANEL)
            ?: (intent.getBooleanExtra(EXTRA_GLOBAL_PANEL, false) || intent.action == Intent.ACTION_ASSIST))
        nativeBridge = NativeJavascriptBridge(activity = this, onProgress = { method -> if (::coordinator.isInitialized) coordinator.phoneProgress(method) }, onPickAttachment = ::pickAttachment)
        chatController = ChatController(this, onSurfaceChanged = {}, picker = ::pickAttachment,
            nativeCommand = {
                if (it.optString("action") == "settings" && globalPanel) configureGlobalPanel(false)
                coordinator.command(it)
            })
        val store = ConversationStore(File(filesDir, "native-conversation.json"))
        coordinator = NativeConversationCoordinator(this, nativeBridge, store, chatController::accept, chatController::requestFocus)
        coordinator.globalMode = globalPanel
        rootView.setContent {
            when {
                coordinator.settingsOpen -> NativeSettingsScreen(coordinator, this, nativeBridge,
                    onThemeChanged = { applyWindowTheme(rootView); coordinator.publish() }, onLanguageChanged = { recreate() })
                coordinator.advancedOpen -> NativeAdvancedScreen(coordinator)
                else -> ChatScreen(chatController, compact = globalPanel,
                    onExpand = { configureGlobalPanel(false) }, onDismiss = { moveTaskToBack(true) })
            }
        }
        setContentView(rootView)
        receiveExternalIntent(intent)
        importer = LegacyConversationImport(this, store).also { loader -> loader.start { result ->
            result.fold(onSuccess = { coordinator.initialize() }, onFailure = { chatController.showError(it.message ?: "无法导入旧数据，请重新打开应用") })
        } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (chatController.recording || chatController.transcribing) { chatController.cancelRecording(); return }
                val handled = coordinator.back()
                if (!handled && globalPanel) { moveTaskToBack(true); return }
                if (!handled) { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
            }
        })
        requestNotificationPermissionIfNeeded()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        val panel = intent.getBooleanExtra(EXTRA_GLOBAL_PANEL, false) || intent.action == Intent.ACTION_ASSIST
        configureGlobalPanel(panel)
        if (panel) coordinator.openGlobalConversation()
        receiveExternalIntent(intent)
        coordinator.receiveLaunchContext()
        if (intent.getBooleanExtra(EXTRA_FOCUS_TASK_INPUT, false)) chatController.requestFocus()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_GLOBAL_PANEL, globalPanel)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (::rootView.isInitialized) applyWindowTheme(rootView)
        if (::coordinator.isInitialized) {
            coordinator.publish(); coordinator.receiveLaunchContext()
            if (coordinator.ready) coordinator.removeReports(DeletedReportStore.consume(this))
        }
    }
    override fun onStart() { super.onStart(); handler.post(refresh) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::chatController.isInitialized && (chatController.recording || chatController.transcribing)) chatController.cancelRecording()
    }
    override fun onStop() {
        if (::chatController.isInitialized && (chatController.recording || chatController.transcribing)) chatController.cancelRecording()
        handler.removeCallbacks(refresh); super.onStop()
    }
    override fun onDestroy() {
        importer?.close(); attachmentCallback = null; attachmentWorker.shutdown()
        if (::chatController.isInitialized) chatController.close()
        if (::coordinator.isInitialized) coordinator.close()
        if (::nativeBridge.isInitialized) nativeBridge.close()
        handler.removeCallbacksAndMessages(null); super.onDestroy()
    }
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
            if (::chatController.isInitialized) chatController.requestFocus()
        }
    }

    @Suppress("DEPRECATION")
    private fun configureGlobalPanel(panel: Boolean) {
        globalPanel = panel
        if (::coordinator.isInitialized) coordinator.globalMode = panel
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.attributes = window.attributes.apply {
            gravity = if (panel) Gravity.BOTTOM else Gravity.TOP
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = if (panel) (resources.displayMetrics.heightPixels * .72f).toInt() else WindowManager.LayoutParams.MATCH_PARENT
            dimAmount = if (panel) .16f else 0f
        }
        if (panel) window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (::rootView.isInitialized) applyWindowTheme(rootView)
    }
    private fun applyWindowTheme(root: View) {
        val dark = AppTheme.isDark(this)
        val background = if (dark) AppTheme.color(this, R.color.brand_dark_header) else AppTheme.color(this, R.color.brand_header)
        root.setBackgroundColor(if (globalPanel) Color.TRANSPARENT else background)
        window.statusBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
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
        const val EXTRA_GLOBAL_PANEL = "lifeos.md.tapilot.extra.GLOBAL_PANEL"
        const val EXTRA_FOCUS_TASK_INPUT = "lifeos.md.tapilot.extra.FOCUS_TASK_INPUT"
        private const val NOTIFICATION_PERMISSION_REQUEST = 2108
        private const val ATTACHMENT_REQUEST = 2112
    }
}
