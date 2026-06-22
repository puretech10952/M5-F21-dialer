package com.puretech.dialer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Vibrant "echo wave" effect for the incoming-call screen: several translucent
 * rings continuously expand outward from the centre (where the avatar sits) and
 * fade as they grow, like sonar pulses. Only animates while [start] is active.
 */
class PulseRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var baseColor = 0xFF1A73E8.toInt()
    private val waveCount = 4
    /** Fraction of the half-size where the avatar edge is (waves start here). */
    private val innerFraction = 0.34f
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun setRingColor(color: Int) {
        baseColor = color
        invalidate()
    }

    fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
        visibility = VISIBLE
    }

    fun stop() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onDraw(canvas: Canvas) {
        if (animator == null) return
        val cx = width / 2f
        val cy = height / 2f
        val half = min(width, height) / 2f
        val innerR = half * innerFraction
        for (i in 0 until waveCount) {
            // Each wave is offset in time so they ripple out one after another.
            val p = (phase + i.toFloat() / waveCount) % 1f
            val radius = innerR + (half - innerR) * p
            // Brightest just outside the avatar, fading to transparent at the edge.
            val alpha = ((1f - p) * 0.45f * 255).toInt().coerceIn(0, 255)
            paint.color = (baseColor and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}
