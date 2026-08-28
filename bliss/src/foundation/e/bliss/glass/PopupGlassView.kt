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
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.android.launcher3.R
import com.qmdeve.liquidglass.widget.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

class PopupGlassView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LiquidGlassView(context, attrs, defStyleAttr) {

    private val geometry = ContinuousCornerGeometry()

    // Popup container radius is typically smaller than folder, match it.
    private var cornerRadiusPx = 24f * context.resources.displayMetrics.density

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
        setCornerRadius(0f)

        // Glass visual parameters for the popup
        setBlurRadius(15f)
        setDispersion(0.2f)
        setTintAlpha(0.6f)
        setTintColorRed(1.0f)
        setTintColorGreen(1.0f)
        setTintColorBlue(1.0f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isClickable = false
        isFocusable = false
        isEnabled = false
        bind(findWallpaperProxy())
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
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
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    private fun findWallpaperProxy(): ViewGroup? {
        val root = rootView
        if (root != null) {
            val proxy = root.findViewById<View>(R.id.glass_source)
            if (proxy is ViewGroup) return proxy
            val proxy2 = root.findViewById<View>(R.id.glass_wallpaper_proxy)
            if (proxy2 is ViewGroup) return proxy2
        }
        var p = parent
        while (p is ViewGroup) {
            val proxy = p.findViewById<View>(R.id.glass_source)
            if (proxy is ViewGroup) {
                return proxy
            }
            p = p.parent
        }
        return null
    }

    fun bindToWallpaper() {
        val proxy = findWallpaperProxy()
        if (proxy != null) {
            bind(proxy)
        }
    }

    fun setPopupRadius(radius: Float) {
        cornerRadiusPx = radius
        invalidateOutline()
        invalidate()
    }
}
