package lifeos.md.tapilot.experiment

import android.app.Activity
import android.app.Presentation
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import java.util.concurrent.Executors
import lifeos.md.tapilot.shell.ShellSession
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import lifeos.md.tapilot.TaskExecutionTracker
import lifeos.md.tapilot.wireless.DeviceControlLease

/** Debug-only experiment. Never routes production device commands or falls back to display 0. */
internal class BackgroundDisplayProbe(private val host: Activity, private val changed: () -> Unit) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var display: Display? = null
    private val inputWorker = Executors.newSingleThreadExecutor()
    private var reader: ImageReader? = null
    private var presentation: Presentation? = null
    private var target: Button? = null
    private var leased = false
    private var generation = 0
    var bitmap: Bitmap? = null; private set
    var frames = 0; private set
    var taps = 0; private set
    var status = "Off"; private set
    val displayId get() = display?.displayId
    val active get() = display != null
    var livePreview = false
    private var lastSnapshotAt = 0L
    var gesturePending = false; private set
    private var snapshotRequested = true

    fun start() {
        ShellSession.requireHelper()
        check(!active)
        check(!TaskExecutionTracker.isActive) { "Stop the current task before starting this experiment" }
        DeviceControlLease.acquire(OWNER)
        leased = true
        try {
            generation++
            val run = generation
            frames = 0; taps = 0; snapshotRequested = true
            val images = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)
            reader = images
            val capture = Runnable {
                if (run == generation) images.acquireLatestImage()?.use { image ->
                    frames++
                    if (snapshotRequested || livePreview) {
                        val plane = image.planes[0]
                        val paddedWidth = plane.rowStride / plane.pixelStride
                        val padded = Bitmap.createBitmap(paddedWidth, HEIGHT, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val next = Bitmap.createBitmap(padded, 0, 0, WIDTH, HEIGHT)
                        if (next !== padded) padded.recycle()
                        // The activity replaces its ImageView reference before the old bitmap is recycled.
                        val old = bitmap
                        bitmap = next; snapshotRequested = false; lastSnapshotAt = SystemClock.uptimeMillis()
                        changed()
                        old?.recycle()
                    }
                }
            }
            images.setOnImageAvailableListener({
                // Consume the latest frame after throttling, including the final static frame.
                if (run == generation && !handler.hasCallbacks(capture)) {
                    val delay = if (livePreview) (100 - (SystemClock.uptimeMillis() - lastSnapshotAt)).coerceAtLeast(0) else 0
                    handler.postDelayed(capture, delay)
                }
            }, handler)
            val id = ShellSession.requireHelper().create(images.surface, WIDTH, HEIGHT)
            check(id > 0) { "Refusing to target the primary display" }
            display = host.getSystemService(DisplayManager::class.java).getDisplay(id)
            if (display == null) {
                ShellSession.requireHelper().release(id)
                error("Shell display did not become available")
            }
            showFixture()
            status = "Display $displayId created; waiting for rendered pixels"
            changed()
        } catch (failure: Exception) { close(); throw failure }
    }

    private fun showFixture() {
        val panel = Presentation(host, checkNotNull(display))
        presentation = panel
        val layout = LinearLayout(panel.context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(224, 245, 255))
        }
        layout.addView(TextView(panel.context).apply {
            text = lifeos.md.tapilot.AppLanguage.wrap(host).getString(lifeos.md.tapilot.R.string.shell_fixture_title); textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
        })
        target = Button(panel.context).apply {
            setTextColor(Color.BLACK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            text = lifeos.md.tapilot.AppLanguage.wrap(host).getString(lifeos.md.tapilot.R.string.shell_fixture_count, 0)
            setOnClickListener { taps++; text = lifeos.md.tapilot.AppLanguage.wrap(host).getString(lifeos.md.tapilot.R.string.shell_fixture_count, taps); snapshotRequested = true; changed() }
        }
        layout.addView(target, LinearLayout.LayoutParams(360, 100))
        panel.setContentView(layout)
        panel.show()
    }

    fun tapFixture() {
        val id = checkNotNull(displayId) { "Start the experiment first" }
        check(id != Display.DEFAULT_DISPLAY)
        val button = checkNotNull(target) { "Test tap is only available on our fixture" }
        val point = IntArray(2); button.getLocationOnScreen(point)
        val path = Path().apply { moveTo(point[0] + button.width / 2f, point[1] + button.height / 2f) }
        dispatchPath(path, 80)
    }

    fun dispatchPath(path: Path, duration: Long, allowed: () -> Boolean = { true }, complete: (Result<Unit>) -> Unit = {}) {
        check(allowed()) { "Input owner changed" }
        val id = checkNotNull(displayId) { "Start the experiment first" }
        check(id != Display.DEFAULT_DISPLAY)
        check(!gesturePending) { "Wait for the previous gesture result" }
        val helper = ShellSession.requireHelper()
        val measure = PathMeasure(path, false)
        val points = if (measure.length == 0f) {
            val bounds = RectF(); path.computeBounds(bounds, true)
            floatArrayOf(bounds.left, bounds.top, bounds.left, bounds.top)
        } else FloatArray(64).also { out ->
            val point = FloatArray(2)
            for (i in 0 until 32) {
                check(measure.getPosTan(measure.length * i / 31, point, null))
                out[i * 2] = point[0]; out[i * 2 + 1] = point[1]
            }
        }
        val run = generation
        gesturePending = true
        status = "Waiting for shell input result"; changed()
        inputWorker.execute {
            val result = runCatching {
                check(allowed()) { "Input owner changed" }
                check(helper.gesture(id, points, duration.coerceIn(1, 5000)))
            }
            handler.post {
                if (run == generation) {
                    gesturePending = false; snapshotRequested = true
                    status = if (result.isSuccess) "Shell input completed on display $id; fixture taps observed: $taps"
                        else "Secondary-display input failed; no primary-screen fallback"
                    changed()
                    complete(result)
                } else complete(Result.failure(IllegalStateException("Display was released")))
            }
        }
    }

    fun launch(component: ComponentName) {
        val id = checkNotNull(displayId) { "Start the experiment first" }
        ShellSession.requireHelper().launch(id, component.flattenToString())
        presentation?.dismiss(); presentation = null; target = null
        snapshotRequested = true
        status = "Shell launch requested on display $id. Inspect the actual screen to verify."
        changed()
    }

    fun refresh() {
        check(active)
        snapshotRequested = true
        // A static surface may not produce another frame; retain the last actual frame.
        target?.invalidate()
        status = "Latest captured frame shown; waiting for a new frame"; changed()
    }

    override fun close() {
        generation++
        livePreview = false; gesturePending = false
        handler.removeCallbacksAndMessages(null)
        presentation?.dismiss(); presentation = null; target = null
        displayId?.let { id -> runCatching { ShellSession.requireHelper().release(id) } }; display = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close(); reader = null
        val old = bitmap; bitmap = null
        if (leased) DeviceControlLease.release(OWNER)
        leased = false
        status = "Off; experiment resources released"
        changed()
        old?.recycle()
    }

    fun dispose() { close(); inputWorker.shutdownNow() }

    companion object {
        private const val OWNER = "background-display-probe"
        const val WIDTH = 480
        const val HEIGHT = 800
    }
}
