package foundation.e.bliss.widgets.clock

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import com.android.launcher3.InvariantDeviceProfile.KEY_SHOW_DESKTOP_LABELS
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.widget.LauncherAppWidgetHostView
import foundation.e.bliss.glass.FolderGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec
import kotlin.math.min

class ClockWidgetHostView(context: Context) : LauncherAppWidgetHostView(context) {

    private val launcher: Launcher = Launcher.getLauncher(context)
    private var glassView: FolderGlassView? = null
    private val clockDialView = IosAnalogClockView(context).apply {
        isClickable = false
        isFocusable = false
        elevation = 2f
    }
    private val labelView = TextView(context).apply {
        elevation = 2f
    }
    private val squircleGeometry = ContinuousCornerGeometry()
    private val squircleBounds = Rect()

    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private val fallbackGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(65, 255, 255, 255)
    }
    private val fallbackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = Color.argb(80, 255, 255, 255)
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        clipToOutline = false
        outlineProvider = null

        val dp = launcher.deviceProfile
        val density = resources.displayMetrics.density
        val prefs: SharedPreferences = LauncherPrefs.getPrefs(context.applicationContext)
        val showLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true)

        labelView.apply {
            text = context.getString(R.string.clock_widget_label)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dp.iconTextSizePx.toFloat())
            setTextColor(context.getColor(R.color.workspace_text_color_light))
            setShadowLayer(dp.iconTextSizePx * 0.2f, 0f, 1f * density, Color.argb(140, 0, 0, 0))
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            maxLines = 1
            visibility = if (showLabel) View.VISIBLE else View.GONE
            isClickable = false
            isFocusable = false
        }

        ensureChildren()
        setOnClickListener(com.android.launcher3.touch.ItemClickHandler.INSTANCE)
    }

    private fun ensureChildren() {
        // 1. Remove any foreign child views inflated by Android framework
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child !== glassView && child !== clockDialView && child !== labelView) {
                removeViewAt(i)
            }
        }

        // 2. Ensure glassView is at index 0 if present
        glassView?.let { gv ->
            if (gv.parent !== this) {
                (gv.parent as? ViewGroup)?.removeView(gv)
                addView(gv, 0)
            } else if (indexOfChild(gv) != 0) {
                removeView(gv)
                addView(gv, 0)
            }
        }

        // 3. Ensure clockDialView is attached at index 1 (or 0 if glassView is null)
        val targetIndex = if (glassView != null && glassView?.parent === this) 1 else 0
        if (clockDialView.parent !== this) {
            (clockDialView.parent as? ViewGroup)?.removeView(clockDialView)
            addView(clockDialView, targetIndex.coerceAtMost(childCount))
        } else if (indexOfChild(clockDialView) != targetIndex) {
            removeView(clockDialView)
            addView(clockDialView, targetIndex.coerceAtMost(childCount))
        }

        // 4. Ensure labelView is attached
        if (labelView.parent !== this) {
            (labelView.parent as? ViewGroup)?.removeView(labelView)
            addView(labelView)
        }
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        ensureChildren()
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        // Suppress RemoteViews inflation to preserve in-process native rendering
        ensureChildren()
    }

    fun getGlassView(): FolderGlassView? = glassView

    fun getSquircleBoundsInSelf(outRect: Rect) {
        outRect.set(squircleBounds)
    }

    fun getSquircleRadius(): Float = squircleBounds.width() * 0.225f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachGlassView()
        ensureChildren()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachGlassView()
    }

    private fun attachGlassView() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val root = rootView
        val source = root?.findViewById<View>(R.id.glass_wallpaper_proxy)
            ?: root?.findViewById(R.id.glass_source)

        if (glassView == null) {
            glassView = FolderGlassView(context).apply {
                if (source != null) bind(source)
                visibility = View.VISIBLE
            }
        } else {
            if (source != null) glassView?.bind(source)
        }
        ensureChildren()
    }

    private fun detachGlassView() {
        glassView?.let { gv ->
            gv.unbind()
            gv.visibility = View.GONE
            if (gv.parent === this) removeView(gv)
        }
        glassView = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        ensureChildren()
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val dp = launcher.deviceProfile
        val labelSpacing = (6f * resources.displayMetrics.density).toInt().coerceAtLeast(dp.iconDrawablePaddingPx)

        var labelHeight = 0
        if (labelView.visibility == View.VISIBLE) {
            labelView.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            labelHeight = labelView.measuredHeight + labelSpacing
        }

        val availableSquareHeight = (h - labelHeight).coerceAtLeast(0)
        val squircleSize = min(w, availableSquareHeight)
        val dialSize = (squircleSize * 0.83f).toInt()

        glassView?.measure(
            MeasureSpec.makeMeasureSpec(squircleSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(squircleSize, MeasureSpec.EXACTLY)
        )
        clockDialView.measure(
            MeasureSpec.makeMeasureSpec(dialSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dialSize, MeasureSpec.EXACTLY)
        )

        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        clipToOutline = false
        outlineProvider = null
        ensureChildren()
        val w = right - left
        val h = bottom - top
        val dp = launcher.deviceProfile
        val labelSpacing = (6f * resources.displayMetrics.density).toInt().coerceAtLeast(dp.iconDrawablePaddingPx)

        val labelHeight = if (labelView.visibility == View.VISIBLE) {
            labelView.measuredHeight + labelSpacing
        } else 0

        val squircleSize = min(w, (h - labelHeight).coerceAtLeast(0))
        val totalBlockHeight = squircleSize + labelHeight
        val blockTop = ((h - totalBlockHeight) / 2).coerceAtLeast(0)
        val squircleLeft = (w - squircleSize) / 2

        squircleBounds.set(
            squircleLeft,
            blockTop,
            squircleLeft + squircleSize,
            blockTop + squircleSize
        )

        glassView?.layout(
            squircleBounds.left,
            squircleBounds.top,
            squircleBounds.right,
            squircleBounds.bottom
        )

        val dialSize = (squircleSize * 0.83f).toInt()
        clockDialView.measure(
            MeasureSpec.makeMeasureSpec(dialSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dialSize, MeasureSpec.EXACTLY)
        )
        val dialLeft = squircleBounds.centerX() - dialSize / 2
        val dialTop = squircleBounds.centerY() - dialSize / 2
        clockDialView.layout(dialLeft, dialTop, dialLeft + dialSize, dialTop + dialSize)

        if (labelView.visibility == View.VISIBLE) {
            val labelTop = squircleBounds.bottom + labelSpacing
            val labelLeft = (w - labelView.measuredWidth) / 2
            labelView.layout(
                labelLeft,
                labelTop,
                labelLeft + labelView.measuredWidth,
                labelTop + labelView.measuredHeight
            )
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            downX = ev.x
            downY = ev.y
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isUninstallVisible) {
            return super.onTouchEvent(ev)
        }
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                val isClick = (dx * dx + dy * dy) <= (slop * slop)
                val handled = super.onTouchEvent(ev)
                if (isClick && !isUninstallVisible) {
                    val workspace = launcher.workspace
                    if (workspace != null && !workspace.isWobbling) {
                        performClick()
                    }
                }
                return handled
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val hasActiveGlass = glassView != null && glassView?.visibility == View.VISIBLE
        if (!hasActiveGlass && !squircleBounds.isEmpty) {
            val path = squircleGeometry.getPath(
                squircleBounds.width().toFloat(),
                squircleBounds.height().toFloat(),
                getSquircleRadius(),
                GlassShapeSpec.IOS_SMOOTHING
            )
            canvas.save()
            canvas.translate(squircleBounds.left.toFloat(), squircleBounds.top.toFloat())
            canvas.drawPath(path, fallbackGlassPaint)
            canvas.restore()
        }
        super.dispatchDraw(canvas)
    }

    override fun prepareDrawDragView(): SafeCloseable {
        val superCloseable = super.prepareDrawDragView()
        val gv = glassView
        if (gv != null && gv.visibility == View.VISIBLE) {
            gv.visibility = View.GONE
            return SafeCloseable {
                gv.visibility = View.VISIBLE
                superCloseable.close()
            }
        }
        return superCloseable
    }
}
