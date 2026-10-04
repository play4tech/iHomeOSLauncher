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
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.android.launcher3.R
import com.example.liquidglass.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

class FolderOpenGlassView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LiquidGlassView(context, attrs) {

    private val geometry = ContinuousCornerGeometry()
    private val cornerRadiusPx = 38f * context.resources.displayMetrics.density

    init {
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (view.width <= 0 || view.height <= 0) return
                    val path =
                        geometry.getPath(
                            width = view.width.toFloat(),
                            height = view.height.toFloat(),
                            radius = cornerRadiusPx,
                            smoothing = GlassShapeSpec.IOS_SMOOTHING
                        )
                    outline.setConvexPath(path)
                }
            }
        clipToOutline = true
        // Standard master glass parameters from GlassConfig
        GlassConfig.applyDefaultConfig(this)
        material = com.example.liquidglass.GlassMaterial.REGULAR
        refractionHeight = 160f
        bevelWidth = 48f
        refractionFalloff = 2f
        dispersionStrength = 0.12f
        enableEdgeHighlight = false
        enableSensorHighlight = false
        enableAdaptiveTint = true
        blurAmount = 0.12f
        setGlassTint(android.graphics.Color.WHITE, 0.22f)
        enableDynamicBackground = true
        cornerRadius = cornerRadiusPx
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isClickable = false
        isFocusable = false
        isEnabled = false
        val proxy = findWallpaperProxy()
        if (proxy != null) {
            backdropSource = proxy
        } else {
            post {
                if (backdropSource == null) {
                    backdropSource = findWallpaperProxy()
                }
            }
        }
        refreshAccessibilityState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backdropSource = null
    }

    override fun draw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.draw(canvas)
            return
        }
        val path =
            geometry.getPath(
                width.toFloat(),
                height.toFloat(),
                cornerRadiusPx,
                GlassShapeSpec.IOS_SMOOTHING
            )
        canvas.save()
        canvas.clipPath(path)
        super.draw(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never consume touches so folder icons, pagination, and touch gestures operate without interference
        return false
    }

    private fun findWallpaperProxy(): View? {
        val root = rootView
        if (root != null) {
            val proxy = root.findViewById<View>(R.id.glass_wallpaper_proxy)
            if (proxy != null) return proxy
            val source = root.findViewById<View>(R.id.glass_source)
            if (source != null) return source
        }
        var p = parent
        while (p is ViewGroup) {
            val proxy = p.findViewById<View>(R.id.glass_wallpaper_proxy)
            if (proxy != null) return proxy
            val source = p.findViewById<View>(R.id.glass_source)
            if (source != null) return source
            p = p.parent
        }
        return null
    }
}
