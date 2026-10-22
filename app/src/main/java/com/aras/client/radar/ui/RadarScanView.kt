package com.aras.client.radar.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Animated radar for discovery results. Blips are added only after a candidate test returns. */
class RadarScanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private data class Blip(val angle: Float, val radius: Float, val color: Int, val bornAt: Long)
    private val blips = mutableListOf<Blip>()
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var sweepDegrees = 0f
    private var animator: ValueAnimator? = null
    private var shader: SweepGradient? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(85, 0, 255, 156)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1f)
        color = Color.argb(38, 0, 255, 156)
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        radius = (min(w, h) / 2f - dp(12f)).coerceAtLeast(0f)
        shader = SweepGradient(
            centerX, centerY,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(155, 0, 255, 156), Color.argb(155, 0, 255, 156)),
            floatArrayOf(0f, 0.78f, 0.995f, 1f)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return
        for (ring in 1..4) canvas.drawCircle(centerX, centerY, radius * ring / 4f, ringPaint)
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, axisPaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, axisPaint)
        val save = canvas.save()
        canvas.rotate(sweepDegrees, centerX, centerY)
        sweepPaint.shader = shader
        canvas.drawCircle(centerX, centerY, radius, sweepPaint)
        sweepPaint.shader = null
        canvas.restoreToCount(save)

        val now = System.currentTimeMillis()
        val iterator = blips.iterator()
        while (iterator.hasNext()) {
            val blip = iterator.next()
            val age = now - blip.bornAt
            if (age >= BLIP_LIFETIME_MS) {
                iterator.remove()
                continue
            }
            val alpha = ((1f - age.toFloat() / BLIP_LIFETIME_MS) * 255).toInt().coerceIn(0, 255)
            val x = centerX + radius * blip.radius * cos(blip.angle)
            val y = centerY + radius * blip.radius * sin(blip.angle)
            blipPaint.color = withAlpha(blip.color, alpha / 3)
            canvas.drawCircle(x, y, dp(11f), blipPaint)
            blipPaint.color = withAlpha(blip.color, alpha)
            canvas.drawCircle(x, y, dp(3.5f), blipPaint)
        }
        canvas.drawCircle(centerX, centerY, dp(4f), centerPaint)
        if (blips.isNotEmpty()) postInvalidateOnAnimation()
    }

    fun startScan() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sweepDegrees = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopScan() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    fun clearBlips() {
        blips.clear()
        invalidate()
    }

    /** Add a candidate at a radius derived from its measured latency; lower latency is nearer the center. */
    fun addTestedCandidate(pingMs: Long) {
        val safePing = pingMs.coerceAtLeast(0L)
        val fraction = (0.18f + (safePing.coerceAtMost(600L) / 600f) * 0.72f).coerceIn(0.18f, 0.9f)
        val angle = Math.toRadians((sweepDegrees + 15f).toDouble()).toFloat()
        blips.add(Blip(angle, fraction, colorForPing(safePing), System.currentTimeMillis()))
        if (blips.size > MAX_BLIPS) blips.removeAt(0)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopScan()
        super.onDetachedFromWindow()
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        private const val BLIP_LIFETIME_MS = 4_000L
        private const val MAX_BLIPS = 80
        private val GREEN = Color.parseColor("#00FF9C")
        private val YELLOW = Color.parseColor("#FFD60A")
        private val RED = Color.parseColor("#FF3B5C")

        fun colorForPing(pingMs: Long): Int = when {
            pingMs <= 150L -> GREEN
            pingMs <= 300L -> YELLOW
            else -> RED
        }
    }
}
