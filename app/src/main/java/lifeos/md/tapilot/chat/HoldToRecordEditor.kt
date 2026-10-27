package lifeos.md.tapilot.chat

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.math.hypot

/** Owns one uninterrupted gesture; moving up only arms cancellation until ACTION_UP. */
class HoldToRecordEditor(context: Context) : EditText(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        outAttrs.imeOptions = outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()
        return connection
    }
    var onPrepare: () -> Unit = {}
    var onDiscardPreparation: () -> Unit = {}
    var onHold: (Boolean) -> Unit = {}
    var onMove: (RecordingGesture) -> Unit = {}
    var onRelease: () -> Unit = {}
    var onCancel: () -> Unit = {}
    var voiceMode = false
    var holdEnabled = true
    private var startX = 0f
    private var startY = 0f
    private var holding = false
    private var pending = false
    var appliedDraftEpoch = -1
    var typingHint = ""
    var speechHint = "Tap to type · hold to talk"
    fun refreshHint() { hint = if (canHoldToSpeak(holdEnabled, hasFocus(), text)) speechHint else typingHint }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused && !holding) { removeCallbacks(begin); if (pending) onDiscardPreparation(); pending = false }
        refreshHint()
    }

    private val begin = Runnable {
        if (!pending || !canHoldToSpeak(holdEnabled, hasFocus(), text)) return@Runnable
        pending = false
        holding = true
        parent?.requestDisallowInterceptTouchEvent(true)
        clearFocus()
        context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(windowToken, 0)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onHold(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!pending && !holding && !canHoldToSpeak(holdEnabled, hasFocus(), text)) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                pending = true
                onPrepare()
                postDelayed(begin, 280L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (holding) {
                    onMove(recordingGesture(event.rawX, event.rawY, rootView.width.toFloat(),
                        rootView.height.toFloat(), resources.displayMetrics.density))
                } else if (pending && hypot(event.rawX - startX, event.rawY - startY) > ViewConfiguration.get(context).scaledTouchSlop) {
                    removeCallbacks(begin)
                    onDiscardPreparation()
                    pending = false
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(begin)
                val tap = pending
                if (pending) onDiscardPreparation()
                pending = false
                if (holding) {
                    holding = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) onRelease() else onCancel()
                } else if (tap && event.actionMasked == MotionEvent.ACTION_UP && !voiceMode) {
                    performClick()
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (!voiceMode) {
            requestFocus()
            context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) cancelGesture()
        super.onWindowFocusChanged(hasWindowFocus)
    }

    private fun cancelGesture() {
        removeCallbacks(begin)
        if (pending) onDiscardPreparation()
        if (holding) onCancel()
        holding = false
        pending = false
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }
}
