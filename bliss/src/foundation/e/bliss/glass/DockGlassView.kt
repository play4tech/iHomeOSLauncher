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

    override var borderEnabled: Boolean = GlassConfig.DEFAULT_BORDER_ENABLED
    override var borderWidthPx: Float = GlassConfig.DEFAULT_BORDER_WIDTH * context.resources.displayMetrics.density
    override var borderOpacity: Float = GlassConfig.DEFAULT_BORDER_OPACITY
    override var borderFade: Float = GlassConfig.DEFAULT_BORDER_FADE

    init {
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val grid =
                        ActivityContext.lookupContext<com.android.launcher3.Launcher>(context)
                            .deviceProfile
                    if (grid.isFloatingDockEnabled && grid.hotseatGeometry != null) {
                        val path =
                            continuousGeometry.getPath(
                                width = view.width.toFloat(),
                                height = view.height.toFloat(),
                                radius = grid.hotseatGeometry.cornerRadius
                            )
                        outline.setConvexPath(path)
                    } else {
                        outline.setRect(0, 0, view.width, view.height)
                    }
                }
            }
        clipToOutline = true
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
        } else if (grid.isFloatingDockEnabled && grid.hotseatGeometry != null) {
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.width = grid.hotseatGeometry.bounds.width()
            lp.height = grid.hotseatGeometry.bounds.height()
            lp.bottomMargin = insets.bottom + grid.hotseatBarBottomSpacePx
            cornerRadius = 0f
        } else {
            lp.gravity = Gravity.BOTTOM
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = grid.hotseatBarSizePx
            lp.bottomMargin = 0
        }
        layoutParams = lp
    }

    override fun draw(canvas: Canvas) {
        val grid =
            ActivityContext.lookupContext<com.android.launcher3.Launcher>(context).deviceProfile
        if (grid.isFloatingDockEnabled && grid.hotseatGeometry != null) {
            val path =
                continuousGeometry.getPath(
                    width = width.toFloat(),
                    height = height.toFloat(),
                    radius = grid.hotseatGeometry.cornerRadius
                )
            canvas.save()
            canvas.clipPath(path)
            super.draw(canvas)

            if (borderEnabled && borderWidthPx > 0f && borderOpacity > 0f) {
                GlassConfig.drawBorder(
                    canvas, path, borderPaint,
                    borderWidthPx, borderOpacity, borderFade,
                    height.toFloat()
                )
            }

            canvas.restore()
        } else {
            super.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never consume touches so hotseat icons and workspace gestures operate without interference
        return false
    }
}
