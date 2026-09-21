package lifeos.md.tapilot.accessibility

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SweepGradient
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import lifeos.md.tapilot.TaskEffect
import kotlin.math.PI
import kotlin.math.sin

class ExecutionEdgeEffectView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shaderMatrix = Matrix()
    private var edgeShader: SweepGradient? = null
    private var windowInsets: WindowInsets? = null
    private var physicalCornerRadii = EdgeEffectCornerRadii(0f, 0f, 0f, 0f)
    private var geometrySettings = EdgeEffectPreferences.get(context)
    private var phase = 0f
    private var effect = TaskEffect.NONE
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setEffect(value: TaskEffect) {
        effect = value
        rebuildEdgeShader()
        if (value == TaskEffect.NONE) {
            animator.cancel()
            visibility = GONE
            return
        }
        visibility = VISIBLE
        if (ValueAnimator.areAnimatorsEnabled()) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
            phase = 0.25f
            invalidate()
        }
    }

    internal fun setGeometrySettings(value: EdgeEffectGeometrySettings) {
        geometrySettings = value
        rebuildEdgePath()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        windowInsets = insets
        rebuildEdgePath()
        return super.onApplyWindowInsets(insets)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        rebuildEdgeShader()
        rebuildEdgePath()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (effect == TaskEffect.NONE || width == 0 || height == 0) return

        val shader = edgeShader ?: return
        shaderMatrix.setRotate(phase * 360f, width / 2f, height / 2f)
        shader.setLocalMatrix(shaderMatrix)

        paint.shader = shader
        drawMistLayer(canvas, widthDp = 11f, alpha = 30, amplitudeDp = 5.5f, frequency = 2, phaseOffset = 0f)
        drawMistLayer(canvas, widthDp = 7f, alpha = 52, amplitudeDp = 3.5f, frequency = 3, phaseOffset = 0.31f)
        drawMistLayer(canvas, widthDp = 3.5f, alpha = 78, amplitudeDp = 2f, frequency = 4, phaseOffset = 0.67f)
        paint.shader = null
    }

    private fun drawMistLayer(
        canvas: Canvas,
        widthDp: Float,
        alpha: Int,
        amplitudeDp: Float,
        frequency: Int,
        phaseOffset: Float,
    ) {
        val strokeWidth = widthDp * density
        val inset = geometrySettings.insetDp * density + strokeWidth / 2f
        val bounds = edgeEffectBounds(width, height, inset)
        val radii = physicalCornerRadii.insetBy(inset)
        val path = mistPath(
            bounds = bounds,
            radii = radii,
            amplitude = amplitudeDp * density,
            frequency = frequency,
            wavePhase = phase + phaseOffset,
        )
        paint.alpha = alpha
        paint.strokeWidth = strokeWidth
        canvas.drawPath(path, paint)
    }

    private fun mistPath(
        bounds: EdgeEffectBounds,
        radii: EdgeEffectCornerRadii,
        amplitude: Float,
        frequency: Int,
        wavePhase: Float,
    ): Path {
        val path = Path()
        path.moveTo(bounds.left + radii.topLeft, bounds.top)
        addWavyHorizontal(path, bounds.left + radii.topLeft, bounds.right - radii.topRight, bounds.top, amplitude, frequency, wavePhase, 1f)
        path.quadTo(bounds.right, bounds.top, bounds.right, bounds.top + radii.topRight)
        addWavyVertical(path, bounds.top + radii.topRight, bounds.bottom - radii.bottomRight, bounds.right, amplitude, frequency, wavePhase + 0.17f, -1f)
        path.quadTo(bounds.right, bounds.bottom, bounds.right - radii.bottomRight, bounds.bottom)
        addWavyHorizontal(path, bounds.right - radii.bottomRight, bounds.left + radii.bottomLeft, bounds.bottom, amplitude, frequency, wavePhase + 0.39f, -1f)
        path.quadTo(bounds.left, bounds.bottom, bounds.left, bounds.bottom - radii.bottomLeft)
        addWavyVertical(path, bounds.bottom - radii.bottomLeft, bounds.top + radii.topLeft, bounds.left, amplitude, frequency, wavePhase + 0.71f, 1f)
        path.quadTo(bounds.left, bounds.top, bounds.left + radii.topLeft, bounds.top)
        path.close()
        return path
    }

    private fun addWavyHorizontal(
        path: Path,
        start: Float,
        end: Float,
        base: Float,
        amplitude: Float,
        frequency: Int,
        wavePhase: Float,
        inwardDirection: Float,
    ) {
        for (step in 1..WAVE_SEGMENTS) {
            val progress = step.toFloat() / WAVE_SEGMENTS
            val coordinate = start + (end - start) * progress
            path.lineTo(coordinate, base + inwardDirection * waveOffset(progress, amplitude, frequency, wavePhase))
        }
    }

    private fun addWavyVertical(
        path: Path,
        start: Float,
        end: Float,
        base: Float,
        amplitude: Float,
        frequency: Int,
        wavePhase: Float,
        inwardDirection: Float,
    ) {
        for (step in 1..WAVE_SEGMENTS) {
            val progress = step.toFloat() / WAVE_SEGMENTS
            val coordinate = start + (end - start) * progress
            path.lineTo(base + inwardDirection * waveOffset(progress, amplitude, frequency, wavePhase), coordinate)
        }
    }

    private fun waveOffset(
        progress: Float,
        amplitude: Float,
        frequency: Int,
        wavePhase: Float,
    ): Float {
        val envelope = sin(PI * progress).toFloat()
        val wave = sin(2.0 * PI * (frequency * progress + wavePhase)).toFloat()
        return amplitude * envelope * (0.55f + 0.45f * wave)
    }

    private fun rebuildEdgeShader() {
        edgeShader = if (effect == TaskEffect.NONE || width == 0 || height == 0) {
            null
        } else {
            SweepGradient(width / 2f, height / 2f, colorsFor(effect), null)
        }
    }

    private fun rebuildEdgePath() {
        if (width == 0 || height == 0) return
        val insets = windowInsets ?: rootWindowInsets
        // Some OEM accessibility overlays omit RoundedCorner. Try device resources
        // before using an estimate. Never override valid physical radii with a minimum.
        // No size-based estimate here: a genuine square display must stay square.
        // OEM resources are only used when they explicitly declare a radius.
        val fallbackRadius = systemCornerRadius("rounded_corner_radius") ?: 0f
        val topFallback = systemCornerRadius("rounded_corner_radius_top") ?: fallbackRadius
        val bottomFallback = systemCornerRadius("rounded_corner_radius_bottom") ?: fallbackRadius
        val automaticRadii = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                edgeEffectCornerRadii(
                    insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: topFallback.toInt(),
                    insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: topFallback.toInt(),
                    insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: bottomFallback.toInt(),
                    insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: bottomFallback.toInt(),
                    fallbackRadius,
                )
        } else {
            edgeEffectCornerRadii(topFallback.toInt(), topFallback.toInt(), bottomFallback.toInt(), bottomFallback.toInt(), fallbackRadius)
        }
        physicalCornerRadii = edgeEffectCornerRadiiForMode(
            geometrySettings.mode,
            automaticRadii,
            geometrySettings.cornerRadiusDp * density,
        )
        invalidate()
    }

    private fun systemCornerRadius(name: String): Float? = runCatching {
        // Optional OEM framework resources, not a hidden API; missing values are normal.
        val id = resources.getIdentifier(name, "dimen", "android")
        if (id == 0) null else resources.getDimension(id).takeIf { it > 0f }
    }.getOrNull()

    private fun colorsFor(effect: TaskEffect): IntArray = when (effect) {
        TaskEffect.ACTIVE -> ACTIVE_COLORS
        TaskEffect.SUCCESS -> SUCCESS_COLORS
        TaskEffect.FAILURE -> FAILURE_COLORS
        TaskEffect.STOPPED -> STOPPED_COLORS
        TaskEffect.NONE -> error("No colors for an inactive effect")
    }

    companion object {
        private const val WAVE_SEGMENTS = 24
        private val ACTIVE_COLORS = intArrayOf(
            Color.rgb(54, 118, 255),
            Color.rgb(127, 77, 255),
            Color.rgb(45, 212, 191),
            Color.rgb(54, 118, 255),
        )
        private val SUCCESS_COLORS = intArrayOf(
            Color.rgb(34, 197, 94),
            Color.rgb(74, 222, 128),
            Color.rgb(16, 185, 129),
            Color.rgb(34, 197, 94),
        )
        private val FAILURE_COLORS = intArrayOf(
            Color.rgb(239, 68, 68),
            Color.rgb(251, 113, 133),
            Color.rgb(245, 158, 11),
            Color.rgb(239, 68, 68),
        )
        private val STOPPED_COLORS = intArrayOf(
            Color.rgb(100, 116, 139),
            Color.rgb(148, 163, 184),
            Color.rgb(71, 85, 105),
            Color.rgb(100, 116, 139),
        )
    }
}
