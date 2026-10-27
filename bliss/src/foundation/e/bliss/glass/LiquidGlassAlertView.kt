/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.bliss.glass

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.Launcher
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

/**
 * Authentic iOS Liquid Glass modal alert dialog displayed seamlessly on Launcher DragLayer.
 * Combines full-screen dim scrim, frosted occlusion, LiquidGlass specular refraction, and fluid spring physics.
 */
class LiquidGlassAlertView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractFloatingView(context, attrs, defStyleAttr), Insettable {

    enum class ActionStyle {
        DESTRUCTIVE,
        ACTION,
        CANCEL
    }

    data class Action(
        val title: CharSequence,
        val style: ActionStyle,
        val onClick: (() -> Unit)? = null
    )

    private val launcher: Launcher = Launcher.getLauncher(context)
    private var titleText: CharSequence? = null
    private var messageText: CharSequence? = null
    private val actions = mutableListOf<Action>()
    private var onDismissListener: (() -> Unit)? = null

    private lateinit var scrimView: View
    private lateinit var cardContainer: FrameLayout
    private lateinit var glassBg: PopupGlassView
    private lateinit var contentColumn: LinearLayout

    private var isDismissHandled = false

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    fun setTitle(title: CharSequence?): LiquidGlassAlertView {
        this.titleText = title
        return this
    }

    fun setMessage(message: CharSequence?): LiquidGlassAlertView {
        this.messageText = message
        return this
    }

    fun addAction(
        title: CharSequence,
        style: ActionStyle,
        onClick: (() -> Unit)? = null
    ): LiquidGlassAlertView {
        actions.add(Action(title, style, onClick))
        return this
    }

    fun setOnDismissListener(listener: (() -> Unit)?): LiquidGlassAlertView {
        this.onDismissListener = listener
        return this
    }

    private fun isDarkTheme(): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun buildUI() {
        removeAllViews()
        val density = resources.displayMetrics.density
        val cardWidth = (270 * density).toInt()
        val cornerRadiusPx = 20f * density
        val isDark = isDarkTheme()
        val dividerColor = Color.parseColor("#2E000000")
        val hairlineThickness = maxOf(1, (0.55f * density).toInt())

        val geometry = ContinuousCornerGeometry()

        // Root FrameLayout hosting scrim and centered card
        val rootFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false
        }

