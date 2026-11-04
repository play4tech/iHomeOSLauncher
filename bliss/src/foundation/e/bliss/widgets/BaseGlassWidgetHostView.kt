package foundation.e.bliss.widgets

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
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.LauncherAppWidgetHostView
import foundation.e.bliss.glass.FolderGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec
import kotlin.math.min

abstract class BaseGlassWidgetHostView(
    context: Context,
    labelResId: Int,
    protected val contentView: View
) : LauncherAppWidgetHostView(context), GlassWidgetHostView {

    protected val launcher: Launcher = Launcher.getLauncher(context)
    protected var mGlassView: FolderGlassView? = null

    protected val labelView = TextView(context).apply {
        elevation = 2f
    }
    protected val squircleGeometry = ContinuousCornerGeometry()
    protected val squircleBounds = Rect()

    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    protected val fallbackGlassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(65, 255, 255, 255)
    }

    protected val previewBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = Color.argb(100, 255, 255, 255)
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
        val isDarkText = Themes.getAttrBoolean(launcher, R.attr.isWorkspaceDarkText)
        val textColor = Themes.getAttrColor(launcher, R.attr.workspaceTextColor)

        labelView.apply {
            text = context.getString(labelResId)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dp.iconTextSizePx.toFloat())
            setTextColor(textColor)
            // Zero shadow under widget labels
            setShadowLayer(0f, 0f, 0f, 0)
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

    protected fun ensureChildren() {
        // 1. Remove foreign child views inflated by Android framework
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child !== mGlassView && (contentView == null || child !== contentView) && child !== labelView) {
                removeViewAt(i)
            }
        }

        // 2. Ensure glassView is at index 0 if present
        mGlassView?.let { gv ->
            if (gv.parent !== this) {
                (gv.parent as? ViewGroup)?.removeView(gv)
                addView(gv, 0)
            } else if (indexOfChild(gv) != 0) {
                removeView(gv)
                addView(gv, 0)
            }
        }

        // 3. Ensure contentView is attached at index 1 (or 0 if glassView is null)
        contentView?.let { cv ->
            val targetIndex = if (mGlassView != null && mGlassView?.parent === this) 1 else 0
            if (cv.parent !== this) {
                (cv.parent as? ViewGroup)?.removeView(cv)
                addView(cv, targetIndex.coerceAtMost(childCount))
            } else if (indexOfChild(cv) != targetIndex) {
                removeView(cv)
                addView(cv, targetIndex.coerceAtMost(childCount))
            }
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
        ensureChildren()
    }

    override fun getGlassView(): FolderGlassView? = mGlassView

    override fun getSquircleBoundsInSelf(outRect: Rect) {
        outRect.set(squircleBounds)
    }

    override fun getSquircleRadius(): Float = squircleBounds.width() * 0.225f

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
        if (appWidgetId == -1) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val root = rootView
        val source = root?.findViewById<View>(R.id.glass_wallpaper_proxy)
            ?: root?.findViewById(R.id.glass_source)

        if (mGlassView == null) {
            mGlassView = FolderGlassView(context).apply {
                if (source != null) bind(source)
                visibility = View.VISIBLE
            }
        } else {
            if (source != null) mGlassView?.bind(source)
        }
        ensureChildren()
    }

    private fun detachGlassView() {
        mGlassView?.let { gv ->
            gv.unbind()
            gv.visibility = View.GONE
            if (gv.parent === this) removeView(gv)
        }
        mGlassView = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        ensureChildren()
        val isPreview = appWidgetId == -1
        if (isPreview) {
            labelView.visibility = View.GONE
        }
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val dp = launcher.deviceProfile
        val labelSpacing = (6f * resources.displayMetrics.density).toInt().coerceAtLeast(dp.iconDrawablePaddingPx)

        var labelHeight = 0
        if (!isPreview && labelView.visibility == View.VISIBLE) {
            labelView.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            labelHeight = labelView.measuredHeight + labelSpacing
        }

        val availableSquareHeight = (h - labelHeight).coerceAtLeast(0)
        val squircleSize = min(w, availableSquareHeight)

        mGlassView?.measure(
            MeasureSpec.makeMeasureSpec(squircleSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(squircleSize, MeasureSpec.EXACTLY)
        )
        contentView?.measure(
            MeasureSpec.makeMeasureSpec(squircleSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(squircleSize, MeasureSpec.EXACTLY)
        )

        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        clipToOutline = false
        outlineProvider = null
        ensureChildren()
        val isPreview = appWidgetId == -1
        if (isPreview) {
            labelView.visibility = View.GONE
        }
        val w = right - left
        val h = bottom - top
        val dp = launcher.deviceProfile
        val labelSpacing = (6f * resources.displayMetrics.density).toInt().coerceAtLeast(dp.iconDrawablePaddingPx)

        val labelHeight = if (!isPreview && labelView.visibility == View.VISIBLE) {
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

        mGlassView?.layout(
            squircleBounds.left,
            squircleBounds.top,
            squircleBounds.right,
            squircleBounds.bottom
        )

        contentView?.layout(
            squircleBounds.left,
            squircleBounds.top,
            squircleBounds.right,
            squircleBounds.bottom
        )

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
        if (appWidgetId == -1) return false
        if (ev.action == MotionEvent.ACTION_DOWN) {
            downX = ev.x
            downY = ev.y
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (appWidgetId == -1) return false
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
        val hasActiveGlass = mGlassView != null && mGlassView?.visibility == View.VISIBLE
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
            if (appWidgetId == -1) {
                canvas.drawPath(path, previewBorderPaint)
            }
            canvas.restore()
        }
        super.dispatchDraw(canvas)
    }

    override fun prepareDrawDragView(): SafeCloseable {
        val superCloseable = super.prepareDrawDragView()
        val gv = mGlassView
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
