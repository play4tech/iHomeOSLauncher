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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.MotionEvent
import android.widget.FrameLayout
import com.android.launcher3.Insettable
import com.android.launcher3.views.ActivityContext
import com.example.liquidglass.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry

/**
 * A [LiquidGlassView] that implements [Insettable] so it receives insets automatically from
 * [com.android.launcher3.InsettableFrameLayout]'s generic dispatch loop — no explicit wiring in
 * Launcher.java is needed.
 *
 * Its [setInsets] mirrors [com.android.launcher3.Hotseat.setInsets] exactly, so this panel always
 * occupies the same rect as the dock bar.
 *
 * Sits as a direct child of DragLayer, between `glass_source` and the hotseat include, so it
 * renders above Workspace/wallpaper but below the icon layer.
 */
class DockGlassView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LiquidGlassView(context, attrs), Insettable, BorderedGlassView {

    private val continuousGeometry = ContinuousCornerGeometry()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override var borderEnabled: Boolean = GlassConfig.DEFAULT_BORDER_ENABLED
    override var borderWidthPx: Float = GlassConfig.DEFAULT_BORDER_WIDTH * context.resources.displayMetrics.density
    override var borderOpacity: Float = GlassConfig.DEFAULT_BORDER_OPACITY
    override var borderFade: Float = GlassConfig.DEFAULT_BORDER_FADE
    override var borderPosition: Int = GlassConfig.DEFAULT_BORDER_POSITION
    override var borderFadeSpread: Float = GlassConfig.DEFAULT_BORDER_FADE_SPREAD

    override var whiteBorderEnabled: Boolean = GlassConfig.DEFAULT_WHITE_BORDER_ENABLED
    override var whiteBorderWidthPx: Float = GlassConfig.DEFAULT_WHITE_BORDER_WIDTH * context.resources.displayMetrics.density
    override var whiteBorderOpacity: Float = GlassConfig.DEFAULT_WHITE_BORDER_OPACITY
    override var whiteBorderFade: Float = GlassConfig.DEFAULT_WHITE_BORDER_FADE
    override var whiteBorderPosition: Int = GlassConfig.DEFAULT_WHITE_BORDER_POSITION
    override var whiteBorderFadeSpread: Float = GlassConfig.DEFAULT_WHITE_BORDER_FADE_SPREAD

    init {
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val grid =
                        ActivityContext.lookupContext<com.android.launcher3.Launcher>(context)
                            .deviceProfile
                    if (grid.isFloatingDockEnabled && grid.hotseatGeometry != null) {
                        outline.setRoundRect(
                            0,
                            0,
                            view.width,
                            view.height,
                            grid.hotseatGeometry.cornerRadius
                        )
                    } else {
                        outline.setRect(0, 0, view.width, view.height)
                    }
                }
            }
        // Do NOT clip to outline. The AGSL shader performs subpixel anti-aliasing
        // (cov = clamp(0.5 - d / 1.5, 0.0, 1.0)) and renders specular caustics at the rim.
        // Hardware outline clipping introduces jagged 1-bit edges and cuts off the specular glow.
        clipToOutline = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlassConfig.registerView(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        GlassConfig.unregisterView(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val grid =
            ActivityContext.lookupContext<com.android.launcher3.Launcher>(context).deviceProfile
        if (grid.isFloatingDockEnabled && grid.hotseatGeometry != null) {
            cornerRadius = grid.hotseatGeometry.cornerRadius
        }
    }

    override fun setInsets(insets: Rect) {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        val grid =
            ActivityContext.lookupContext<com.android.launcher3.Launcher>(context).deviceProfile

        if (grid.isVerticalBarLayout) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            if (grid.isSeascape) {
                lp.gravity = Gravity.LEFT
                lp.width = grid.hotseatBarSizePx + insets.left
            } else {
                lp.gravity = Gravity.RIGHT
                lp.width = grid.hotseatBarSizePx + insets.right
            }
            cornerRadius = 0f
        } else if (grid.isFloatingDockEnabled && grid.hotseatGeometry != null) {
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.width = grid.hotseatGeometry.bounds.width()
            lp.height = grid.hotseatGeometry.bounds.height()
            lp.bottomMargin = insets.bottom + grid.hotseatBarBottomSpacePx
            cornerRadius = grid.hotseatGeometry.cornerRadius
        } else {
            lp.gravity = Gravity.BOTTOM
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = grid.hotseatBarSizePx
            lp.bottomMargin = 0
            cornerRadius = 0f
        }
        layoutParams = lp
    }

    override fun draw(canvas: Canvas) {
        val grid =
            ActivityContext.lookupContext<com.android.launcher3.Launcher>(context).deviceProfile
        if (grid.isFloatingDockEnabled && grid.hotseatGeometry != null) {
            val targetRadius = grid.hotseatGeometry.cornerRadius
            if (cornerRadius != targetRadius) {
                cornerRadius = targetRadius
            }

            // LiquidGlassView's API 33+ AGSL shader computes the signed distance field (SDF)
            // for the capsule shape, surface normal gradient, inverse-power refraction falloff,
            // chromatic dispersion, and directional specular rim highlights with 1.5px subpixel
            // anti-aliasing. We do NOT clip the canvas with clipPath() because that would slice
            // off the optical rim and caustics.
            super.draw(canvas)

            if (borderEnabled && borderWidthPx > 0f && borderOpacity > 0f) {
                val path = continuousGeometry.getPath(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    radius = targetRadius
                )
                GlassConfig.drawBorder(
                    canvas, path, borderPaint,
                    borderWidthPx, borderOpacity, borderFade,
                    borderPosition, borderFadeSpread, false,
                    height.toFloat()
                )
            }

            if (whiteBorderEnabled && whiteBorderWidthPx > 0f && whiteBorderOpacity > 0f) {
                val path = continuousGeometry.getPath(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    radius = targetRadius
                )
                GlassConfig.drawBorder(
                    canvas, path, whiteBorderPaint,
                    whiteBorderWidthPx, whiteBorderOpacity, whiteBorderFade,
                    whiteBorderPosition, whiteBorderFadeSpread, true,
                    height.toFloat()
                )
            }
        } else {
            super.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never consume touches so hotseat icons and workspace gestures operate without interference
        return false
    }
}