        // Full-screen dim scrim
        scrimView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(if (isDark) Color.parseColor("#73000000") else Color.parseColor("#59000000"))
            alpha = 0f
            isClickable = true
            isFocusable = false
            setOnClickListener {
                close(true)
                notifyDismiss()
            }
        }
        rootFrame.addView(scrimView)

        // UI Content Column defining the intrinsic card height
        contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header (Title & Message)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val padTop = (20 * density).toInt()
            val padBottom = if (messageText.isNullOrEmpty()) (20 * density).toInt() else (16 * density).toInt()
            val padHoriz = (18 * density).toInt()
            setPadding(padHoriz, padTop, padHoriz, padBottom)
        }

        if (!titleText.isNullOrEmpty()) {
            val titleView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                text = titleText
                textSize = 17f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.parseColor("#000000"))
                setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics), 1.0f)
            }
            headerLayout.addView(titleView)
        }

        if (!messageText.isNullOrEmpty()) {
            val messageView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (!titleText.isNullOrEmpty()) {
                        topMargin = (4 * density).toInt()
                    }
                }
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                text = messageText
                textSize = 13f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setTextColor(Color.parseColor("#7A000000"))
                setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics), 1.0f)
            }
            headerLayout.addView(messageView)
        }
        contentColumn.addView(headerLayout)

        // Hairline divider under header
        val headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
            setBackgroundColor(dividerColor)
        }
        contentColumn.addView(headerDivider)

        // Action Buttons
        val rowHeight = (44 * density).toInt()
        val canBeHorizontal = actions.size == 2 &&
                actions[0].title.length <= 14 &&
                actions[1].title.length <= 14

        if (canBeHorizontal) {
            val buttonRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
            }
            val btn0 = createActionButton(actions[0], isDark, LinearLayout.LayoutParams(0, rowHeight, 1.0f))
            val btn1 = createActionButton(actions[1], isDark, LinearLayout.LayoutParams(0, rowHeight, 1.0f))
            val vertDivider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(hairlineThickness, LinearLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(dividerColor)
            }
            buttonRow.addView(btn0)
            buttonRow.addView(vertDivider)
            buttonRow.addView(btn1)
            contentColumn.addView(buttonRow)
        } else {
            for (i in actions.indices) {
                if (i > 0) {
                    val horizDivider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
                        setBackgroundColor(dividerColor)
                    }
                    contentColumn.addView(horizDivider)
                }
                val btn = createActionButton(
                    actions[i],
                    isDark,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
                )
                contentColumn.addView(btn)
            }
        }

        // Layer 0: Liquid Glass view for refraction, bevel, dispersion, and specular rim
        glassBg = PopupGlassView(context).apply {
            setPopupRadius(cornerRadiusPx)
        }

        // Custom CardContainer that sizes tightly to contentColumn without expanding vertically
        cardContainer = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val widthSpec = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
                contentColumn.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val targetHeight = contentColumn.measuredHeight
                val heightSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)

                glassBg.measure(widthSpec, heightSpec)

                setMeasuredDimension(cardWidth, targetHeight)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val w = right - left
                val h = bottom - top
                glassBg.layout(0, 0, w, h)
                contentColumn.layout(0, 0, w, h)
                pivotX = w / 2f
                pivotY = h / 2f
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (view.width <= 0 || view.height <= 0) return
                    val path = geometry.getPath(
                        width = view.width.toFloat(),
                        height = view.height.toFloat(),
                        radius = cornerRadiusPx,
                        smoothing = GlassShapeSpec.IOS_SMOOTHING
                    )
                    outline.setConvexPath(path)
                }
            }
            clipToOutline = true
        }

        cardContainer.addView(glassBg)
        cardContainer.addView(contentColumn)

        rootFrame.addView(cardContainer)
        addView(rootFrame)
    }

    private fun createActionButton(
        action: Action,
        isDark: Boolean,
        lp: LinearLayout.LayoutParams
    ): TextView {
        return TextView(context).apply {
            layoutParams = lp
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = action.title
            textSize = 17f
            isClickable = true
            isFocusable = true

            when (action.style) {
                ActionStyle.DESTRUCTIVE -> {
                    setTextColor(Color.parseColor("#FF3B30"))
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                }
                ActionStyle.ACTION -> {
                    setTextColor(Color.parseColor("#007AFF"))
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                }
                ActionStyle.CANCEL -> {
                    setTextColor(Color.parseColor("#007AFF"))
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                }
            }

            val rippleColor = Color.argb(25, 0, 0, 0)
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, ColorDrawable(Color.WHITE))

            setOnClickListener {
                close(true)
                action.onClick?.invoke()
            }
        }
    }

    fun show() {
        val dragLayer = launcher.dragLayer ?: return
        buildUI()

        // Close any conflicting floating views
        AbstractFloatingView.closeOpenViews(
            launcher,
            false,
            TYPE_OPTIONS_POPUP_DIALOG or TYPE_ACTION_POPUP
        )

        val lp = InsettableFrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            ignoreInsets = true
        }
        layoutParams = lp
        elevation = 300f
        dragLayer.addView(this)
        mIsOpen = true
        launcher.updateDisallowBack()

        post {
            glassBg.bindToWallpaper()
        }

        cardContainer.scaleX = 0.88f
        cardContainer.scaleY = 0.88f
        cardContainer.alpha = 0f

        val spring = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        val scaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.88f, 1f)
        val scaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.88f, 1f)
        val cardAlpha = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)
        val scrimAlpha = ObjectAnimator.ofFloat(scrimView, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, cardAlpha, scrimAlpha)
            duration = 260
            interpolator = spring
            start()
        }
    }

    override fun canHandleBack(): Boolean = true

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    override fun onBackProgressed(backEvent: android.window.BackEvent) {
        val progress = backEvent.progress
        val scale = 1f - 0.08f * progress
        cardContainer.scaleX = scale
        cardContainer.scaleY = scale
        cardContainer.alpha = 1f - 0.25f * progress
        scrimView.alpha = 1f - 0.35f * progress
    }

    override fun onBackCancelled() {
        cardContainer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
        scrimView.animate().alpha(1f).setDuration(150).start()
    }

    override fun onBackInvoked() {
        cardContainer.scaleX = 1f
        cardContainer.scaleY = 1f
        cardContainer.alpha = 1f
        close(true)
        notifyDismiss()
    }

    override fun isOfType(type: Int): Boolean {
        return (type and TYPE_OPTIONS_POPUP_DIALOG) != 0
    }

    override fun setInsets(insets: Rect?) {
        // Full screen edge-to-edge overlay
    }

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen) return
        mIsOpen = false
        launcher.updateDisallowBack()

        if (animate) {
            val exitScaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 1f, 0.92f)
            val exitScaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 1f, 0.92f)
            val exitCardAlpha = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 1f, 0f)
            val exitScrimAlpha = ObjectAnimator.ofFloat(scrimView, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                playTogether(exitScaleX, exitScaleY, exitCardAlpha, exitScrimAlpha)
                duration = 160
                interpolator = AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeViewFromDragLayer()
                        notifyDismiss()
                    }
                })
                start()
            }
        } else {
            removeViewFromDragLayer()
            notifyDismiss()
        }
    }

    private fun removeViewFromDragLayer() {
        launcher.dragLayer?.removeView(this)
        launcher.updateDisallowBack()
    }

    private fun notifyDismiss() {
        if (!isDismissHandled) {
            isDismissHandled = true
            onDismissListener?.invoke()
        }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun showRemoveAppAlert(
            launcher: Launcher,
            appTitle: CharSequence,
            canUninstall: Boolean,
            onDeleteApp: Runnable? = null,
            onRemoveFromHome: Runnable? = null,
            onDismiss: Runnable? = null
        ): LiquidGlassAlertView {
            val alert = LiquidGlassAlertView(launcher)
            alert.setTitle("Remove \"$appTitle\"?")
            alert.setMessage("Removing from Home Screen will keep the app in your App Library.")
            if (canUninstall) {
                alert.addAction("Uninstall", ActionStyle.DESTRUCTIVE) { onDeleteApp?.run() }
                alert.addAction("Remove from Home Screen", ActionStyle.ACTION) { onRemoveFromHome?.run() }
                alert.addAction("Cancel", ActionStyle.CANCEL)
            } else {
                alert.addAction("Remove from Home Screen", ActionStyle.DESTRUCTIVE) { onRemoveFromHome?.run() }
                alert.addAction("Cancel", ActionStyle.CANCEL)
            }
            alert.setOnDismissListener { onDismiss?.run() }
            alert.show()
            return alert
        }

        @JvmStatic
        @JvmOverloads
        fun showRemoveFolderAlert(
            launcher: Launcher,
            folderTitle: CharSequence?,
            onRemoveFromHome: Runnable? = null,
            onDismiss: Runnable? = null
        ): LiquidGlassAlertView {
            val alert = LiquidGlassAlertView(launcher)
            val title = if (!folderTitle.isNullOrBlank()) "Remove \"$folderTitle\"?" else "Remove Folder?"
            alert.setTitle(title)
            alert.setMessage("Removing this folder from Home Screen will keep its apps in your App Library.")
            alert.addAction("Remove from Home Screen", ActionStyle.DESTRUCTIVE) { onRemoveFromHome?.run() }
            alert.addAction("Cancel", ActionStyle.CANCEL)
            alert.setOnDismissListener { onDismiss?.run() }
            alert.show()
            return alert
        }

        @JvmStatic
        @JvmOverloads
        fun showRemoveWidgetAlert(
            launcher: Launcher,
            widgetLabel: CharSequence?,
            onRemoveWidget: Runnable? = null,
            onDismiss: Runnable? = null
        ): LiquidGlassAlertView {
            val alert = LiquidGlassAlertView(launcher)
            val title = if (!widgetLabel.isNullOrBlank()) "Remove \"$widgetLabel\" Widget?" else "Remove Widget?"
            alert.setTitle(title)
            alert.setMessage("Removing this widget will not delete its app or data.")
            // Short 2 buttons -> horizontal side-by-side with Cancel on left and Remove on right
            alert.addAction("Cancel", ActionStyle.CANCEL)
            alert.addAction("Remove", ActionStyle.DESTRUCTIVE) { onRemoveWidget?.run() }
            alert.setOnDismissListener { onDismiss?.run() }
            alert.show()
            return alert
        }

        @JvmStatic
        @JvmOverloads
        fun showDeleteAppAlert(
            launcher: Launcher,
            appTitle: CharSequence,
            onDeleteApp: Runnable? = null,
            onDismiss: Runnable? = null
        ): LiquidGlassAlertView {
            val alert = LiquidGlassAlertView(launcher)
            alert.setTitle("Uninstall \"$appTitle\"?")
            alert.setMessage("Uninstalling this app will also delete its data.")
            // Short 2 buttons -> horizontal side-by-side with Cancel on left and Uninstall on right
            alert.addAction("Cancel", ActionStyle.CANCEL)
            alert.addAction("Uninstall", ActionStyle.DESTRUCTIVE) { onDeleteApp?.run() }
            alert.setOnDismissListener { onDismiss?.run() }
            alert.show()
            return alert
        }
    }
}
