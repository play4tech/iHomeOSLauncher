package foundation.e.bliss.widgets.calendar

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
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

class CalendarDayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF453A") // iOS vibrant red
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93") // iOS secondary label gray
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL_AND_STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        try {
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        } catch (ignored: Throwable) {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    private var calendar: Calendar = Calendar.getInstance()
    private var isReceiverRegistered = false

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(timeReceiver, filter)
            }
            isReceiverRegistered = true
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(timeReceiver)
            } catch (ignored: Throwable) {}
            isReceiverRegistered = false
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            calendar = Calendar.getInstance()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val locale = Locale.getDefault()
        val cardSize = min(w, h)

        // Format day of week and month
        val rawDay = SimpleDateFormat("EEE", locale).format(calendar.time).replace(".", "").trim()
        val rawMonth = SimpleDateFormat("MMM", locale).format(calendar.time).replace(".", "").trim()

        val dayText = rawDay.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val monthText = rawMonth.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

        val headerTextSize = cardSize * 0.150f // Matched to iOS proportion
        val dateTextSize = cardSize * 0.60f

        dayPaint.apply {
            color = Color.parseColor("#FF453A")
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = headerTextSize
            letterSpacing = -0.01f
            clearShadowLayer()
        }

        monthPaint.apply {
            color = Color.parseColor("#8E8E93")
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = headerTextSize
            letterSpacing = -0.01f
            setShadowLayer(cardSize * 0.008f, 0f, cardSize * 0.002f, Color.argb(45, 0, 0, 0))
        }

        val dateStr = calendar.get(Calendar.DAY_OF_MONTH).toString()
        datePaint.apply {
            color = Color.WHITE
            style = Paint.Style.FILL_AND_STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = cardSize * 0.010f
            try {
                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            } catch (ignored: Throwable) {
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            textSize = dateTextSize
            letterSpacing = -0.02f
            setShadowLayer(cardSize * 0.010f, 0f, cardSize * 0.003f, Color.argb(55, 0, 0, 0))
        }

        // True Visual Glyph Bounds Measurement (Eliminates FontMetrics Ascent/Descent Padding)
        val capBounds = Rect()
        dayPaint.getTextBounds("T", 0, 1, capBounds)
        val headerCapHeight = capBounds.height().toFloat()

        val dateBounds = Rect()
        datePaint.getTextBounds(dateStr, 0, dateStr.length, dateBounds)
        val dateCapHeight = dateBounds.height().toFloat()

        // Visual gap between baseline of header and top of date digits
        val blockGap = cardSize * 0.048f
        val totalBlockHeight = headerCapHeight + blockGap + dateCapHeight

        // Vertically center the visual block (nudged slightly down by 2% to match iOS optical balance)
        val topMargin = (h - totalBlockHeight) / 2f + (h * 0.02f)

        // Nudge header slightly up for optimal breathing room and iOS proportion
        val headerShiftUp = cardSize * 0.036f
        val headerBaseline = topMargin - capBounds.top - headerShiftUp
        val dateBaseline = topMargin - capBounds.top + blockGap - dateBounds.top

        // Horizontal Centering
        val dayWidth = dayPaint.measureText(dayText)
        val spaceWidth = monthPaint.measureText(" ") * 0.85f
        val monthWidth = monthPaint.measureText(monthText)
        val totalHeaderWidth = dayWidth + spaceWidth + monthWidth

        val headerX = (w - totalHeaderWidth) / 2f
        val dateX = (w - datePaint.measureText(dateStr)) / 2f

        canvas.drawText(dayText, headerX, headerBaseline, dayPaint)
        canvas.drawText(monthText, headerX + dayWidth + spaceWidth, headerBaseline, monthPaint)

        canvas.drawText(dateStr, dateX, dateBaseline, datePaint)
    }
}
