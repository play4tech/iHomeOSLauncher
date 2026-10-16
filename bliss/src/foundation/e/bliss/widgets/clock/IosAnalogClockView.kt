package foundation.e.bliss.widgets.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class IosAnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val hourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1C1C1E")
        strokeCap = Paint.Cap.ROUND
    }
    private val minuteTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8E8E93")
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1E")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1C1C1E")
        strokeCap = Paint.Cap.ROUND
    }
    private val secondHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF9500")
        strokeCap = Paint.Cap.ROUND
    }
    private val secondHubFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF9500")
    }
    private val centerPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val darkCenterPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1C1C1E")
    }

    private val fontMetrics = Paint.FontMetrics()
    private var calendar: Calendar = Calendar.getInstance()
    private var isRunning = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            invalidate()
            val now = System.currentTimeMillis()
            // Schedule next tick precisely on the boundary of the next whole second
            val delay = 1000L - (now % 1000L)
            postDelayed(this, delay)
        }
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_TIMEZONE_CHANGED == intent.action) {
                val tz = intent.getStringExtra("time-zone")
                calendar = if (tz != null) Calendar.getInstance(TimeZone.getTimeZone(tz))
                           else Calendar.getInstance()
            }
            if (isRunning) {
                removeCallbacks(tickRunnable)
                tickRunnable.run()
            } else {
                invalidate()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (w > 0 && h > 0) min(w, h) else w.coerceAtLeast(h)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            updateRunningState()
        }
    }

    private fun drawStaticDial(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 1. Pure white circular clock face
        canvas.drawCircle(cx, cy, radius, facePaint)

        // 2. 60 perimeter ticks
        val outerTickRadius = radius * 0.950f
        val hourTickLen = radius * 0.082f
        val minTickLen = radius * 0.046f
        hourTickPaint.strokeWidth = radius * 0.022f
        minuteTickPaint.strokeWidth = radius * 0.012f

        for (i in 0 until 60) {
            val angleRad = Math.toRadians((i * 6 - 90).toDouble())
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            val isHour = i % 5 == 0
            val len = if (isHour) hourTickLen else minTickLen
            val paint = if (isHour) hourTickPaint else minuteTickPaint

            canvas.drawLine(
                cx + cosA * (outerTickRadius - len),
                cy + sinA * (outerTickRadius - len),
                cx + cosA * outerTickRadius,
                cy + sinA * outerTickRadius,
                paint
            )
        }

        // 3. Numerals 1..12
        numberPaint.textSize = radius * 0.22f
        numberPaint.getFontMetrics(fontMetrics)
        val textCenterOffset = (fontMetrics.ascent + fontMetrics.descent) / 2f
        val numberRadius = radius * 0.70f

        for (hour in 1..12) {
            val angleRad = Math.toRadians((hour * 30 - 90).toDouble())
            val nx = cx + cos(angleRad).toFloat() * numberRadius
            val ny = cy + sin(angleRad).toFloat() * numberRadius - textCenterOffset
            canvas.drawText(hour.toString(), nx, ny, numberPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        if (!isRunning) {
            updateRunningState()
        }

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f

        // Draw static dial directly to canvas (pure white circle, ticks, numbers)
        drawStaticDial(canvas, cx, cy, radius)

        calendar.timeInMillis = System.currentTimeMillis()
        val seconds = calendar.get(Calendar.SECOND)
        val minutes = calendar.get(Calendar.MINUTE)
        val hours = calendar.get(Calendar.HOUR) % 12

        // 1-second step tick (snaps cleanly to each perimeter tick, 6 degrees per second)
        val secondAngle = seconds * 6f
        val minuteAngle = minutes * 6f + (seconds / 60f) * 6f
        val hourAngle = hours * 30f + (minutes / 60f) * 30f + (seconds / 3600f) * 30f

        // 1. Hour hand (bold, rounded cap)
        handPaint.strokeWidth = radius * 0.064f
        canvas.save()
        canvas.rotate(hourAngle, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - radius * 0.50f, handPaint)
        canvas.restore()

        // 2. Minute hand (noticeably thinner than hour hand, rounded cap)
        handPaint.strokeWidth = radius * 0.042f
        canvas.save()
        canvas.rotate(minuteAngle, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - radius * 0.76f, handPaint)
        canvas.restore()

        // 3. Center dark cap for hour/minute hands
        canvas.drawCircle(cx, cy, radius * 0.046f, darkCenterPinPaint)

        // 4. Orange Second hand + counter-tail + center ring
        secondHandPaint.strokeWidth = radius * 0.016f
        canvas.save()
        canvas.rotate(secondAngle, cx, cy)
        canvas.drawLine(cx, cy + radius * 0.18f, cx, cy - radius * 0.88f, secondHandPaint)
        canvas.restore()

        // Orange hub ring
        secondHubFillPaint.color = Color.parseColor("#FF9500")
        canvas.drawCircle(cx, cy, radius * 0.038f, secondHubFillPaint)
        // White inner ring
        canvas.drawCircle(cx, cy, radius * 0.016f, centerPinPaint)
        // Center dark pin
        canvas.drawCircle(cx, cy, radius * 0.008f, darkCenterPinPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(timeReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(timeReceiver, filter)
            }
        } catch (ignored: Exception) {}
        updateRunningState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTicker()
        try {
            context.unregisterReceiver(timeReceiver)
        } catch (ignored: Exception) {}
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateRunningState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateRunningState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateRunningState()
    }

    private fun updateRunningState() {
        val shouldRun = isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (shouldRun && !isRunning) {
            isRunning = true
            removeCallbacks(tickRunnable)
            tickRunnable.run()
        } else if (!shouldRun && isRunning) {
            stopTicker()
        }
    }

    private fun stopTicker() {
        isRunning = false
        removeCallbacks(tickRunnable)
    }
}
