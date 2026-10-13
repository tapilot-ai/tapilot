package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppForegroundTracker
import lifeos.md.tapilot.TaskExecutionStatus
import lifeos.md.tapilot.TaskExecutionTracker
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Suppress("DEPRECATION")
class MidsceneAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val cancelledSessions = ConcurrentHashMap.newKeySet<String>()
    private lateinit var taskEffects: TaskEffectController
    private lateinit var floatingBall: FloatingBallController
    @Volatile private var latestCapture: CapturePayload? = null
    private val extendedActions by lazy { ExtendedDeviceActions(this) }
    private val continuousEffectCapture by lazy { ContinuousEffectCapture(this) }
    private var lastTapPoint: NativeAction.Point? = null
    private var lastTapAtMillis = 0L

    fun capabilityDetails(): org.json.JSONObject = org.json.JSONObject()
        .put("systemActions", org.json.JSONArray(extendedActions.availableSystemActions()))
        .put("apps", extendedActions.apps())

    override fun onServiceConnected() {
        AppLanguage.apply(this)
        if (::floatingBall.isInitialized) floatingBall.close()
        if (::taskEffects.isInitialized) taskEffects.close()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        taskEffects = TaskEffectController(this, windowManager, handler)
        floatingBall = FloatingBallController(this, windowManager).apply {
            setAppForeground(AppForegroundTracker.isForeground)
            setTaskRunning(TaskExecutionTracker.isActive)
        }
        // Publish only after every operation dependency is ready. WebView RPC can
        // call current() immediately after receiving the connection callback.
        instance = WeakReference(this)
        notifyConnectionChanged(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        publishDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (::floatingBall.isInitialized) floatingBall.close()
        if (::taskEffects.isInitialized) taskEffects.close()
        cancelledSessions.clear()
        latestCapture = null
        publishDisconnected()
        super.onDestroy()
    }

    private fun publishDisconnected() {
        if (instance.get() !== this) return
        instance.clear()
        notifyConnectionChanged(false)
    }

    fun cancelSession(sessionId: String) {
        cancelledSessions.add(sessionId)
        handler.postDelayed(
            { cancelledSessions.remove(sessionId) },
            CANCELLED_SESSION_RETENTION_MS,
        )
    }

    fun completeSession(sessionId: String) {
        cancelledSessions.remove(sessionId)
    }

    fun validateFrame(frameId: String) {
        val capture = latestCapture
        if (capture?.frameId != frameId) throw StaleFrameException()
        val metrics = deviceDisplayMetrics()
        val rotation = getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
            ?.rotation ?: 0
        if (capture.rotation != rotation ||
            capture.screenWidth != metrics.widthPixels ||
            capture.screenHeight != metrics.heightPixels
        ) {
            throw StaleFrameException()
        }
    }

    fun updateTaskStatus(status: TaskExecutionStatus, detail: String? = null) {
        TaskExecutionTracker.update(status)
        onMain {
            taskEffects.update(status, detail)
            floatingBall.setTaskRunning(TaskExecutionTracker.isActive)
        }
    }

    fun refreshTaskRunning() = onMain {
        if (::floatingBall.isInitialized) {
            floatingBall.setTaskRunning(TaskExecutionTracker.isActive)
        }
    }

    fun setAppForeground(foreground: Boolean) = onMain {
        if (::floatingBall.isInitialized) floatingBall.setAppForeground(foreground)
    }

    fun setFloatingBallEnabled(enabled: Boolean) = onMain {
        if (::floatingBall.isInitialized) floatingBall.setEnabled(enabled)
    }

    fun showGlobalResult(message: String) = onMain {
        if (::floatingBall.isInitialized) floatingBall.showResult(message)
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }

    internal fun previewEdgeEffect(settings: EdgeEffectGeometrySettings) {
        if (::taskEffects.isInitialized) taskEffects.preview(settings)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::floatingBall.isInitialized) { floatingBall.updatePosition(); floatingBall.refreshTheme() }
    }

    fun capture(sessionId: String, recordFrame: Boolean = true, callback: (Result<CapturePayload>) -> Unit) {
        if (isCancelled(sessionId, callback)) return
        if (continuousEffectCapture.tryCapture(deliver = { result ->
            if (cancelledSessions.contains(sessionId)) {
                result.hardwareBuffer.close()
                callback(Result.failure(SessionCancelledException()))
            } else {
                runCatching { encodeCapture(result, recordFrame) }.fold(
                    onSuccess = { callback(Result.success(it)) },
                    onFailure = { handler.postDelayed({ captureFullDisplay(sessionId, callback, recordFrame = recordFrame) }, 350L) },
                )
            }
        }, fallback = {
            // Some devices share a rate limit between window and display captures.
            handler.postDelayed({ captureFullDisplay(sessionId, callback, recordFrame = recordFrame) }, 350L)
        })) return
        captureFullDisplay(sessionId, callback, recordFrame = recordFrame)
    }

    private fun encodeCapture(result: ScreenshotResult, recordFrame: Boolean): CapturePayload {
        val hardwareBuffer = result.hardwareBuffer
        val bitmap = try {
            Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                ?: error("Unable to copy screenshot buffer")
        } finally { hardwareBuffer.close() }
        try {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode screenshot" }
            val metrics = deviceDisplayMetrics()
            return CapturePayload(
                dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray()),
                frameId = UUID.randomUUID().toString(), capturedAt = System.currentTimeMillis(),
                rotation = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0,
                screenWidth = metrics.widthPixels, screenHeight = metrics.heightPixels,
                imageWidth = bitmap.width, imageHeight = bitmap.height,
            ).also { if (recordFrame) latestCapture = it }
        } finally { bitmap.recycle() }
    }

    private fun captureFullDisplay(
        sessionId: String,
        callback: (Result<CapturePayload>) -> Unit,
        retryAttempt: Int = 0,
        recordFrame: Boolean = true,
    ) {
        if (isCancelled(sessionId, callback)) return
        // Full-display capture includes accessibility overlays. Hide only while
        // Android latches the frame so AI input and report-player frames stay
        // clean. The animator keeps advancing and resumes at its current phase.
        val hiddenTaskEffect = taskEffects.hideForCapture()
        val hiddenFloatingBall = floatingBall.hideForCapture()
        handler.postDelayed({
            if (isCancelled(sessionId, callback)) {
                taskEffects.restoreAfterCapture(hiddenTaskEffect)
                floatingBall.restoreAfterCapture(hiddenFloatingBall)
                return@postDelayed
            }
            runCatching { takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        try {
                            if (cancelledSessions.contains(sessionId)) {
                                result.hardwareBuffer.close()
                                callback(Result.failure(SessionCancelledException()))
                            } else callback(Result.success(encodeCapture(result, recordFrame)))
                        } catch (error: Throwable) {
                            callback(Result.failure(error))
                        } finally {
                            taskEffects.restoreAfterCapture(hiddenTaskEffect)
                            floatingBall.restoreAfterCapture(hiddenFloatingBall)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        taskEffects.restoreAfterCapture(hiddenTaskEffect)
                        floatingBall.restoreAfterCapture(hiddenFloatingBall)
                        val retryDelay = ScreenshotFailurePolicy.retryDelayMillis(
                            errorCode,
                            retryAttempt,
                        )
                        if (retryDelay != null) {
                            handler.postDelayed(
                                { captureFullDisplay(sessionId, callback, retryAttempt + 1, recordFrame) },
                                retryDelay,
                            )
                            return
                        }
                        val disconnected =
                            errorCode == ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR &&
                            runCatching { serviceInfo == null }.getOrDefault(true)
                        callback(Result.failure(IllegalStateException(
                            ScreenshotFailurePolicy.message(errorCode, disconnected),
                        )))
                    }
                },
            ) }.onFailure {
                taskEffects.restoreAfterCapture(hiddenTaskEffect)
                floatingBall.restoreAfterCapture(hiddenFloatingBall)
                callback(Result.failure(it))
            }
        }, OVERLAY_SETTLE_MS)
    }

    fun execute(sessionId: String, action: NativeAction, callback: (Result<Unit>) -> Unit) {
        if (isCancelled(sessionId, callback)) return
        when (action) {
            is NativeAction.System -> if (action.action == "saveScreenshot") {
                captureFullDisplay(sessionId, callback = { captureResult ->
                    captureResult.fold(onSuccess = { payload ->
                        screenshotSaveExecutor.execute {
                            val result = runCatching {
                                ScreenshotStore.save(contentResolver, payload.dataUrl) { sessionId in cancelledSessions }
                            }
                            handler.post { callback(result) }
                        }
                    }, onFailure = { callback(Result.failure(it)) })
                })
            } else callback(runCatching { extendedActions.system(action.action) })
            is NativeAction.EditText -> callback(runCatching { extendedActions.edit(action) })
            is NativeAction.MultiGesture -> {
                val (scaleX, scaleY) = gestureCoordinateScale()
                extendedActions.gesture(action, scaleX, scaleY, { sessionId in cancelledSessions }, callback)
            }
            is NativeAction.Tap -> gesture(
                action.x,
                action.y,
                action.x,
                action.y,
                action.duration ?: 50L,
            ) { result ->
                if (result.isSuccess) {
                    lastTapPoint = NativeAction.Point(action.x, action.y)
                    lastTapAtMillis = System.currentTimeMillis()
                }
                callback(result)
            }
            is NativeAction.DoubleTap -> gesture(
                action.x,
                action.y,
                action.x,
                action.y,
                50L,
            ) { first ->
                if (first.isFailure) callback(first)
                else handler.postDelayed({
                    if (!isCancelled(sessionId, callback)) {
                        gesture(action.x, action.y, action.x, action.y, 50L, callback)
                    }
                }, 80L)
            }
            is NativeAction.LongPress -> gesture(
                action.x,
                action.y,
                action.x,
                action.y,
                action.duration ?: 800L,
                callback,
            )
            is NativeAction.Swipe -> gesture(
                action.startX,
                action.startY,
                action.endX,
                action.endY,
                action.duration ?: 350L,
                callback,
            )
            is NativeAction.Pinch -> pinchGesture(action, callback)
            is NativeAction.Input -> inputText(sessionId, action, callback)
            is NativeAction.ClearInput -> inputText(
                sessionId,
                NativeAction.Input("", action.x, action.y, true, false),
                callback,
            )
            is NativeAction.Key -> performKey(action.keyName, callback)
            is NativeAction.CursorMove -> performCursorMove(
                action.direction,
                action.times,
                callback,
            )
            NativeAction.Back -> globalAction(GLOBAL_ACTION_BACK, callback)
            NativeAction.Home -> globalAction(GLOBAL_ACTION_HOME, callback)
            NativeAction.RecentApps -> globalAction(GLOBAL_ACTION_RECENTS, callback)
            is NativeAction.AppDetails -> openAppDetails(action.packageOrName, callback)
            is NativeAction.Launch -> launch(action.uri, callback)
        }
    }

    private fun gesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long,
        callback: (Result<Unit>) -> Unit,
    ) {
        val (scaleX, scaleY) = gestureCoordinateScale()
        val path = Path().apply {
            moveTo(startX * scaleX, startY * scaleY)
            if (startX != endX || startY != endY) {
                lineTo(endX * scaleX, endY * scaleY)
            }
        }
        val description = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.coerceAtLeast(1)))
            .build()
        val accepted = dispatchGesture(
            description,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback(Result.success(Unit))
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback(Result.failure(IllegalStateException("Gesture was cancelled")))
                }
            },
            handler,
        )
        if (!accepted) callback(Result.failure(IllegalStateException("Gesture was rejected")))
    }

    private fun pinchGesture(action: NativeAction.Pinch, callback: (Result<Unit>) -> Unit) {
        val (scaleX, scaleY) = gestureCoordinateScale()
        val metrics = deviceDisplayMetrics()
        val geometry = pinchGeometry(action, metrics.widthPixels, metrics.heightPixels)
        val centerX = geometry.centerX * scaleX
        val centerY = geometry.centerY * scaleY
        val distanceScale = minOf(scaleX, scaleY)
        val startRadius = geometry.startRadius * distanceScale
        val endRadius = geometry.endRadius * distanceScale
        val leftFinger = Path().apply {
            moveTo(centerX - startRadius, centerY)
            lineTo(centerX - endRadius, centerY)
        }
        val rightFinger = Path().apply {
            moveTo(centerX + startRadius, centerY)
            lineTo(centerX + endRadius, centerY)
        }
        val duration = action.duration.coerceAtLeast(1L)
        val description = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(leftFinger, 0, duration))
            .addStroke(GestureDescription.StrokeDescription(rightFinger, 0, duration))
            .build()
        val accepted = dispatchGesture(
            description,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback(Result.success(Unit))
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback(Result.failure(IllegalStateException("Pinch gesture was cancelled")))
                }
            },
            handler,
        )
        if (!accepted) {
            callback(Result.failure(IllegalStateException("Pinch gesture was rejected")))
        }
    }

    private fun gestureCoordinateScale(): Pair<Float, Float> {
        val metrics = deviceDisplayMetrics()
        val currentDisplay = getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY) ?: return 1f to 1f
        val mode = currentDisplay.mode
        val rotated = currentDisplay.rotation == android.view.Surface.ROTATION_90 ||
            currentDisplay.rotation == android.view.Surface.ROTATION_270
        val physicalWidth = if (rotated) mode.physicalHeight else mode.physicalWidth
        val physicalHeight = if (rotated) mode.physicalWidth else mode.physicalHeight
        return calculateGestureCoordinateScale(
            logicalWidth = metrics.widthPixels,
            logicalHeight = metrics.heightPixels,
            physicalWidth = physicalWidth,
            physicalHeight = physicalHeight,
        )
    }

    private fun inputText(
        sessionId: String,
        action: NativeAction.Input,
        callback: (Result<Unit>) -> Unit,
    ) {
        val applyText = {
            if (!isCancelled(sessionId, callback)) {
                val node = findInputNode(action.x, action.y)
                val inputConnectionAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasActiveAccessibilityInputConnection()
                } else {
                    false
                }
                val fallbackPoint = resolveInputFallbackPoint(action)
                when (TextInputFallbackPolicy.resolve(
                    hasEditableNode = node != null,
                    hasInputConnection = inputConnectionAvailable,
                    hasRecentTapPoint = fallbackPoint != null,
                )) {
                    TextInputMethod.ACCESSIBILITY_NODE -> {
                        requireNotNull(node)
                        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        val value = if (action.replace) {
                            action.text
                        } else {
                            "${node.text?.toString().orEmpty()}${action.text}"
                        }
                        val args = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                value,
                            )
                        }
                        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        node.recycle()
                        if (!success) {
                            callback(Result.failure(IllegalStateException("Input rejected ACTION_SET_TEXT")))
                        } else if (action.autoDismissKeyboard && isKeyboardVisible()) {
                            handler.postDelayed({
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                callback(Result.success(Unit))
                            }, 80L)
                        } else {
                            callback(Result.success(Unit))
                        }
                    }
                    TextInputMethod.INPUT_CONNECTION -> {
                        node?.recycle()
                        inputViaAccessibilityInputConnection(action, callback)
                    }
                    TextInputMethod.CLIPBOARD_PASTE -> {
                        node?.recycle()
                        pasteViaClipboard(
                            sessionId,
                            action.text,
                            requireNotNull(fallbackPoint),
                            callback,
                        )
                    }
                    TextInputMethod.UNAVAILABLE -> {
                        node?.recycle()
                        callback(Result.failure(IllegalStateException(
                            "No editable input, active input connection, or recent input-field tap is available",
                        )))
                    }
                }
            }
        }
        if (action.x != null && action.y != null) {
            gesture(action.x, action.y, action.x, action.y, 50L) { result ->
                if (result.isFailure) callback(result)
                else handler.postDelayed(applyText, 120L)
            }
        } else {
            applyText()
        }
    }

    private fun resolveInputFallbackPoint(action: NativeAction.Input): NativeAction.Point? {
        if (action.x != null && action.y != null) return NativeAction.Point(action.x, action.y)
        return lastTapPoint?.takeIf {
            TextInputFallbackPolicy.isRecentTap(lastTapAtMillis, System.currentTimeMillis())
        }
    }

    private fun inputViaAccessibilityInputConnection(
        action: NativeAction.Input,
        callback: (Result<Unit>) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            callback(Result.failure(UnsupportedOperationException(
                "Accessibility input connections require Android 13 or newer",
            )))
            return
        }
        inputViaAccessibilityInputConnectionApi33(action, callback)
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasActiveAccessibilityInputConnection(): Boolean =
        inputMethod?.currentInputStarted == true && inputMethod?.currentInputConnection != null

    @android.annotation.TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun inputViaAccessibilityInputConnectionApi33(
        action: NativeAction.Input,
        callback: (Result<Unit>) -> Unit,
    ) {
        val accessibilityInputMethod = inputMethod
        val connection = accessibilityInputMethod?.currentInputConnection
        if (accessibilityInputMethod?.currentInputStarted != true || connection == null) {
            callback(Result.failure(IllegalStateException("No active accessibility input connection")))
            return
        }
        runCatching {
            if (action.replace) connection.performContextMenuAction(android.R.id.selectAll)
            connection.commitText(action.text, 1, null)
        }.fold(
            onSuccess = { callback(Result.success(Unit)) },
            onFailure = { callback(Result.failure(it)) },
        )
    }

    private fun pasteViaClipboard(
        sessionId: String,
        text: String,
        point: NativeAction.Point,
        callback: (Result<Unit>) -> Unit,
    ) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_LABEL, text))
        gesture(point.x, point.y, point.x, point.y, 650L) { longPressResult ->
            if (longPressResult.isFailure) {
                callback(longPressResult)
                return@gesture
            }
            handler.postDelayed({
                if (isCancelled(sessionId, callback)) return@postDelayed
                val pasteNode = findPasteActionNode()
                if (pasteNode == null) {
                    callback(Result.failure(IllegalStateException(
                        "Clipboard fallback could not find the system Paste action",
                    )))
                    return@postDelayed
                }
                val pasted = pasteNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                pasteNode.recycle()
                if (pasted) {
                    handler.postDelayed({ clearOwnedClipboard(clipboard, text) }, 350L)
                    callback(Result.success(Unit))
                } else {
                    callback(Result.failure(IllegalStateException(
                        "The system Paste action rejected the click",
                    )))
                }
            }, 300L)
        }
    }

    private fun findPasteActionNode(): AccessibilityNodeInfo? {
        val systemPasteLabel = resources.getIdentifier("paste", "string", "android")
            .takeIf { it != 0 }
            ?.let(::getString)
        val labels = listOfNotNull(systemPasteLabel, "粘贴", "Paste")
        for (window in windows.asReversed()) {
            val root = window.root ?: continue
            val match = findNodeByLabels(root, labels)
            if (match != null) return match
        }
        return null
    }

    private fun findNodeByLabels(
        node: AccessibilityNodeInfo,
        labels: List<String>,
    ): AccessibilityNodeInfo? {
        val candidates = listOf(node.text?.toString(), node.contentDescription?.toString())
            .filterNotNull()
        if (candidates.any { candidate ->
                labels.any { label ->
                    candidate.equals(label, ignoreCase = true) ||
                        candidate.startsWith(label, ignoreCase = true)
                }
            }
        ) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNodeByLabels(child, labels)
            if (match != null) {
                node.recycle()
                return match
            }
        }
        node.recycle()
        return null
    }

    private fun clearOwnedClipboard(clipboard: ClipboardManager, expectedText: String) {
        val currentText = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }
            .getOrNull()
        if (currentText == expectedText) clipboard.clearPrimaryClip()
    }

    private fun findInputNode(x: Float?, y: Float?): AccessibilityNodeInfo? {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.isEditable == true) return focused
        focused?.recycle()
        if (x == null || y == null) return null
        return findDeepestEditable(rootInActiveWindow, x.toInt(), y.toInt())
    }

    private fun findDeepestEditable(
        node: AccessibilityNodeInfo?,
        x: Int,
        y: Int,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) {
            node.recycle()
            return null
        }
        for (index in 0 until node.childCount) {
            val match = findDeepestEditable(node.getChild(index), x, y)
            if (match != null) {
                node.recycle()
                return match
            }
        }
        return if (node.isEditable) node else {
            node.recycle()
            null
        }
    }

    private fun performKey(keyName: String, callback: (Result<Unit>) -> Unit) {
        when (keyName.lowercase().replace(" ", "")) {
            "enter" -> {
                val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                val success = node?.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
                ) == true
                node?.recycle()
                if (success) callback(Result.success(Unit))
                else callback(Result.failure(UnsupportedOperationException("Enter is not supported by the focused input")))
            }
            "shift+enter" -> insertAtSelection("\n", callback)
            "tab" -> focusNextInput(callback)
            "backspace" -> deleteBeforeSelection(callback)
            "arrowleft", "left" -> performCursorMove("left", 1, callback)
            "arrowright", "right" -> performCursorMove("right", 1, callback)
            "arrowup", "up" -> performVerticalCursorMove(up = true, callback)
            "arrowdown", "down" -> performVerticalCursorMove(up = false, callback)
            "home" -> globalAction(GLOBAL_ACTION_HOME, callback)
            "end" -> setSelectionBoundary(toEnd = true, callback)
            "control+a", "ctrl+a" -> selectAll(callback)
            "control+c", "ctrl+c" -> performFocusedNodeAction(
                AccessibilityNodeInfo.ACTION_COPY,
                callback,
            )
            "control+x", "ctrl+x" -> performFocusedNodeAction(
                AccessibilityNodeInfo.ACTION_CUT,
                callback,
            )
            "control+v", "ctrl+v" -> performFocusedNodeAction(
                AccessibilityNodeInfo.ACTION_PASTE,
                callback,
            )
            "escape", "back" -> globalAction(GLOBAL_ACTION_BACK, callback)
            else -> {
                if (keyName.length == 1) insertAtSelection(keyName, callback)
                else callback(Result.failure(UnsupportedOperationException("Unsupported key: $keyName")))
            }
        }
    }

    private fun performCursorMove(
        direction: String,
        times: Int,
        callback: (Result<Unit>) -> Unit,
    ) {
        if (direction != "left" && direction != "right") {
            callback(Result.failure(IllegalArgumentException("Unknown cursor direction: $direction")))
            return
        }
        val node = focusedEditableNode()
        if (node == null) {
            callback(Result.failure(IllegalStateException("No editable input is focused")))
            return
        }
        val length = node.text?.length ?: 0
        val selectionStart = node.textSelectionStart.takeIf { it >= 0 } ?: length
        val selectionEnd = node.textSelectionEnd.takeIf { it >= 0 } ?: selectionStart
        val current = if (direction == "left") {
            minOf(selectionStart, selectionEnd)
        } else {
            maxOf(selectionStart, selectionEnd)
        }
        val delta = times.coerceAtLeast(1) * if (direction == "left") -1 else 1
        val next = (current + delta).coerceIn(0, length)
        val success = setSelection(node, next, next)
        node.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Cursor movement is not supported by the focused input")))
    }

    private fun performVerticalCursorMove(up: Boolean, callback: (Result<Unit>) -> Unit) {
        val node = focusedEditableNode()
        if (node == null) {
            callback(Result.failure(IllegalStateException("No editable input is focused")))
            return
        }
        val text = node.text?.toString().orEmpty()
        val position = node.textSelectionEnd.takeIf { it >= 0 } ?: text.length
        val lineStart = if (position == 0) 0 else text.lastIndexOf('\n', position - 1) + 1
        val column = position - lineStart
        val next = if (up) {
            if (lineStart == 0) {
                0
            } else {
                val previousEnd = lineStart - 1
                val previousStart = if (previousEnd == 0) {
                    0
                } else {
                    text.lastIndexOf('\n', previousEnd - 1) + 1
                }
                previousStart + minOf(column, previousEnd - previousStart)
            }
        } else {
            val currentEnd = text.indexOf('\n', position)
            if (currentEnd < 0) {
                text.length
            } else {
                val nextStart = currentEnd + 1
                val nextEnd = text.indexOf('\n', nextStart).takeIf { it >= 0 } ?: text.length
                nextStart + minOf(column, nextEnd - nextStart)
            }
        }
        val success = setSelection(node, next, next)
        node.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Vertical cursor movement is not supported by the focused input")))
    }

    private fun isKeyboardVisible(): Boolean =
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    private fun setSelectionBoundary(toEnd: Boolean, callback: (Result<Unit>) -> Unit) {
        val node = focusedEditableNode()
        if (node == null) {
            callback(Result.failure(IllegalStateException("No editable input is focused")))
            return
        }
        val position = if (toEnd) node.text?.length ?: 0 else 0
        val success = setSelection(node, position, position)
        node.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Selection is not supported by the focused input")))
    }

    private fun selectAll(callback: (Result<Unit>) -> Unit) {
        val node = focusedEditableNode()
        if (node == null) {
            callback(Result.failure(IllegalStateException("No editable input is focused")))
            return
        }
        val success = setSelection(node, 0, node.text?.length ?: 0)
        node.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Select all is not supported by the focused input")))
    }

    private fun setSelection(node: AccessibilityNodeInfo, start: Int, end: Int): Boolean {
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    private fun insertAtSelection(text: String, callback: (Result<Unit>) -> Unit) {
        updateFocusedText(callback) { value, start, end ->
            value.replaceRange(minOf(start, end), maxOf(start, end), text)
        }
    }

    private fun deleteBeforeSelection(callback: (Result<Unit>) -> Unit) {
        updateFocusedText(callback) { value, start, end ->
            val from = if (start == end) (start - 1).coerceAtLeast(0) else minOf(start, end)
            value.removeRange(from, maxOf(start, end))
        }
    }

    private fun updateFocusedText(
        callback: (Result<Unit>) -> Unit,
        transform: (String, Int, Int) -> String,
    ) {
        val node = focusedEditableNode()
        if (node == null) {
            callback(Result.failure(IllegalStateException("No editable input is focused")))
            return
        }
        val current = node.text?.toString().orEmpty()
        val start = node.textSelectionStart.takeIf { it >= 0 } ?: current.length
        val end = node.textSelectionEnd.takeIf { it >= 0 } ?: start
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                transform(current, start, end),
            )
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Text editing is not supported by the focused input")))
    }

    private fun focusNextInput(callback: (Result<Unit>) -> Unit) {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val next = focused?.focusSearch(View.FOCUS_FORWARD)
        val success = next?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) == true
        next?.recycle()
        focused?.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Tab is not supported in the current window")))
    }

    private fun performFocusedNodeAction(action: Int, callback: (Result<Unit>) -> Unit) {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val success = node?.performAction(action) == true
        node?.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Focused input rejected action $action")))
    }

    private fun focusedEditableNode(): AccessibilityNodeInfo? {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (node.isEditable) return node
        node.recycle()
        return null
    }

    private fun launch(uri: String, callback: (Result<Unit>) -> Unit) {
        runCatching {
            val intent = when {
                uri.contains("://") -> Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                uri.contains("/") -> {
                    val component = ComponentName.unflattenFromString(uri)
                        ?: throw IllegalArgumentException("Invalid Android component: $uri")
                    Intent().setComponent(component)
                }
                else -> {
                    val packageName = packageManager.getLaunchIntentForPackage(uri)?.let { uri }
                        ?: LaunchableAppResolver.resolve(
                            uri,
                            packageManager.queryIntentActivities(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                                0,
                            ).map { app ->
                                LaunchableApp(
                                    app.loadLabel(packageManager)?.toString().orEmpty(),
                                    app.activityInfo.packageName,
                                )
                            },
                        )
                        ?: throw IllegalArgumentException("No launchable app found for package or name: $uri")
                    packageManager.getLaunchIntentForPackage(packageName)
                        ?: throw IllegalArgumentException("No launchable app found for package: $packageName")
                }
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }.fold(
            onSuccess = { callback(Result.success(Unit)) },
            onFailure = { callback(Result.failure(it)) },
        )
    }

    private fun openAppDetails(packageOrName: String, callback: (Result<Unit>) -> Unit) {
        runCatching {
            val packageName = packageManager.getLaunchIntentForPackage(packageOrName)?.let {
                packageOrName
            } ?: LaunchableAppResolver.resolve(
                packageOrName,
                packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0,
                ).map { app ->
                    LaunchableApp(
                        app.loadLabel(packageManager)?.toString().orEmpty(),
                        app.activityInfo.packageName,
                    )
                },
            ) ?: throw IllegalArgumentException(
                "No installed launcher app found for package or name: $packageOrName",
            )
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.fold(
            onSuccess = { callback(Result.success(Unit)) },
            onFailure = { callback(Result.failure(it)) },
        )
    }

    private fun globalAction(action: Int, callback: (Result<Unit>) -> Unit) {
        if (performGlobalAction(action)) callback(Result.success(Unit))
        else callback(Result.failure(IllegalStateException("Global action was rejected: $action")))
    }

    private fun <T> isCancelled(sessionId: String, callback: (Result<T>) -> Unit): Boolean {
        if (!cancelledSessions.contains(sessionId)) return false
        callback(Result.failure(SessionCancelledException()))
        return true
    }

    data class CapturePayload(
        val dataUrl: String,
        val frameId: String,
        val capturedAt: Long,
        val rotation: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val imageWidth: Int,
        val imageHeight: Int,
    )

    class SessionCancelledException : IllegalStateException("Task session was cancelled")
    class StaleFrameException : IllegalStateException(
        "The screen changed after it was captured; capture a fresh frame before acting",
    )

    companion object {
        private val screenshotSaveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        // Two 60 Hz frames are enough for WindowManager to retire accessibility
        // overlay surfaces while keeping the running effect visually continuous.
        private const val OVERLAY_SETTLE_MS = 32L
        private const val CANCELLED_SESSION_RETENTION_MS = 5 * 60 * 1000L
        private const val CLIPBOARD_LABEL = "Tapilot text input"
        private var instance = WeakReference<MidsceneAccessibilityService>(null)
        private val connectionListeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

        fun current(): MidsceneAccessibilityService? = instance.get()?.takeIf { service ->
            // A service object can outlive its system connection on some ROMs.
            // Android returns null here when the accessibility connection is gone.
            runCatching { service.serviceInfo != null }.getOrDefault(false)
        }

        fun addConnectionListener(listener: (Boolean) -> Unit) {
            connectionListeners.add(listener)
            listener(current() != null)
        }

        fun removeConnectionListener(listener: (Boolean) -> Unit) {
            connectionListeners.remove(listener)
        }

        private fun notifyConnectionChanged(connected: Boolean) {
            connectionListeners.forEach { listener -> listener(connected) }
        }
    }
}
