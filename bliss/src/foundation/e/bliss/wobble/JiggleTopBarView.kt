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
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher

class JiggleTopBarView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr), Insettable {

    private val editButtonContainer: FrameLayout
    private val doneButtonContainer: FrameLayout
    private val contentRow: FrameLayout
    private var isShowing = false

    init {
        visibility = GONE
        val density = resources.displayMetrics.density

        contentRow =
            FrameLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
            }

        // --- Edit Button (Liquid Glass Pill) ---
        val editWidth = (62 * density).toInt()
        val buttonHeight = (32 * density).toInt()

        editButtonContainer =
            FrameLayout(context).apply {
                layoutParams =
                    LayoutParams(editWidth, buttonHeight).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                isClickable = true
                isFocusable = true
            }

        val editGlassBg =
            foundation.e.bliss.glass.PopupGlassView(context).apply {
                layoutParams = FrameLayout.LayoutParams(editWidth, buttonHeight)
                setPopupRadius(16f * density)
                setBlurRadius(10f)
                setTintAlpha(0.35f)
            }
        editButtonContainer.addView(editGlassBg)

        val editTextView =
            TextView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                text = "Edit"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
            }
        editButtonContainer.addView(editTextView)

        editButtonContainer.setOnClickListener {
            JiggleEditMenuPopup.showPopup(editButtonContainer)
        }
        contentRow.addView(editButtonContainer)

        // --- Done Button (Liquid Glass Pill) ---
        val doneWidth = (68 * density).toInt()

        doneButtonContainer =
            FrameLayout(context).apply {
                layoutParams =
                    LayoutParams(doneWidth, buttonHeight).apply {
                        gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    }
                isClickable = true
                isFocusable = true
            }

        val doneGlassBg =
            foundation.e.bliss.glass.PopupGlassView(context).apply {
                layoutParams = FrameLayout.LayoutParams(doneWidth, buttonHeight)
                setPopupRadius(16f * density)
                setBlurRadius(10f)
                setTintAlpha(0.45f)
            }
        doneButtonContainer.addView(doneGlassBg)

        val doneTextView =
            TextView(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                text = "Done"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
            }
        doneButtonContainer.addView(doneTextView)

        doneButtonContainer.setOnClickListener {
            val launcher = Launcher.getLauncher(context)
            launcher.workspace?.wobbleLayouts(false)
        }
        contentRow.addView(doneButtonContainer)

        addView(contentRow)
        elevation = 50f
    }

    override fun setInsets(insets: Rect) {
        val density = resources.displayMetrics.density
        val topMargin = maxOf((6 * density).toInt(), insets.top)
        val lp = contentRow.layoutParams as LayoutParams
        if (lp.topMargin != topMargin) {
            lp.topMargin = topMargin
            contentRow.layoutParams = lp
        }
    }

    fun show() {
        if (isShowing) return
        isShowing = true
        visibility = VISIBLE
        alpha = 0f
        val density = resources.displayMetrics.density
        translationY = -15f * density

        // Trigger wallpaper binding on glass views
        post {
            for (i in 0 until editButtonContainer.childCount) {
                (editButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.PopupGlassView)
                    ?.bindToWallpaper()
            }
            for (i in 0 until doneButtonContainer.childCount) {
                (doneButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.PopupGlassView)
                    ?.bindToWallpaper()
            }
        }

        val alphaAnim = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        val transAnim = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -15f * density, 0f)

        ObjectAnimator.ofPropertyValuesHolder(this, alphaAnim, transAnim).apply {
            duration = 250
            interpolator = Interpolators.EMPHASIZED_DECELERATE
            start()
        }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        val density = resources.displayMetrics.density

        val alphaAnim = PropertyValuesHolder.ofFloat(View.ALPHA, alpha, 0f)
        val transAnim =
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, translationY, -15f * density)

        ObjectAnimator.ofPropertyValuesHolder(this, alphaAnim, transAnim).apply {
            duration = 200
            interpolator = Interpolators.EMPHASIZED_ACCELERATE
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = GONE
                    }
                }
            )
            start()
        }
    }
}
