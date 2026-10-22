package lifeos.md.tapilot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Owns focused text editing and its input-connection/clipboard fallbacks.
 * Visual target selection and managed gesture dispatch stay with the device service.
 */
@Suppress("DEPRECATION")
internal class TextInputController(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val cancelled: (String) -> Boolean,
    private val gesture: (Float, Float, Float, Float, Long, (Result<Unit>) -> Unit) -> Unit,
    private val globalAction: (Int, (Result<Unit>) -> Unit) -> Unit,
) {
    private var lastTapPoint: NativeAction.Point? = null
    private var lastTapAtMillis = 0L

    fun rememberTap(x: Float, y: Float) {
        lastTapPoint = NativeAction.Point(x, y)
        lastTapAtMillis = System.currentTimeMillis()
    }

    private fun isCancelled(sessionId: String, callback: (Result<Unit>) -> Unit): Boolean =
        cancelled(sessionId).also { if (it) callback(Result.failure(MidsceneAccessibilityService.SessionCancelledException())) }

    fun inputText(
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
                                service.performGlobalAction(GLOBAL_ACTION_BACK)
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasActiveAccessibilityInputConnection(): Boolean =
        service.inputMethod?.currentInputStarted == true && service.inputMethod?.currentInputConnection != null

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun inputViaAccessibilityInputConnectionApi33(
        action: NativeAction.Input,
        callback: (Result<Unit>) -> Unit,
    ) {
        val accessibilityInputMethod = service.inputMethod
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
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
        val systemPasteLabel = android.R.string.paste
            .takeIf { it != 0 }
            ?.let(service::getString)
        val labels = listOfNotNull(systemPasteLabel, "粘贴", "Paste")
        for (window in service.windows.asReversed()) {
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
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.isEditable == true) return focused
        focused?.recycle()
        if (x == null || y == null) return null
        return findDeepestEditable(service.rootInActiveWindow, x.toInt(), y.toInt())
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

    fun performKey(keyName: String, callback: (Result<Unit>) -> Unit) {
        when (keyName.lowercase().replace(" ", "")) {
            "enter" -> {
                val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
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

    fun performCursorMove(
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
        service.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

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
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val next = focused?.focusSearch(View.FOCUS_FORWARD)
        val success = next?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) == true
        next?.recycle()
        focused?.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Tab is not supported in the current window")))
    }

    private fun performFocusedNodeAction(action: Int, callback: (Result<Unit>) -> Unit) {
        val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val success = node?.performAction(action) == true
        node?.recycle()
        if (success) callback(Result.success(Unit))
        else callback(Result.failure(UnsupportedOperationException("Focused input rejected action $action")))
    }

    private fun focusedEditableNode(): AccessibilityNodeInfo? {
        val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (node.isEditable) return node
        node.recycle()
        return null
    }

    companion object { private const val CLIPBOARD_LABEL = "Tapilot text input" }
}
