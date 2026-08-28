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
package foundation.e.bliss.wallpaper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.android.app.animation.Interpolators
import com.android.launcher3.Launcher
import com.android.launcher3.views.BaseDragLayer
import foundation.e.bliss.glass.PopupGlassView
import foundation.e.bliss.utils.runOnMainThread

/**
 * An iOS-styled frosted liquid glass loading HUD shown while a newly selected wallpaper is being
 * decoded, applied to WallpaperManager, and refreshed into the real-time wallpaper proxy.
 */
class WallpaperLoadingOverlay
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {

    private val hudCard: FrameLayout

    init {
        val density = resources.displayMetrics.density
        isClickable = true
        isFocusable = true
        elevation = 1000f
        setBackgroundColor(Color.parseColor("#4D000000"))

        val cardWidth = (210 * density).toInt()
        val cardHeight = (56 * density).toInt()

        // Center HUD Card
        hudCard =
            FrameLayout(context).apply {
                layoutParams =
                    LayoutParams(cardWidth, cardHeight).apply { gravity = Gravity.CENTER }
            }

        // Liquid glass background for HUD
        val glassBg =
            PopupGlassView(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight)
                setPopupRadius(18f * density)
                setBlurRadius(15f)
                setTintAlpha(0.5f)
            }
        hudCard.addView(glassBg)

        // Dark frosted backing for maximum contrast on any background
        val fallbackBacking =
            View(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight)
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 18f * density
                        setColor(Color.parseColor("#33000000"))
                    }
            }
        hudCard.addView(fallbackBacking)

        // Content row (Spinner + Text)
        val contentRow =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }

        val progressBar =
            ProgressBar(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
                        .apply { marginEnd = (12 * density).toInt() }
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            }
        contentRow.addView(progressBar)

        val textView =
            TextView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                text = "Setting Wallpaper..."
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        contentRow.addView(textView)

        hudCard.addView(contentRow)
        addView(hudCard)
    }

    private var showAnimator: ObjectAnimator? = null
    private var dismissAnimator: ObjectAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            for (i in 0 until hudCard.childCount) {
                (hudCard.getChildAt(i) as? PopupGlassView)?.bindToWallpaper()
            }
        }
    }

    companion object {
        private var currentOverlay: WallpaperLoadingOverlay? = null

        @JvmStatic
        fun show(launcher: Launcher) {
            runOnMainThread {
                val dragLayer = launcher.dragLayer ?: return@runOnMainThread

                // Cleanly remove any existing overlay
                currentOverlay?.let { old ->
                    old.showAnimator?.cancel()
                    old.dismissAnimator?.cancel()
                    (old.parent as? ViewGroup)?.removeView(old)
                }
                currentOverlay = null

                val overlay = WallpaperLoadingOverlay(launcher)
                overlay.layoutParams =
                    BaseDragLayer.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                overlay.alpha = 0f
                dragLayer.addView(overlay)
                currentOverlay = overlay

                overlay.showAnimator =
                    ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f).apply {
                        duration = 200
                        interpolator = Interpolators.EMPHASIZED_DECELERATE
                        start()
                    }
            }
        }

        @JvmStatic
        fun dismiss() {
            runOnMainThread {
                val overlay = currentOverlay ?: return@runOnMainThread
                currentOverlay = null
                overlay.showAnimator?.cancel()
                overlay.showAnimator = null

                if (overlay.alpha <= 0.05f) {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                    return@runOnMainThread
                }

                overlay.dismissAnimator =
                    ObjectAnimator.ofFloat(overlay, View.ALPHA, overlay.alpha, 0f).apply {
                        duration = 200
                        interpolator = Interpolators.EMPHASIZED_ACCELERATE
                        addListener(
                            object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                                }
                            }
                        )
                        start()
                    }

                overlay.postDelayed({ (overlay.parent as? ViewGroup)?.removeView(overlay) }, 250)
            }
        }
    }
}
