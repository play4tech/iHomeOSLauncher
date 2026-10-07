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
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.android.launcher3.R
import com.example.liquidglass.GlassAccessibilityMode
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

class PopupGlassView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LiquidGlassView(context, attrs) {

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

        // Glass visual parameters for the popup - Frosted Liquid Glass matching master GlassConfig
        GlassConfig.applyDefaultConfig(this)
        cornerRadius = cornerRadiusPx
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlassConfig.registerView(this)
        isClickable = false
        isFocusable = false
        isEnabled = false
        if (backdropSource == null) {
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
        }
        refreshAccessibilityState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        GlassConfig.unregisterView(this)
        backdropSource = null
    }

    private val whiteTintPaint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = Color.argb(82, 255, 255, 255)
        }

    private val rimPaint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.0f * context.resources.displayMetrics.density
            color = Color.argb(65, 255, 255, 255)
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
        canvas.drawPath(path, whiteTintPaint)
        canvas.drawPath(path, rimPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never consume touches so popup menu items, jiggle edit buttons, and dialogs are interactive
        return false
    }

    private fun findWallpaperProxy(): View? {
        val root = rootView
        if (root != null) {
            val source = root.findViewById<View>(R.id.glass_source)
            if (source != null) return source
            val proxy = root.findViewById<View>(R.id.glass_wallpaper_proxy)
            if (proxy != null) return proxy
        }
        var p = parent
        while (p is ViewGroup) {
            val source = p.findViewById<View>(R.id.glass_source)
            if (source != null) return source
            val proxy = p.findViewById<View>(R.id.glass_wallpaper_proxy)
            if (proxy != null) return proxy
            p = p.parent
        }
        return null
    }

    fun bindToWallpaper() {
        val proxy = findWallpaperProxy()
        if (proxy != null) {
            backdropSource = proxy
        }
    }

    fun setPopupRadius(radius: Float) {
        cornerRadiusPx = radius
        cornerRadius = radius
        invalidateOutline()
        invalidate()
    }

    fun setBlurRadius(radius: Float) {
        blurAmount = (radius / 100f).coerceIn(0.01f, 1.0f)
    }

    fun setTintAlpha(alpha: Float) {
        setGlassTint(Color.WHITE, alpha)
    }
}
