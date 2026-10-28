package foundation.e.bliss.widgets.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 1:1 replica of the iOS squircle digital clock widget with dynamic perimeter seconds track.
 *
 * Designed to be hosted inside [foundation.e.bliss.widgets.BaseGlassWidgetHostView]
 * to provide authentic Liquid Glass backdrop with live wallpaper refraction.
 */
class DigitalClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textScaleX = 0.84f
        letterSpacing = -0.02f
    }

    private val colonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val textBounds = Rect()
    private var calendar: Calendar = Calendar.getInstance()
    private var isRunning = false
    private var isReceiverRegistered = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            invalidate()
            postOnAnimation(this)
        }
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_TIMEZONE_CHANGED == intent.action) {
                val tz = intent.getStringExtra("time-zone")
                calendar = if (tz != null) Calendar.getInstance(TimeZone.getTimeZone(tz))
                else Calendar.getInstance()
            } else {
                calendar = Calendar.getInstance()
            }
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        calendar = Calendar.getInstance()
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(timeReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(timeReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (ignored: Throwable) {}
        }

        if (visibility == VISIBLE && windowVisibility == VISIBLE) {
            startTicking()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTicking()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(timeReceiver)
            } catch (ignored: Throwable) {}
            isReceiverRegistered = false
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            calendar = Calendar.getInstance()
            startTicking()
        } else {
            stopTicking()
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && isAttachedToWindow) {
            calendar = Calendar.getInstance()
            startTicking()
        } else {
            stopTicking()
        }
    }

    private fun startTicking() {
        if (!isRunning && isAttachedToWindow && windowVisibility == VISIBLE) {
            isRunning = true
            removeCallbacks(frameRunnable)
            postOnAnimation(frameRunnable)
        }
    }

    private fun stopTicking() {
        if (isRunning) {
            isRunning = false
            removeCallbacks(frameRunnable)
        }
    }

    /**
     * Exact analytical ray intersection distance from center (cx, cy) to rounded rectangle
     * boundary at angle [rad], with given [inset].
     */
    private fun getSquircleRadiusAtAngle(
        rad: Double,
        hw: Float,
        hh: Float,
        crOrig: Float,
        inset: Float
    ): Float {
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val ax = abs(dx)
        val ay = abs(dy)
        val wInset = hw - inset
        val hInset = hh - inset
        val cr = max(1f, crOrig - inset)
        val xc = wInset - cr
        val yc = hInset - cr

        if (ax > 1e-5f) {
            val tv = wInset / ax
            if (tv * ay <= yc) {
                return tv
            }
        }

        if (ay > 1e-5f) {
            val th = hInset / ay
            if (th * ax <= xc) {
                return th
            }
        }

        val b = ax * xc + ay * yc
        val c = xc * xc + yc * yc - cr * cr
        val disc = max(0f, b * b - c)
        return b + sqrt(disc)
    }

    /**
     * Calculates alpha for tick mark [s] (0..59) given the [second] (0..59) and fractional [ms] (0..999).
     *
     * Provides a smooth ease-in fade as each new second begins, gentle pulsing breath while active,
     * and a seamless minute rollover fade-out across the entire dial.
     */
    private fun getTickAlpha(s: Int, second: Int, ms: Int): Int {
        val inactiveAlpha = 75
        val activeAlpha = 255

        if (second == 0) {
            return if (s == 0) {
                if (ms < 300) {
                    val tIn = ms / 300f
                    val easeIn = tIn * tIn * (3f - 2f * tIn)
                    (inactiveAlpha + (activeAlpha - inactiveAlpha) * easeIn).toInt()
                } else {
                    val pPulse = (ms - 300f) / 700f
                    val pulse = (sin(pPulse * Math.PI) * 40f).toFloat()
                    (activeAlpha - pulse).toInt().coerceIn(inactiveAlpha, activeAlpha)
                }
            } else {
                if (ms < 450) {
                    val tFade = ms / 450f
                    val ease = tFade * tFade * (3f - 2f * tFade)
                    (activeAlpha - (activeAlpha - inactiveAlpha) * ease).toInt()
                } else {
                    inactiveAlpha
                }
            }
        } else {
            return if (s < second) {
                activeAlpha
            } else if (s == second) {
                if (ms < 300) {
                    val tIn = ms / 300f
                    val easeIn = tIn * tIn * (3f - 2f * tIn)
                    (inactiveAlpha + (activeAlpha - inactiveAlpha) * easeIn).toInt()
                } else {
                    val pPulse = (ms - 300f) / 700f
                    val pulse = (sin(pPulse * Math.PI) * 40f).toFloat()
                    (activeAlpha - pulse).toInt().coerceIn(inactiveAlpha, activeAlpha)
                }
            } else {
                inactiveAlpha
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cardSize = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val hw = cardSize / 2f
        val hh = cardSize / 2f
        val crOrig = cardSize * 0.225f // iOS Squircle ratio

        val now = System.currentTimeMillis()
        calendar.timeInMillis = now
        val currentSecond = calendar.get(Calendar.SECOND)
        val ms = (now % 1000L).toInt()

        // 1. Draw 60 seconds tick marks along squircle perimeter track
        val outerInset = cardSize * 0.042f
        val innerInset = cardSize * 0.086f
        val strokeW = cardSize * 0.013f
        tickPaint.strokeWidth = strokeW

        for (s in 0 until 60) {
            val deg = s * 6.0
            val rad = Math.toRadians(deg - 90.0)
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            val rOut = getSquircleRadiusAtAngle(rad, hw, hh, crOrig, outerInset)
            val rIn = getSquircleRadiusAtAngle(rad, hw, hh, crOrig, innerInset)

            val x1 = cx + rIn * cosA
            val y1 = cy + rIn * sinA
            val x2 = cx + rOut * cosA
            val y2 = cy + rOut * sinA

            val alpha = getTickAlpha(s, currentSecond, ms)
            tickPaint.alpha = alpha
            if (alpha > 120) {
                tickPaint.setShadowLayer(
                    cardSize * 0.008f,
                    0f,
                    cardSize * 0.002f,
                    Color.argb((alpha * 0.24f).toInt(), 0, 0, 0)
                )
            } else {
                tickPaint.clearShadowLayer()
            }
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // 2. Draw Digital Clock (Hours : Minutes) centered with enlarged font height
        val is24Hour = DateFormat.is24HourFormat(context)
        val rawHour = if (is24Hour) {
            calendar.get(Calendar.HOUR_OF_DAY)
        } else {
            val h12 = calendar.get(Calendar.HOUR)
            if (h12 == 0) 12 else h12
        }
        val hourStr = rawHour.toString()
        val minute = calendar.get(Calendar.MINUTE)
        val minStr = String.format(Locale.getDefault(), "%02d", minute)

        // Target text sizing: up to 48% of card size, constrained to 79% inner width
        val maxAllowedWidth = cardSize * 0.79f
        var targetTextSize = cardSize * 0.48f
        timePaint.textSize = targetTextSize
        timePaint.setShadowLayer(cardSize * 0.012f, 0f, cardSize * 0.003f, Color.argb(75, 0, 0, 0))

        var hourW = timePaint.measureText(hourStr)
        var minW = timePaint.measureText(minStr)
        var dotR = targetTextSize * 0.052f
        var colonW = dotR * 2f + targetTextSize * 0.06f
        var totalW = hourW + colonW + minW

        if (totalW > maxAllowedWidth) {
            val scale = maxAllowedWidth / totalW
            targetTextSize *= scale
            timePaint.textSize = targetTextSize
            hourW = timePaint.measureText(hourStr)
            minW = timePaint.measureText(minStr)
            dotR = targetTextSize * 0.052f
            colonW = dotR * 2f + targetTextSize * 0.06f
            totalW = hourW + colonW + minW
        }

        timePaint.getTextBounds("80", 0, 2, textBounds)
        val refH = textBounds.height().toFloat()
        val baseY = cy - textBounds.exactCenterY()
        val startX = cx - totalW / 2f

        // Draw Hour
        canvas.drawText(hourStr, startX, baseY, timePaint)

        // Draw Colon (Two round circular dots optically centered)
        val colonCx = startX + hourW + colonW / 2f
        val dotOffset = refH * 0.22f
        colonPaint.setShadowLayer(cardSize * 0.012f, 0f, cardSize * 0.003f, Color.argb(75, 0, 0, 0))
        canvas.drawCircle(colonCx, cy - dotOffset, dotR, colonPaint)
        canvas.drawCircle(colonCx, cy + dotOffset, dotR, colonPaint)

        // Draw Minute
        canvas.drawText(minStr, startX + hourW + colonW, baseY, timePaint)
    }
}
