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
) : LiquidGlassView(context, attrs), BorderedGlassView {

    private val continuousGeometry = ContinuousCornerGeometry()
    private val cornerRadiusDp: Float = 30f

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override var borderEnabled: Boolean = GlassConfig.DEFAULT_BORDER_ENABLED
    override var borderWidthPx: Float = GlassConfig.DEFAULT_BORDER_WIDTH * context.resources.displayMetrics.density
    override var borderOpacity: Float = GlassConfig.DEFAULT_BORDER_OPACITY
    override var borderFade: Float = GlassConfig.DEFAULT_BORDER_FADE

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

        if (borderEnabled && borderWidthPx > 0f && borderOpacity > 0f) {
            GlassConfig.drawBorder(
                canvas, path, borderPaint,
                borderWidthPx, borderOpacity, borderFade,
                height.toFloat()
            )
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }
}
