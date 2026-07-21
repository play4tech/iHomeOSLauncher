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
import android.graphics.Canvas
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import com.qmdeve.liquidglass.widget.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry

/**
 * A [LiquidGlassView] for individual folder icon preview backgrounds.
 *
 * Mirrors the dock glass effect ([DockGlassView]) but sized to a single icon's
 * preview area. Added as the first child of [com.android.launcher3.folder.FolderIcon]
 * (which is a FrameLayout), and bound to the launcher's glass_source ViewGroup
 * so it samples the wallpaper + workspace content beneath it, frame-by-frame — exactly
 * like the dock does.
 *
 * Corner radius uses the iOS-matched ratio: minOf(w, h) * 0.45f
 * (= ~22.5% of icon size), matching the system squircle for app icons.
 */
class FolderGlassView(context: Context) : LiquidGlassView(context) {

    private val geometry = ContinuousCornerGeometry()

    init {
        clipToOutline = true

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width.toFloat()
                val h = view.height.toFloat()
                if (w <= 0 || h <= 0) return
                val radius = minOf(w, h) / 2f
                val path = android.graphics.Path()
                com.android.launcher3.graphics.IconShape.getShape().addToPath(path, 0f, 0f, radius)
                outline.setConvexPath(path)
            }
        }

        // Glass visual parameters — tuned to match the dock
        setBlurRadius(2f)
        setDispersion(0.2f)
        setTintAlpha(0.25f)
        setTintColorRed(1.0f)
        setTintColorGreen(1.0f)
        setTintColorBlue(1.0f)

        // Disable all interactive behaviors
        setDraggableEnabled(false)
        setElasticEnabled(false)
        setTouchEffectEnabled(false)

        // Shape handled by canvas clipping; skip library's built-in corner rounding
        setCornerRadius(0f)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas)
            return
        }
        val path = android.graphics.Path()
        val radius = minOf(w, h) / 2f
        com.android.launcher3.graphics.IconShape.getShape().addToPath(path, 0f, 0f, radius)
        
        canvas.save()
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
