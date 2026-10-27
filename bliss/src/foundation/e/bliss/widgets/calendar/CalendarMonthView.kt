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
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

class CalendarMonthView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val monthHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF453A") // iOS vibrant red
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = 0.02f
    }

    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93") // iOS secondary label gray
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val todayBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF453A") // Clean solid iOS red badge without blurry shadow
    }

    private val todayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
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

        // 7-column grid layout spanning 78% of card width (11% side margins matching iOS)
        val gridLeft = w * 0.110f
        val gridRight = w * 0.890f
        val gridWidth = gridRight - gridLeft
        val colWidth = gridWidth / 7f

        val firstDayOfWeek = calendar.firstDayOfWeek
        val shortWeekdays = DateFormatSymbols.getInstance(locale).shortWeekdays

        val scratchCal = Calendar.getInstance(calendar.timeZone, locale).apply {
            time = calendar.time
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayCol = (scratchCal.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        val totalDays = scratchCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val totalCells = firstDayCol + totalDays
        val rowCount = if (totalCells > 35) 6 else 5

        // Typography sizes tuned for 5 or 6 row layout
        val is6Row = rowCount > 5
        val monthTextSize = if (is6Row) cardSize * 0.064f else cardSize * 0.068f
        val weekdayTextSize = if (is6Row) cardSize * 0.058f else cardSize * 0.062f
        val numberTextSize = if (is6Row) cardSize * 0.060f else cardSize * 0.064f

        monthHeaderPaint.apply {
            textSize = monthTextSize
            clearShadowLayer()
        }
        weekdayPaint.apply {
            textSize = weekdayTextSize
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setShadowLayer(cardSize * 0.005f, 0f, cardSize * 0.0015f, Color.argb(45, 0, 0, 0))
        }
        dayNumberPaint.apply {
            textSize = numberTextSize
            setShadowLayer(cardSize * 0.005f, 0f, cardSize * 0.0015f, Color.argb(45, 0, 0, 0))
        }
        todayNumberPaint.apply {
            textSize = numberTextSize
            clearShadowLayer()
        }
        todayBadgePaint.apply {
            setShadowLayer(cardSize * 0.006f, 0f, cardSize * 0.002f, Color.argb(40, 0, 0, 0))
        }

        val rowStep = if (is6Row) cardSize * 0.098f else cardSize * 0.114f
        val monthToWeekdayGap = if (is6Row) cardSize * 0.092f else cardSize * 0.105f // Fixed cramped header gap
        val weekdayToFirstRowGap = if (is6Row) cardSize * 0.096f else cardSize * 0.110f

        // Vertically Center the Entire Calendar Block using True Cap-Height
        val capBounds = Rect()
        monthHeaderPaint.getTextBounds("O", 0, 1, capBounds)
        val monthCapHeight = capBounds.height().toFloat()

        val totalGridHeight = monthCapHeight + monthToWeekdayGap + weekdayToFirstRowGap + (rowCount - 1) * rowStep
        val topMargin = (h - totalGridHeight) / 2f

        val monthBaseline = topMargin - capBounds.top
        val weekdayBaseline = monthBaseline + monthToWeekdayGap
        val firstRowBaseline = weekdayBaseline + weekdayToFirstRowGap

        // 1. Month Header (e.g. "OCTOBER")
        val monthName = SimpleDateFormat("MMMM", locale).format(calendar.time).uppercase(locale)
        val firstDayOfWeekIndex = (firstDayOfWeek - 1) % 7 + 1
        val firstRawName = shortWeekdays[firstDayOfWeekIndex].trimStart()
        val firstLetter = firstRawName.firstOrNull()?.uppercaseChar()?.toString() ?: "M"
        val col0CenterX = gridLeft + 0.5f * colWidth
        val monthStartX = col0CenterX - (weekdayPaint.measureText(firstLetter) / 2f)
        canvas.drawText(monthName, monthStartX, monthBaseline, monthHeaderPaint)

        // 2. Weekday Header Row (White for Mon-Fri, Gray for Sat-Sun)
        for (col in 0..6) {
            val dayOfWeek = (firstDayOfWeek - 1 + col) % 7 + 1
            val rawName = shortWeekdays[dayOfWeek].trimStart()
            val letter = rawName.firstOrNull()?.uppercaseChar()?.toString() ?: ""
            val colCenterX = gridLeft + (col + 0.5f) * colWidth
            val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
            weekdayPaint.color = if (isWeekend) Color.parseColor("#8E8E93") else Color.WHITE
            canvas.drawText(letter, colCenterX, weekdayBaseline, weekdayPaint)
        }

        // 3. Days Grid & True-Centered Today Badge
        val todayDay = calendar.get(Calendar.DAY_OF_MONTH)
        val badgeRadius = colWidth * 0.48f
        val todayStr = todayDay.toString()
        val numBounds = Rect()
        todayNumberPaint.getTextBounds(todayStr, 0, todayStr.length, numBounds)
        val numberCenterOffset = (numBounds.top + numBounds.bottom) / 2f

        for (dayNum in 1..totalDays) {
            val cellIndex = firstDayCol + dayNum - 1
            val col = cellIndex % 7
            val row = cellIndex / 7

            val dayOfWeek = (firstDayOfWeek - 1 + col) % 7 + 1
            val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)

            val colCenterX = gridLeft + (col + 0.5f) * colWidth
            val rowBaseline = firstRowBaseline + row * rowStep
            val dayStr = dayNum.toString()

            if (dayNum == todayDay) {
                val circleCenterY = rowBaseline + numberCenterOffset
                canvas.drawCircle(colCenterX, circleCenterY, badgeRadius, todayBadgePaint)
                todayNumberPaint.color = Color.WHITE
                canvas.drawText(dayStr, colCenterX, rowBaseline, todayNumberPaint)
            } else {
                dayNumberPaint.color = if (isWeekend) Color.parseColor("#8E8E93") else Color.WHITE
                canvas.drawText(dayStr, colCenterX, rowBaseline, dayNumberPaint)
            }
        }
    }
}
