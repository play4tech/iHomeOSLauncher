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

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.Insettable
import com.android.launcher3.views.ActivityContext
import com.qmdeve.liquidglass.widget.LiquidGlassView

/**
 * A [LiquidGlassView] that implements [Insettable] so it receives insets automatically
 * from [com.android.launcher3.InsettableFrameLayout]'s generic dispatch loop —
 * no explicit wiring in Launcher.java is needed.
 *
 * Its [setInsets] mirrors [com.android.launcher3.Hotseat.setInsets] exactly, so this
 * panel always occupies the same rect as the dock bar.
 *
 * Sits as a direct child of DragLayer, between `glass_source` and the hotseat include,
 * so it renders above Workspace/wallpaper but below the icon layer.
 */
class DockGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LiquidGlassView(context, attrs), Insettable {

    override fun setInsets(insets: Rect) {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        val grid = ActivityContext.lookupContext<com.android.launcher3.Launcher>(context).deviceProfile

        if (grid.isVerticalBarLayout) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            if (grid.isSeascape) {
                lp.gravity = Gravity.LEFT
                lp.width = grid.hotseatBarSizePx + insets.left
            } else {
                lp.gravity = Gravity.RIGHT
                lp.width = grid.hotseatBarSizePx + insets.right
            }
        } else {
            lp.gravity = Gravity.BOTTOM
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = grid.hotseatBarSizePx
        }
        layoutParams = lp
    }
}
