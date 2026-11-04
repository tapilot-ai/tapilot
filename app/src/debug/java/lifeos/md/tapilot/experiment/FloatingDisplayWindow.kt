package lifeos.md.tapilot.experiment

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Path
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import android.widget.TextView

/** A real captured display preview. All input is explicitly addressed to the secondary display. */
@SuppressLint("ClickableViewAccessibility")
internal class FloatingDisplayWindow(
    private val host: android.content.Context,
    private val probe: BackgroundDisplayProbe,
    private val zh: Boolean,
    private val canInteract: () -> Boolean = { true },
    private val stop: () -> Unit,
) : AutoCloseable {
    private val manager = host.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var preview: ImageView? = null
    private var lastStatus: String? = null
    private var path: Path? = null
    private var downAt = 0L
    private var samples = 0
    private fun text(chinese: String, english: String) = if (zh) chinese else english
    private fun dp(value: Int) = (value * host.resources.displayMetrics.density).toInt()

    fun show(atBottom: Boolean = false, maxWidthDp: Int = 240) {
        check(probe.active) { "Start the experiment first" }
        check(Settings.canDrawOverlays(host)) { "Allow display over other apps first" }
        if (root != null) return
        val bounds = manager.currentWindowMetrics.bounds
        val width = minOf(dp(maxWidthDp), bounds.width() * 2 / 3, bounds.height() / 2 * probe.width / probe.height)
        val params = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.LEFT; x = dp(12); y = dp(80) }
        val panel = FrameLayout(host).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(12).toFloat()
            }
            clipToOutline = true
            elevation = dp(4).toFloat()
        }
        val image = ImageView(host).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = text("虚拟屏实时画面，可点击或滑动", "Live virtual display; tap or swipe")
            setOnTouchListener { view, event -> onPreviewTouch(view, event) }
        }
        val previewHeight = width * probe.height / probe.width
        if (atBottom) {
            params.x = (bounds.width() - width - dp(12)).coerceAtLeast(0)
            params.y = (bounds.height() - previewHeight - dp(80)).coerceAtLeast(0)
        }
        panel.addView(image, FrameLayout.LayoutParams(width, previewHeight))
        // Small visual controls with generous touch targets, overlaid on the capture.
        val handle = FrameLayout(host).apply {
            contentDescription = text("拖动虚拟屏", "Move virtual display")
            isFocusable = true
        }
        handle.addView(View(host).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(180, 255, 255, 255))
                cornerRadius = dp(2).toFloat()
            }
            elevation = dp(2).toFloat()
        }, FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER))
        panel.addView(handle, FrameLayout.LayoutParams(dp(80), dp(48), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val close = FrameLayout(host).apply {
            contentDescription = text("关闭并释放虚拟屏", "Close and release virtual display")
            isFocusable = true
            setOnClickListener { stop() }
        }
        close.addView(TextView(host).apply {
            text = "×"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(140, 20, 24, 28))
            }
        }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        panel.addView(close, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.RIGHT))
        var initialX = 0; var initialY = 0; var downX = 0f; var downY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { initialX = params.x; initialY = params.y; downX = event.rawX; downY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    val current = manager.currentWindowMetrics.bounds
                    params.x = (initialX + event.rawX - downX).toInt().coerceIn(0, (current.width() - panel.width).coerceAtLeast(0))
                    params.y = (initialY + event.rawY - downY).toInt().coerceIn(0, (current.height() - panel.height).coerceAtLeast(0))
                    manager.updateViewLayout(panel, params)
                }
            }
            true
        }
        manager.addView(panel, params)
        root = panel; preview = image
        probe.livePreview = true
        render()
        probe.refresh()
    }

    private fun onPreviewTouch(view: View, event: MotionEvent): Boolean {
        if (!canInteract() || event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount != 1 || !probe.active) {
            path = null
            return true
        }
        val point = PreviewCoordinates.map(event.x, event.y, view.width, view.height,
            probe.width, probe.height)
        if (point == null) { path = null; return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                path = Path().apply { moveTo(point.first, point.second) }
                downAt = event.eventTime; samples = 1
            }
            MotionEvent.ACTION_MOVE -> {
                if (++samples > 256) path = null
                path?.lineTo(point.first, point.second)
            }
            MotionEvent.ACTION_UP -> {
                val completed = path
                path = null
                if (completed != null) {
                    completed.lineTo(point.first, point.second)
                    runCatching { probe.dispatchPath(completed, (event.eventTime - downAt).coerceAtLeast(80)) }
                        .onFailure { Toast.makeText(host, it.message, Toast.LENGTH_SHORT).show() }
                    view.performClick()
                }
            }
        }
        return true
    }

    fun render() {
        if (!probe.active) { close(); return }
        preview?.setImageBitmap(probe.bitmap)
        if (lastStatus != probe.status && probe.status.contains("gesture cancelled", ignoreCase = true)) {
            Toast.makeText(host, text("设备取消了虚拟屏操作", "Device cancelled virtual display input"), Toast.LENGTH_SHORT).show()
        }
        lastStatus = probe.status
    }

    override fun close() {
        probe.livePreview = false
        path = null
        preview?.setImageDrawable(null)
        root?.let { manager.removeViewImmediate(it) }
        root = null; preview = null; lastStatus = null
    }
}
