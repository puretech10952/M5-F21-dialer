package com.puretech.dialer

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Google-style "slide to answer" control for the incoming-call screen. A round
 * handle sits in the middle of a pill track: drag it RIGHT (toward the green
 * phone) to answer, or LEFT (toward the red end-call) to decline. Released short
 * of either end, the handle springs back to the centre. An optional alternative
 * to the round Answer/Decline buttons (see [Prefs.swipeToAnswer]).
 */
class SwipeToAnswerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var onAnswer: (() -> Unit)? = null
    var onDecline: (() -> Unit)? = null

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density

    private val green = 0xFF00C853.toInt()
    private val red = 0xFFFF3B30.toInt()
    private val neutral = 0xFFECEFF1.toInt()

    private val handleRadius = dp(30f)
    private val targetRadius = dp(26f)
    private val edgePad = dp(6f)
    private val iconHalf = dp(13f)
    private val targetIconHalf = dp(12f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = green }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = neutral }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x66FFFFFF
    }

    private val argb = ArgbEvaluator()
    private val callIcon = ContextCompat.getDrawable(context, R.drawable.ic_call)!!.mutate()
    private val endIcon = ContextCompat.getDrawable(context, R.drawable.ic_call_end)!!.mutate()
    private val handleIcon = ContextCompat.getDrawable(context, R.drawable.ic_call)!!.mutate()

    private val trackRect = RectF()
    private var restX = 0f
    private var leftX = 0f
    private var rightX = 0f
    private var handleX = 0f
    private var centerY = 0f

    private var dragging = false
    private var grabDx = 0f
    private var fired = false
    private var settleAnim: ValueAnimator? = null
    private var hintAnim: ValueAnimator? = null
    private var hintPhase = 0f

    init {
        callIcon.setTint(Color.WHITE)
        endIcon.setTint(Color.WHITE)
        startHint()
    }

    /** Recentre the handle and re-arm the control for the next incoming call. */
    fun reset() {
        settleAnim?.cancel()
        fired = false
        dragging = false
        handleX = restX
        invalidate()
        startHint()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(dp(280f).toInt(), widthMeasureSpec)
        val desiredH = (handleRadius * 2 + edgePad * 2).toInt()
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerY = h / 2f
        leftX = edgePad + handleRadius
        rightX = w - edgePad - handleRadius
        restX = w / 2f
        if (!dragging && settleAnim?.isRunning != true) handleX = restX
        trackRect.set(
            edgePad, centerY - handleRadius - edgePad / 2f,
            w - edgePad, centerY + handleRadius + edgePad / 2f
        )
    }

    override fun onDraw(canvas: Canvas) {
        val r = trackRect.height() / 2f
        canvas.drawRoundRect(trackRect, r, r, trackPaint)

        // End targets.
        drawCircleIcon(canvas, leftX, centerY, targetRadius, redPaint, endIcon, targetIconHalf)
        drawCircleIcon(canvas, rightX, centerY, targetRadius, greenPaint, callIcon, targetIconHalf)

        // Idle hint chevrons pointing outward from the centre.
        if (!dragging && !fired) {
            drawChevrons(canvas)
        }

        // Handle: colour blends toward the side it is being dragged.
        val frac = ((handleX - restX) / (rightX - restX)).coerceIn(-1f, 1f)
        handlePaint.color = when {
            frac > 0f -> argb.evaluate(frac, neutral, green) as Int
            frac < 0f -> argb.evaluate(-frac, neutral, red) as Int
            else -> neutral
        }
        // Icon tint flips to white once it has clearly committed to a direction.
        val iconColor = if (abs(frac) > 0.45f) Color.WHITE else 0xFF263238.toInt()
        handleIcon.setTint(iconColor)
        drawCircleIcon(canvas, handleX, centerY, handleRadius, handlePaint, handleIcon, iconHalf)
    }

    private fun drawCircleIcon(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        paint: Paint, icon: android.graphics.drawable.Drawable, half: Float
    ) {
        canvas.drawCircle(cx, cy, radius, paint)
        icon.setBounds(
            (cx - half).toInt(), (cy - half).toInt(),
            (cx + half).toInt(), (cy + half).toInt()
        )
        icon.draw(canvas)
    }

    private fun drawChevrons(canvas: Canvas) {
        // Two chevrons each side; opacity pulses outward to suggest the drag.
        val gap = dp(11f)
        val cw = dp(6f)
        val baseRight = handleX + handleRadius + dp(10f)
        val baseLeft = handleX - handleRadius - dp(10f)
        for (i in 0..1) {
            val a = (((hintPhase - i * 0.33f) % 1f + 1f) % 1f)
            val alpha = (170 * (1f - abs(a - 0.5f) * 2f)).toInt().coerceIn(0, 170)
            chevronPaint.alpha = alpha
            val rx = baseRight + i * gap
            canvas.drawPath(chevron(rx, centerY, cw, true), chevronPaint)
            val lx = baseLeft - i * gap
            canvas.drawPath(chevron(lx, centerY, cw, false), chevronPaint)
        }
    }

    private fun chevron(x: Float, y: Float, w: Float, pointRight: Boolean): Path {
        val dir = if (pointRight) 1f else -1f
        return Path().apply {
            moveTo(x - dir * w / 2f, y - w)
            lineTo(x + dir * w / 2f, y)
            lineTo(x - dir * w / 2f, y + w)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (fired) return false
                settleAnim?.cancel()
                stopHint()
                dragging = true
                grabDx = event.x - handleX
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                handleX = (event.x - grabDx).coerceIn(leftX, rightX)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                when {
                    handleX >= rightX - dp(4f) -> commit(true)
                    handleX <= leftX + dp(4f) -> commit(false)
                    else -> settleBack()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun commit(answer: Boolean) {
        if (fired) return
        fired = true
        stopHint()
        // Snap fully to the target, then fire.
        handleX = if (answer) rightX else leftX
        invalidate()
        if (answer) onAnswer?.invoke() else onDecline?.invoke()
    }

    private fun settleBack() {
        settleAnim?.cancel()
        val from = handleX
        settleAnim = ValueAnimator.ofFloat(from, restX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { handleX = it.animatedValue as Float; invalidate() }
            start()
        }
        startHint()
    }

    private fun startHint() {
        if (hintAnim?.isRunning == true) return
        hintAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { hintPhase = it.animatedValue as Float; if (!dragging) invalidate() }
            start()
        }
    }

    private fun stopHint() {
        hintAnim?.cancel()
        hintAnim = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopHint()
        settleAnim?.cancel()
    }
}
