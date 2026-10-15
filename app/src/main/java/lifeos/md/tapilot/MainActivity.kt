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
import lifeos.md.tapilot.services.ServiceGateway
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
    private var pendingVoiceLaunch by mutableStateOf(false)
    private lateinit var rootView: ComposeView
    private lateinit var chatController: ChatController
    private lateinit var coordinator: NativeConversationCoordinator
    private lateinit var nativeBridge: ServiceGateway
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
        // This activity is exported for launch/share. External extras never authorize recording.
        // Only our permission flow (or its saved lifecycle state) may resume a voice request.
        pendingVoiceLaunch = savedInstanceState?.getBoolean(EXTRA_START_VOICE) ?: false
        intent.removeExtra(EXTRA_START_VOICE)
        val runtime = (application as TapilotApplication).assistant
        runtime.attach(this)
        nativeBridge = runtime.bridge
        chatController = ChatController(this, onSurfaceChanged = {}, picker = ::pickAttachment,
            nativeCommand = {
                if (it.optString("action") == "settings" && globalPanel) configureGlobalPanel(false)
                runtime.command(it)
            })
        coordinator = runtime.coordinator
        runtime.observe = { chatController.accept(it); startPendingVoice() }
        runtime.focus = { if (!pendingVoiceLaunch && !runtime.startingVoice && !runtime.voiceState.active) chatController.requestFocus() }
        runtime.picker = ::pickAttachment
        val store = ConversationStore(File(filesDir, "native-conversation.json"))
        coordinator.globalMode = globalPanel
        rootView.setContent {
            when {
                coordinator.settingsOpen -> NativeSettingsScreen(coordinator, this, nativeBridge,
                    onThemeChanged = { applyWindowTheme(rootView); coordinator.publish() }, onLanguageChanged = { recreate() })
                coordinator.advancedOpen -> NativeAdvancedScreen(coordinator)
                else -> ChatScreen(chatController, dismissible = globalPanel, voiceConnecting = pendingVoiceLaunch || runtime.startingVoice,
                    onExpand = { configureGlobalPanel(false) }, onDismiss = { moveTaskToBack(true) })
            }
        }
        setContentView(rootView)
        receiveExternalIntent(intent)
        if (coordinator.ready) coordinator.publish()
        else importer = LegacyConversationImport(this, store).also { loader -> loader.start { result ->
            result.fold(onSuccess = { coordinator.initialize() }, onFailure = { chatController.showError(it.message ?: "无法导入旧数据，请重新打开应用") })
        } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (chatController.voiceState.active) { moveTaskToBack(true); return }
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
        pendingVoiceLaunch = false
        intent.removeExtra(EXTRA_START_VOICE)
        configureGlobalPanel(panel)
        if (panel && !(application as TapilotApplication).assistant.voiceEntryPending) coordinator.openGlobalConversation()
        receiveExternalIntent(intent)
        coordinator.receiveLaunchContext()
        if (intent.getBooleanExtra(EXTRA_FOCUS_TASK_INPUT, false) && !pendingVoiceLaunch) chatController.requestFocus()
        startPendingVoice()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_GLOBAL_PANEL, globalPanel)
        outState.putBoolean(EXTRA_START_VOICE, pendingVoiceLaunch)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (::rootView.isInitialized) applyWindowTheme(rootView)
        if (::coordinator.isInitialized) {
            coordinator.publish(); coordinator.receiveLaunchContext()
            if (coordinator.ready) {
                coordinator.removeReports(DeletedReportStore.consume(this))
                (application as TapilotApplication).assistant.finishVoiceEntry()
            }
        }
    }
    override fun onPostResume() { super.onPostResume(); startPendingVoice() }
    private fun startPendingVoice() {
        if (!pendingVoiceLaunch || !::chatController.isInitialized || !coordinator.ready || chatController.state.running ||
            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        pendingVoiceLaunch = false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if (!chatController.voiceState.active) chatController.startVoiceConversation(allowExistingInput = true)
        else if (chatController.voiceState.phase in listOf(lifeos.md.tapilot.voice.VoicePhase.Paused, lifeos.md.tapilot.voice.VoicePhase.Error)) chatController.resumeVoice()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7102 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingVoiceLaunch = true; handler.post { startPendingVoice() }
        } else if (requestCode == 7102) {
            chatController.showError(if (chatController.state.chinese) "麦克风权限未开启，允许后可开始语音对答" else "Allow microphone access to start voice chat")
        }
    }
    override fun onStart() { super.onStart(); handler.post(refresh) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && globalPanel && ::chatController.isInitialized && chatController.voiceState.active &&
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            Log.d("TapilotVoice", "Voice chat window focused")
        if (!hasFocus && ::chatController.isInitialized && (chatController.recording || chatController.transcribing)) chatController.cancelRecording()
    }
    override fun onStop() {
        if (::chatController.isInitialized && (chatController.recording || chatController.transcribing)) chatController.cancelRecording()
        handler.removeCallbacks(refresh); super.onStop()
    }
    override fun onDestroy() {
        importer?.close(); attachmentCallback = null; attachmentWorker.shutdown()
        if (::chatController.isInitialized) chatController.close()
        (application as TapilotApplication).assistant.detach()
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
            gravity = Gravity.TOP
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            dimAmount = 0f
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (::rootView.isInitialized) applyWindowTheme(rootView)
    }
    private fun applyWindowTheme(root: View) {
        val dark = AppTheme.isDark(this)
        val background = if (dark) AppTheme.color(this, R.color.brand_dark_header) else AppTheme.color(this, R.color.brand_header)
        root.setBackgroundColor(background)
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
        const val EXTRA_START_VOICE = "lifeos.md.tapilot.extra.START_VOICE"
        const val EXTRA_GLOBAL_PANEL = "lifeos.md.tapilot.extra.GLOBAL_PANEL"
        const val EXTRA_FOCUS_TASK_INPUT = "lifeos.md.tapilot.extra.FOCUS_TASK_INPUT"
        private const val NOTIFICATION_PERMISSION_REQUEST = 2108
        private const val ATTACHMENT_REQUEST = 2112
    }
}
