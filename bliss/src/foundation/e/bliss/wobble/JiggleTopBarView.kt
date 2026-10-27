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
import android.view.MotionEvent
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
    private val editTextView: TextView
    private val doneTextView: TextView
    private val contentRow: FrameLayout
    private var isShowing = false
    private var activePressedView: View? = null
    private var currentAnimator: ObjectAnimator? = null

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false
        val density = resources.displayMetrics.density
        setPadding(0, 0, 0, (12 * density).toInt())

        contentRow =
            FrameLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                clipChildren = false
                clipToPadding = false
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
            foundation.e.bliss.glass.JiggleButtonGlassView(context).apply {
                layoutParams = FrameLayout.LayoutParams(editWidth, buttonHeight)
            }
        editButtonContainer.addView(editGlassBg)

        editTextView =
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

        val onEditClick = View.OnClickListener {
            JiggleEditMenuPopup.showPopup(editButtonContainer)
        }
        editButtonContainer.setOnClickListener(onEditClick)
        editTextView.setOnClickListener(onEditClick)
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
            foundation.e.bliss.glass.JiggleButtonGlassView(context).apply {
                layoutParams = FrameLayout.LayoutParams(doneWidth, buttonHeight)
            }
        doneButtonContainer.addView(doneGlassBg)

        doneTextView =
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

        val onDoneClick = View.OnClickListener {
            val launcher = Launcher.getLauncher(context)
            launcher.workspace?.wobbleLayouts(false)
        }
        doneButtonContainer.setOnClickListener(onDoneClick)
        doneTextView.setOnClickListener(onDoneClick)
        contentRow.addView(doneButtonContainer)

        addView(contentRow)
        elevation = 100f
    }

    override fun setInsets(insets: Rect) {
        val density = resources.displayMetrics.density
        val topMargin = maxOf((12 * density).toInt(), insets.top + (6 * density).toInt())
        val lp = contentRow.layoutParams as? LayoutParams ?: return
        if (lp.topMargin != topMargin) {
            lp.topMargin = topMargin
            contentRow.layoutParams = lp
        }
    }

    private fun getExpandedHitRect(child: View, horizontalPadDp: Float, verticalPadDp: Float): Rect {
        val rect = Rect()
        child.getHitRect(rect)
        rect.offset(contentRow.left, contentRow.top)
        val density = resources.displayMetrics.density
        val hPad = (horizontalPadDp * density).toInt()
        val vPad = (verticalPadDp * density).toInt()
        rect.inset(-hPad, -vPad)
        return rect
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (visibility != VISIBLE || alpha < 0.1f) return false

        val x = ev.x.toInt()
        val y = ev.y.toInt()

        val editHitRect = getExpandedHitRect(editButtonContainer, 24f, 24f)
        val doneHitRect = getExpandedHitRect(doneButtonContainer, 24f, 24f)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (editHitRect.contains(x, y)) {
                    activePressedView = editButtonContainer
                    animateButtonPress(editButtonContainer, true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                } else if (doneHitRect.contains(x, y)) {
                    activePressedView = doneButtonContainer
                    animateButtonPress(doneButtonContainer, true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val pressed = activePressedView
                if (pressed != null) {
                    val hitRect = if (pressed == editButtonContainer) editHitRect else doneHitRect
                    val isInside = hitRect.contains(x, y)
                    animateButtonPress(pressed, isInside)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val pressed = activePressedView
                activePressedView = null
                if (pressed != null) {
                    animateButtonPress(pressed, false)
                    val hitRect = if (pressed == editButtonContainer) editHitRect else doneHitRect
                    if (hitRect.contains(x, y)) {
                        pressed.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                        if (pressed == editButtonContainer) {
                            JiggleEditMenuPopup.showPopup(editButtonContainer)
                        } else {
                            val launcher = Launcher.getLauncher(context)
                            launcher.workspace?.wobbleLayouts(false)
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                val pressed = activePressedView
                activePressedView = null
                if (pressed != null) {
                    animateButtonPress(pressed, false)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun animateButtonPress(view: View, pressed: Boolean) {
        val targetScale = if (pressed) 0.92f else 1.0f
        val targetAlpha = if (pressed) 0.65f else 1.0f
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .alpha(targetAlpha)
            .setDuration(100)
            .start()
    }

    fun show() {
        if (isShowing) return
        isShowing = true
        currentAnimator?.cancel()
        currentAnimator = null
        visibility = VISIBLE
        bringToFront()
        alpha = 0f
        val density = resources.displayMetrics.density
        translationY = -15f * density

        val launcher = try { Launcher.getLauncher(context) } catch (e: Exception) { null }
        val statusBarHeight = launcher?.deviceProfile?.insets?.top ?: 0
        val topMargin = maxOf((12 * density).toInt(), statusBarHeight + (6 * density).toInt())
        val lp = contentRow.layoutParams as? LayoutParams
        if (lp != null && lp.topMargin != topMargin) {
            lp.topMargin = topMargin
            contentRow.layoutParams = lp
        }

        editButtonContainer.scaleX = 1f
        editButtonContainer.scaleY = 1f
        editButtonContainer.alpha = 1f
        doneButtonContainer.scaleX = 1f
        doneButtonContainer.scaleY = 1f
        doneButtonContainer.alpha = 1f

        // Trigger wallpaper binding on glass views
        post {
            for (i in 0 until editButtonContainer.childCount) {
                (editButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                    ?.bindToWallpaper()
            }
            for (i in 0 until doneButtonContainer.childCount) {
                (doneButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                    ?.bindToWallpaper()
            }
        }

        val alphaAnim = PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        val transAnim = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -15f * density, 0f)

        val anim = ObjectAnimator.ofPropertyValuesHolder(this, alphaAnim, transAnim).apply {
            duration = 250
            interpolator = Interpolators.EMPHASIZED_DECELERATE
            addUpdateListener { invalidateGlassButtons() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentAnimator === animation) {
                        currentAnimator = null
                    }
                    invalidateGlassButtons()
                }
            })
            start()
        }
        currentAnimator = anim
    }

    fun invalidateGlassButtons() {
        for (i in 0 until editButtonContainer.childCount) {
            (editButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                ?.invalidate()
        }
        for (i in 0 until doneButtonContainer.childCount) {
            (doneButtonContainer.getChildAt(i) as? foundation.e.bliss.glass.JiggleButtonGlassView)
                ?.invalidate()
        }
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        activePressedView = null
        currentAnimator?.cancel()
        currentAnimator = null
        val density = resources.displayMetrics.density

        val alphaAnim = PropertyValuesHolder.ofFloat(View.ALPHA, alpha, 0f)
        val transAnim =
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, translationY, -15f * density)

        val anim = ObjectAnimator.ofPropertyValuesHolder(this, alphaAnim, transAnim).apply {
            duration = 200
            interpolator = Interpolators.EMPHASIZED_ACCELERATE
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (currentAnimator === animation) {
                            currentAnimator = null
                        }
                        if (!isShowing) {
                            visibility = GONE
                            editButtonContainer.scaleX = 1f
                            editButtonContainer.scaleY = 1f
                            editButtonContainer.alpha = 1f
                            doneButtonContainer.scaleX = 1f
                            doneButtonContainer.scaleY = 1f
                            doneButtonContainer.alpha = 1f
                        }
                    }
                }
            )
            start()
        }
        currentAnimator = anim
    }
}
