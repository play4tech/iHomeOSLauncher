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
import android.graphics.Outline
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import com.example.liquidglass.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry

/**
 * Dedicated [LiquidGlassView] subclass for the live dock preview inside the
 * Customize Glass Effect screen. Renders with iOS continuous curvature squircle geometry.
 */
class PreviewDockGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LiquidGlassView(context, attrs) {

    private val continuousGeometry = ContinuousCornerGeometry()
    private val cornerRadiusDp: Float = 30f

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width <= 0 || view.height <= 0) return
                val radius = cornerRadiusDp * resources.displayMetrics.density
                val path = continuousGeometry.getPath(
                    width = view.width.toFloat(),
                    height = view.height.toFloat(),
                    radius = radius
                )
                outline.setConvexPath(path)
            }
        }
        clipToOutline = true
    }

    override fun draw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.draw(canvas)
            return
        }
        val radius = cornerRadiusDp * resources.displayMetrics.density
        val path = continuousGeometry.getPath(
            width = width.toFloat(),
            height = height.toFloat(),
            radius = radius
        )
        canvas.save()
        canvas.clipPath(path)
        super.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }
}
