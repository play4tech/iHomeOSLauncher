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
 */
package foundation.e.bliss.glass

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.views.BaseDragLayer
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

/**
 * Authentic iOS Liquid Glass capsule pill HUD / Toast.
 * Matching the exact liquid glass effect, specular highlight, and styling
 * of the popup context menu. Drops down smoothly from below the status bar,
 * displays a translucent frosted glass pill with an icon and confirmation message,
 * and slides back up automatically after a short timeout or when dismissed.
 */
class LiquidGlassToast @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractFloatingView(context, attrs, defStyleAttr), Insettable {

    private val launcher: Launcher = Launcher.getLauncher(context)
    private var messageText: CharSequence = ""
    private var iconRes: Int = R.drawable.ic_ios_check_circle

    private lateinit var pillCard: FrameLayout
    private lateinit var glassBg: PopupGlassView
    private lateinit var contentRow: LinearLayout

    private val dismissHandler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { close(true) }
    private var topOffsetPx: Int = 0

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }

    private fun buildUI() {
        removeAllViews()
        val density = resources.displayMetrics.density
        val pillHeightPx = (46 * density).toInt()
        val cornerRadiusPx = pillHeightPx / 2f

        // Content Row containing Icon + Text
        contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (9 * density).toInt(),
                (18 * density).toInt(),
                (9 * density).toInt()
            )
        }

        if (iconRes != 0) {
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (20 * density).toInt(),
                    (20 * density).toInt()
                ).apply {
                    marginEnd = (9 * density).toInt()
                }
                setImageResource(iconRes)
            }
            contentRow.addView(iconView)
        }

        val textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = messageText
            setTextColor(Color.parseColor("#1C1C1E")) // Deep iOS black, matching popup context menu
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        contentRow.addView(textView)

        // Glass reflection & refraction layer matching popup context menu
        glassBg = PopupGlassView(context).apply {
            setPopupRadius(cornerRadiusPx)
        }

        // Pill Card FrameLayout sizing strictly to contentRow
        pillCard = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                contentRow.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(pillHeightPx, MeasureSpec.EXACTLY)
                )
                val w = contentRow.measuredWidth
                val h = pillHeightPx
                val wSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
                val hSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                glassBg.measure(wSpec, hSpec)
                setMeasuredDimension(w, h)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val w = right - left
                val h = bottom - top
                glassBg.layout(0, 0, w, h)
                contentRow.layout(0, 0, w, h)
                pivotX = w / 2f
                pivotY = h / 2f
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            outlineProvider = object : ViewOutlineProvider() {
                private val geometry = ContinuousCornerGeometry()
                override fun getOutline(view: View, outline: Outline) {
                    if (view.width <= 0 || view.height <= 0) return
                    try {
                        val path = geometry.getPath(
                            width = view.width.toFloat(),
                            height = view.height.toFloat(),
                            radius = cornerRadiusPx,
                            smoothing = GlassShapeSpec.IOS_SMOOTHING
                        )
                        outline.setConvexPath(path)
                    } catch (e: Exception) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
            }
            clipToOutline = true
            elevation = 8f * density // 8dp elevation matching ArrowPopup / popup context menu
            isClickable = true
            isFocusable = true
            setOnClickListener { close(true) }
        }

        var startY = 0f
        pillCard.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    false
                }
                MotionEvent.ACTION_UP -> {
                    if (startY - event.rawY > 10f * density) {
                        close(true)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        pillCard.addView(glassBg)
        pillCard.addView(contentRow)

        addView(pillCard)
    }

    private fun updateBackdropSource() {
        if (!::glassBg.isInitialized) return
        val expandedCategory = com.android.launcher3.applibrary.view.AppLibraryCategoryExpandedView.getOpen(launcher)
        val spotlightOverlay = launcher.findViewById<View>(R.id.spotlight_search_overlay)?.takeIf { it.visibility == View.VISIBLE }
        val openFolder = com.android.launcher3.folder.Folder.getOpen(launcher)
        if (spotlightOverlay != null) {
            glassBg.backdropSource = spotlightOverlay
        } else if (expandedCategory != null) {
            glassBg.backdropSource = expandedCategory
        } else if (openFolder != null) {
            glassBg.backdropSource = openFolder
        } else {
            glassBg.bindToWallpaper()
        }
    }

    private fun showInternal() {
        val dragLayer = launcher.dragLayer ?: return
        buildUI()

        val density = resources.displayMetrics.density
        val res = resources
        val sbarId = res.getIdentifier("status_bar_height", "dimen", "android")
        val systemStatusBarHeight = if (sbarId > 0) res.getDimensionPixelSize(sbarId) else (32 * density).toInt()
        val insetsTop = launcher.deviceProfile?.insets?.top ?: systemStatusBarHeight
        val effectiveStatusBarHeight = maxOf(insetsTop, systemStatusBarHeight)
        topOffsetPx = effectiveStatusBarHeight + (10 * density).toInt()

        val lp = BaseDragLayer.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = topOffsetPx
            ignoreInsets = true
        }
        layoutParams = lp
        elevation = 500f
        dragLayer.addView(this)
        mIsOpen = true

        updateBackdropSource()
        post {
            updateBackdropSource()
        }

        // iOS Dynamic Island / Pill slide-down spring animation
        val initialY = -(topOffsetPx + 80f * density)
        translationY = initialY
        alpha = 0f
        scaleX = 0.88f
        scaleY = 0.88f

        val spring = PathInterpolator(0.18f, 1f, 0.25f, 1f)
        val animY = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, initialY, 0f)
        val animAlpha = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f)
        val animScaleX = ObjectAnimator.ofFloat(this, View.SCALE_X, 0.88f, 1f)
        val animScaleY = ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.88f, 1f)

        AnimatorSet().apply {
            playTogether(animY, animAlpha, animScaleX, animScaleY)
            duration = 380
            interpolator = spring
            start()
        }

        // Auto dismiss after 2.4 seconds
        dismissHandler.postDelayed(dismissRunnable, 2400)
    }

    override fun canHandleBack(): Boolean = false

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        // Clicks outside the pill card pass straight through to home screen / app library
        return false
    }

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen) return
        mIsOpen = false
        dismissHandler.removeCallbacks(dismissRunnable)

        if (animate) {
            val density = resources.displayMetrics.density
            val exitY = -(topOffsetPx + 80f * density)
            val exitAnimY = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, translationY, exitY)
            val exitAlpha = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 0f)
            val exitScaleX = ObjectAnimator.ofFloat(this, View.SCALE_X, scaleX, 0.88f)
            val exitScaleY = ObjectAnimator.ofFloat(this, View.SCALE_Y, scaleY, 0.88f)

            AnimatorSet().apply {
                playTogether(exitAnimY, exitAlpha, exitScaleX, exitScaleY)
                duration = 240
                interpolator = AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        launcher.dragLayer?.removeView(this@LiquidGlassToast)
                    }
                })
                start()
            }
        } else {
            launcher.dragLayer?.removeView(this)
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and TYPE_SNACKBAR) != 0
    }

    override fun setInsets(insets: Rect?) {}

    companion object {
        @JvmStatic
        @JvmOverloads
        fun show(
            launcher: Launcher,
            message: CharSequence,
            @DrawableRes iconResId: Int = R.drawable.ic_ios_check_circle
        ): LiquidGlassToast {
            closeOpenViews(launcher, false, TYPE_SNACKBAR)
            val toast = LiquidGlassToast(launcher).apply {
                this.messageText = message
                this.iconRes = iconResId
            }
            toast.showInternal()
            return toast
        }
    }
}
