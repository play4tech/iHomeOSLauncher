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
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.R
import com.example.liquidglass.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry

/**
 * A dedicated [LiquidGlassView] pill for the Jiggle mode "Edit" and "Done" buttons.
 * Shares the exact same AGSL shader pipeline, wallpaper proxy backdrop, refraction falloff,
 * dispersion, and continuous squircle/pill border geometry as [DockGlassView].
 */
class JiggleButtonGlassView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LiquidGlassView(context, attrs), BorderedGlassView {

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
        // Do NOT clip to outline. The AGSL shader performs subpixel anti-aliasing
        // and renders specular caustics at the rim, exactly like DockGlassView.
        clipToOutline = false
        isClickable = false
        isFocusable = false
        isEnabled = false

        GlassConfig.applyDefaultConfig(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlassConfig.registerView(this)
        bindToWallpaper()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        GlassConfig.unregisterView(this)
        backdropSource = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            cornerRadius = h / 2f
        }
    }

    fun bindToWallpaper() {
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

    override fun draw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.draw(canvas)
            return
        }

        val targetRadius = height / 2f
        if (cornerRadius != targetRadius) {
            cornerRadius = targetRadius
        }

        // Render full AGSL shader without clipPath to preserve optical caustics & specular rim
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never consume touches; touch is handled by container
        return false
    }
}
