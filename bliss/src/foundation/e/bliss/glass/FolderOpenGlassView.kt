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
) : LiquidGlassView(context, attrs), BorderedGlassView {

    private val geometry = ContinuousCornerGeometry()
    private val cornerRadiusPx = 38f * context.resources.displayMetrics.density
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
        cornerRadius = cornerRadiusPx
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlassConfig.registerView(this)
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
        GlassConfig.unregisterView(this)
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

        if (borderEnabled && borderWidthPx > 0f && borderOpacity > 0f) {
            GlassConfig.drawBorder(
                canvas, path, borderPaint,
                borderWidthPx, borderOpacity, borderFade,
                borderPosition, borderFadeSpread, false,
                height.toFloat()
            )
        }

        if (whiteBorderEnabled && whiteBorderWidthPx > 0f && whiteBorderOpacity > 0f) {
            GlassConfig.drawBorder(
                canvas, path, whiteBorderPaint,
                whiteBorderWidthPx, whiteBorderOpacity, whiteBorderFade,
                whiteBorderPosition, whiteBorderFadeSpread, true,
                height.toFloat()
            )
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never consume touches so folder icons, pagination, and touch gestures operate without interference
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
}
