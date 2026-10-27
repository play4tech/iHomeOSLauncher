package com.android.launcher3.settings

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * Authentic iOS 17 / iOS 18 system slider with smooth physics, tactile scaling,
 * layered drop shadows, and capsule track geometry.
 */
class IosSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSliderChangeListener {
        fun onProgressChanged(slider: IosSlider, progress: Float, fromUser: Boolean)
        fun onStartTrackingTouch(slider: IosSlider) {}
        fun onStopTrackingTouch(slider: IosSlider) {}
    }

    var min: Float = 0f
        set(value) {
            field = value
            if (progress < value) progress = value
            invalidate()
        }

    var max: Float = 100f
        set(value) {
            field = value
            if (progress > value) progress = value
            invalidate()
        }

    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(min, max)
            if (abs(field - clamped) > 1e-4f) {
                field = clamped
                invalidate()
            }
        }

    var steps: Int = 0 // 0 for continuous float, >0 for discrete steps

    var onSliderChangeListener: OnSliderChangeListener? = null

    private val density = resources.displayMetrics.density
    private val trackHeight = 5.5f * density
    private val baseThumbRadius = 13.5f * density // 27dp diameter
    private var thumbScale = 1.0f

    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = trackHeight
        color = Color.parseColor("#007AFF") // iOS System Blue
    }

    private val inactiveTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = trackHeight
    }

    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(
            4f * density,
            0f,
            2f * density,
            Color.argb(65, 0, 0, 0)
        )
    }

    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * density
        color = Color.argb(30, 0, 0, 0)
    }

    private var scaleAnimator: ValueAnimator? = null
    private var isDragging = false
    private var touchDownX = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        setWillNotDraw(false)
        updateTrackColors()
    }

    private val isDarkMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private fun updateTrackColors() {
        inactiveTrackPaint.color = if (isDarkMode) {
            Color.parseColor("#38383A")
        } else {
            Color.parseColor("#E5E5EA")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateTrackColors()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (38f * density).toInt()
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, h)
    }

    private fun animateThumbScale(expanded: Boolean) {
        scaleAnimator?.cancel()
        val targetScale = if (expanded) 1.12f else 1.0f
        val duration = if (expanded) 120L else 180L
        val interpolator = if (expanded) DecelerateInterpolator() else OvershootInterpolator(1.2f)

        scaleAnimator = ValueAnimator.ofFloat(thumbScale, targetScale).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                thumbScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun performHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
            } else {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        } catch (ignored: Throwable) {}
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val x = event.x

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                animateThumbScale(true)
                performHaptic()
                updateProgressFromX(x, true)
                onSliderChangeListener?.onStartTrackingTouch(this)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateProgressFromX(x, true)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    isDragging = false
                    animateThumbScale(false)
                    updateProgressFromX(x, true)
                    onSliderChangeListener?.onStopTrackingTouch(this)
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    isDragging = false
                    animateThumbScale(false)
                    onSliderChangeListener?.onStopTrackingTouch(this)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromX(x: Float, fromUser: Boolean) {
        val trackLeft = baseThumbRadius
        val trackRight = width.toFloat() - baseThumbRadius
        val trackWidth = (trackRight - trackLeft).coerceAtLeast(1f)

        var ratio = ((x - trackLeft) / trackWidth).coerceIn(0f, 1f)
        if (steps > 0) {
            val stepVal = Math.round(ratio * steps).toFloat() / steps
            ratio = stepVal.coerceIn(0f, 1f)
        }

        val newProgress = min + ratio * (max - min)
        if (abs(newProgress - progress) > 1e-4f) {
            progress = newProgress
            invalidate()
            if (fromUser) {
                onSliderChangeListener?.onProgressChanged(this, progress, true)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cy = h / 2f
        val trackLeft = baseThumbRadius
        val trackRight = w - baseThumbRadius
        val trackWidth = (trackRight - trackLeft).coerceAtLeast(1f)

        val span = (max - min).coerceAtLeast(1e-4f)
        val ratio = ((progress - min) / span).coerceIn(0f, 1f)
        val thumbX = trackLeft + ratio * trackWidth

        // 1. Draw Inactive Track (Full span background)
        canvas.drawLine(trackLeft, cy, trackRight, cy, inactiveTrackPaint)

        // 2. Draw Active Track (From left to thumb position)
        if (thumbX > trackLeft) {
            canvas.drawLine(trackLeft, cy, thumbX, cy, activeTrackPaint)
        }

        // 3. Draw Thumb (Pure white knob with soft layered drop shadow and subtle stroke)
        val currentRadius = baseThumbRadius * thumbScale
        canvas.drawCircle(thumbX, cy, currentRadius, thumbShadowPaint)
        canvas.drawCircle(thumbX, cy, currentRadius, thumbFillPaint)
        canvas.drawCircle(thumbX, cy, currentRadius, thumbStrokePaint)
    }
}
