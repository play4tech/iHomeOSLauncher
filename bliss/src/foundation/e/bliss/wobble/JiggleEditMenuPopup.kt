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
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
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

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false

        val density = resources.displayMetrics.density
        val cardWidth = (230 * density).toInt()
        val cardHeight = (54 * density).toInt()

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
                setPopupRadius(20f * density)
            }
        cardContainer.addView(glassBg)

        val clickAction =
            View.OnClickListener {
                android.util.Log.d("JiggleEditMenuPopup", "Edit Wallpaper clicked!")
                close(true)
                launcher.workspace?.wobbleLayouts(false)
                WallpaperPickerHelper.launchGalleryPicker(launcher)
            }

        // Row content
        val rowLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.ios_pill_button_bg)
                setOnClickListener(clickAction)
            }

        // Wallpaper icon
        val iconView =
            ImageView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
                        .apply { marginEnd = (14 * density).toInt() }
                setImageResource(R.drawable.ic_edit_wallpaper)
                imageTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#1C1C1E"))
            }
        rowLayout.addView(iconView)

        // Label
        val labelView =
            TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
                text = "Edit Wallpaper"
                setTextColor(Color.parseColor("#1C1C1E"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface =
                    android.graphics.Typeface.create(
                        "sans-serif-medium",
                        android.graphics.Typeface.NORMAL
                    )
            }
        rowLayout.addView(labelView)

        cardContainer.setOnClickListener(clickAction)
        cardContainer.isClickable = true
        cardContainer.isFocusable = true

        cardContainer.addView(rowLayout)
        addView(cardContainer)
        isClickable = true
        isFocusable = true
    }

    fun show(anchorView: View) {
        val dragLayer = launcher.dragLayer ?: return
        val density = resources.displayMetrics.density

        val anchorLoc = IntArray(2)
        anchorView.getLocationInWindow(anchorLoc)
        val dragLayerLoc = IntArray(2)
        dragLayer.getLocationInWindow(dragLayerLoc)

        val left = anchorLoc[0] - dragLayerLoc[0]
        val top = anchorLoc[1] - dragLayerLoc[1] + anchorView.height + (6 * density).toInt()

        val lp = cardContainer.layoutParams as LayoutParams
        lp.marginStart = maxOf((12 * density).toInt(), left)
        lp.topMargin = top
        cardContainer.layoutParams = lp

        layoutParams =
            com.android.launcher3.InsettableFrameLayout.LayoutParams(
                com.android.launcher3.InsettableFrameLayout.LayoutParams.MATCH_PARENT,
                com.android.launcher3.InsettableFrameLayout.LayoutParams.MATCH_PARENT
            )
        elevation = 200f
        dragLayer.addView(this)
        isOpen = true
        mIsOpen = true

        cardContainer.pivotX = (20 * density)
        cardContainer.pivotY = 0f
        cardContainer.scaleX = 0.8f
        cardContainer.scaleY = 0.8f
        cardContainer.alpha = 0f

        val scaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.8f, 1f)
        val alpha = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)

        android.animation.AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 200
            interpolator = Interpolators.EMPHASIZED_DECELERATE
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            val dragLayer = launcher.dragLayer
            if (dragLayer != null && dragLayer.isEventOverView(cardContainer, event)) {
                android.util.Log.d("JiggleEditMenuPopup", "Card clicked via onTouchEvent")
                close(true)
                launcher.workspace?.wobbleLayouts(false)
                WallpaperPickerHelper.launchGalleryPicker(launcher)
                return true
            } else {
                close(true)
                return true
            }
        }
        return true
    }

    override fun handleClose(animate: Boolean) {
        if (!isOpen) return
        isOpen = false
        mIsOpen = false
        if (animate) {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f)
            val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0f)
            ObjectAnimator.ofPropertyValuesHolder(cardContainer, scaleX, scaleY, alpha).apply {
                duration = 150
                interpolator = Interpolators.EMPHASIZED_ACCELERATE
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            launcher.dragLayer?.removeView(this@JiggleEditMenuPopup)
                        }
                    }
                )
                start()
            }
        } else {
            launcher.dragLayer?.removeView(this)
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
            if (dragLayer != null && dragLayer.isEventOverView(cardContainer, ev)) {
                android.util.Log.d("JiggleEditMenuPopup", "Card clicked via onControllerTouchEvent")
                close(true)
                launcher.workspace?.wobbleLayouts(false)
                WallpaperPickerHelper.launchGalleryPicker(launcher)
                return true
            } else {
                close(true)
                return true
            }
        }
        return true
    }

    companion object {
        @JvmStatic
        fun showPopup(anchorView: View): JiggleEditMenuPopup {
            val popup = JiggleEditMenuPopup(anchorView.context)
            popup.show(anchorView)
            return popup
        }
    }
}
