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
    var onHold: (Boolean) -> Unit = {}
    var onMove: (Boolean) -> Unit = {}
    var onRelease: () -> Unit = {}
    var onCancel: () -> Unit = {}
    var voiceMode = false
    var holdEnabled = true
    private var startX = 0f
    private var startY = 0f
    private var holding = false
    private var pending = false
    private val begin = Runnable {
        pending = false
        holding = true
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onHold(voiceMode)
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, startX, startY, 0)
        try { super.onTouchEvent(cancel) } finally { cancel.recycle() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!holdEnabled) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                pending = true
                if (voiceMode) {
                    begin.run()
                    return true
                }
                postDelayed(begin, 480L)
            }
            MotionEvent.ACTION_MOVE -> {
                if (holding) {
                    onMove(startY - event.rawY > 72f * resources.displayMetrics.density)
                    return true
                }
                if (pending && hypot(event.rawX - startX, event.rawY - startY) > ViewConfiguration.get(context).scaledTouchSlop) {
                    removeCallbacks(begin)
                    pending = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(begin)
                pending = false
                if (holding) {
                    holding = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) onRelease() else onCancel()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(begin)
        if (holding) onCancel()
        holding = false
        pending = false
        super.onDetachedFromWindow()
    }
}
