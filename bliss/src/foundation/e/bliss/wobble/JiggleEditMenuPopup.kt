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
package foundation.e.bliss.wobble

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
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
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.ads.AdManager
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.OptionsPopupView
import foundation.e.bliss.glass.PopupGlassView
import foundation.e.bliss.wallpaper.WallpaperPickerHelper

class JiggleEditMenuPopup
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AbstractFloatingView(context, attrs, defStyleAttr) {

    private val launcher = Launcher.getLauncher(context)
    private var isOpen = false
    private val cardContainer: FrameLayout
    private val glassBg: PopupGlassView
    private val menuRows = mutableListOf<View>()

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false

        val density = resources.displayMetrics.density
        val cardWidth = (196 * density).toInt()
        val rowHeight = (44 * density).toInt()
        val dividerHeight = maxOf(1, (0.55f * density).toInt())
        val cardHeight = (rowHeight * 3) + (dividerHeight * 2)

        cardContainer =
            FrameLayout(context).apply {
                layoutParams =
                    LayoutParams(cardWidth, cardHeight).apply {
                        gravity = Gravity.START or Gravity.TOP
                    }
            }

        glassBg =
            PopupGlassView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                setPopupRadius(26f * density)
            }
        cardContainer.addView(glassBg)

        // Vertical rows container
        val rowsColumn =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }

        // --- Row 1: Add Widget ---
        val rowAddWidget = createRow(
            iconRes = R.drawable.ic_add_widget,
            label = "Add Widget",
            density = density,
            rowHeight = rowHeight
        ) {
            close(true)
            AdManager.showInterstitialWithCooldown(launcher) {
                OptionsPopupView.openWidgets(launcher)
            }
        }
        rowsColumn.addView(rowAddWidget)
        menuRows.add(rowAddWidget)

        rowsColumn.addView(createDivider(density, dividerHeight))

        // --- Row 2: Customize ---
        val rowCustomize = createRow(
            iconRes = R.drawable.ic_palette,
            label = "Customize",
            density = density,
            rowHeight = rowHeight
        ) {
            close(true)
            AdManager.showInterstitialWithCooldown(launcher) {
                try {
                    launcher.startActivity(Intent(launcher, com.android.launcher3.settings.SettingsActivity::class.java))
                } catch (e: Exception) {
                    android.util.Log.e("JiggleEditMenuPopup", "Error launching settings", e)
                }
            }
        }
        rowsColumn.addView(rowCustomize)
        menuRows.add(rowCustomize)

        rowsColumn.addView(createDivider(density, dividerHeight))

        // --- Row 3: Change Wallpaper ---
        val rowEditWallpaper = createRow(
            iconRes = R.drawable.ic_edit_wallpaper,
            label = "Change Wallpaper",
            density = density,
            rowHeight = rowHeight
        ) {
            close(true)
            AdManager.showInterstitialWithCooldown(launcher) {
                launcher.workspace?.wobbleLayouts(false)
                WallpaperPickerHelper.launchGalleryPicker(launcher)
            }
        }
        rowsColumn.addView(rowEditWallpaper)
        menuRows.add(rowEditWallpaper)

        cardContainer.addView(rowsColumn)
        addView(cardContainer)
        isClickable = true
        isFocusable = true
    }

    private fun isDarkTheme(): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun createRow(
        iconRes: Int,
        label: String,
        density: Float,
        rowHeight: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            isClickable = true
            isFocusable = true

            val textColor = Color.BLACK
            val rippleColor = Color.argb(28, 0, 0, 0)
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, ColorDrawable(Color.WHITE))

            setOnClickListener { onClick() }

            val iconView = ImageView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt())
                        .apply { marginEnd = (12 * density).toInt() }
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(textColor)
            }
            addView(iconView)

            val labelView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
                text = label
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            addView(labelView)
        }
    }

    private fun createDivider(density: Float, dividerHeight: Int): View {
        val dividerColor = Color.parseColor("#1F000000")
        return View(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dividerHeight)
                    .apply {
                        marginStart = (48 * density).toInt()
                        marginEnd = (16 * density).toInt()
                    }
            setBackgroundColor(dividerColor)
        }
    }

    fun show(anchorView: View) {
        val dragLayer = launcher.dragLayer ?: return
        val density = resources.displayMetrics.density

        val anchorRect = Rect()
        dragLayer.getDescendantRectRelativeToSelf(anchorView, anchorRect)

        val left = if (anchorRect.left > 0) anchorRect.left else (16 * density).toInt()
        val top = if (anchorRect.bottom > 0) {
            anchorRect.bottom + (4 * density).toInt()
        } else {
            val statusBarHeight = launcher.deviceProfile?.insets?.top ?: (24 * density).toInt()
            statusBarHeight + (42 * density).toInt()
        }

        val cardWidth = cardContainer.layoutParams.width
        val maxLeft = if (dragLayer.width > cardWidth) dragLayer.width - cardWidth - (12 * density).toInt() else left
        val safeLeft = if (maxLeft > (12 * density).toInt()) left.coerceIn((12 * density).toInt(), maxLeft) else left

        val lp = cardContainer.layoutParams as LayoutParams
        lp.marginStart = safeLeft
        lp.topMargin = top
        cardContainer.layoutParams = lp

        layoutParams =
            BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                ignoreInsets = true
            }
        elevation = 200f
        isOpen = true
        mIsOpen = true
        dragLayer.addView(this)
        launcher.updateDisallowBack()

        post { glassBg.bindToWallpaper() }

        cardContainer.pivotX = (16 * density)
        cardContainer.pivotY = 0f
        cardContainer.scaleX = 0.82f
        cardContainer.scaleY = 0.82f
        cardContainer.alpha = 0f

        val spring = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        val scaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.82f, 1f)
        val scaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.82f, 1f)
        val alpha = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)

        android.animation.AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 240
            interpolator = spring
            start()
        }
    }

    override fun canHandleBack(): Boolean = true

    override fun onBackProgressed(backEvent: android.window.BackEvent) {
        val progress = backEvent.progress
        val scale = 1f - 0.08f * progress
        cardContainer.scaleX = scale
        cardContainer.scaleY = scale
        cardContainer.alpha = 1f - 0.25f * progress
    }

    override fun onBackCancelled() {
        cardContainer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start()
    }

    override fun onBackInvoked() {
        cardContainer.scaleX = 1f
        cardContainer.scaleY = 1f
        cardContainer.alpha = 1f
        close(true)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            val dragLayer = launcher.dragLayer
            if (dragLayer != null) {
                for (row in menuRows) {
                    if (dragLayer.isEventOverView(row, event)) {
                        row.performClick()
                        return true
                    }
                }
                if (dragLayer.isEventOverView(cardContainer, event)) {
                    return true
                }
            }
            close(true)
            return true
        }
        return true
    }

    override fun handleClose(animate: Boolean) {
        if (!isOpen) return
        isOpen = false
        mIsOpen = false
        launcher.updateDisallowBack()
        if (animate) {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.82f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.82f)
            val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f)
            ObjectAnimator.ofPropertyValuesHolder(cardContainer, scaleX, scaleY, alpha).apply {
                duration = 180
                interpolator = AccelerateInterpolator()
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            launcher.dragLayer?.removeView(this@JiggleEditMenuPopup)
                            launcher.updateDisallowBack()
                        }
                    }
                )
                start()
            }
        } else {
            launcher.dragLayer?.removeView(this)
            launcher.updateDisallowBack()
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and (TYPE_ACTION_POPUP or TYPE_OPTIONS_POPUP)) != 0
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val dragLayer = launcher.dragLayer ?: return false
            if (!dragLayer.isEventOverView(cardContainer, ev)) {
                close(true)
                return true
            }
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return false
        if (ev.action == MotionEvent.ACTION_UP) {
            val dragLayer = launcher.dragLayer
            if (dragLayer != null) {
                for (row in menuRows) {
                    if (dragLayer.isEventOverView(row, ev)) {
                        row.performClick()
                        return true
                    }
                }
                if (dragLayer.isEventOverView(cardContainer, ev)) {
                    return true
                }
            }
            close(true)
            return true
        }
        return true
    }

    companion object {
        @JvmStatic
        fun showPopup(anchorView: View): JiggleEditMenuPopup {
            val launcher = Launcher.getLauncher(anchorView.context)
            AdManager.preloadInterstitial(launcher)
            AbstractFloatingView.closeOpenViews(launcher, false, TYPE_OPTIONS_POPUP)
            val popup = JiggleEditMenuPopup(anchorView.context)
            popup.show(anchorView)
            return popup
        }
    }
}
