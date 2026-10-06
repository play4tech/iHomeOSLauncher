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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.android.launcher3.R
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * A [LiquidGlassView] for individual folder icon preview backgrounds.
 *
 * Sized to a single icon's preview area. Added as the first child of
 * [com.android.launcher3.folder.FolderIcon] (which is a FrameLayout), and bound to the launcher's
 * glass_wallpaper_proxy or glass_source View so it samples the wallpaper.
 *
 * Corner radius uses the iOS-matched ratio: minOf(w, h) * 0.225f, matching the system squircle
 * for app icons.
 */
class FolderGlassView(context: Context) : LiquidGlassView(context), BorderedGlassView {

    private val cachedPath = android.graphics.Path()
    private var cachedW = 0f
    private var cachedH = 0f

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override var borderEnabled: Boolean = GlassConfig.DEFAULT_BORDER_ENABLED
    override var borderWidthPx: Float = GlassConfig.DEFAULT_BORDER_WIDTH * context.resources.displayMetrics.density
    override var borderOpacity: Float = GlassConfig.DEFAULT_BORDER_OPACITY
    override var borderFade: Float = GlassConfig.DEFAULT_BORDER_FADE

    private fun updatePath(w: Float, h: Float): android.graphics.Path {
        if (w == cachedW && h == cachedH && !cachedPath.isEmpty) {
            return cachedPath
        }
        cachedW = w
        cachedH = h
        cachedPath.rewind()
        if (w <= 0f || h <= 0f) return cachedPath
        val radius = minOf(w, h) / 2f
        com.android.launcher3.graphics.IconShape.getShape().addToPath(cachedPath, 0f, 0f, radius)
        return cachedPath
    }

    init {
        clipToOutline = true

        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val w = view.width.toFloat()
                    val h = view.height.toFloat()
                    if (w <= 0 || h <= 0) return
                    val path = updatePath(w, h)
                    outline.setConvexPath(path)
                }
            }

        // Standard master glass parameters from GlassConfig (matches App Library category effect)
        GlassConfig.applyDefaultConfig(this)
        enableDynamicBackground = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backdropSource = null
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

    fun bind(source: View?) {
        backdropSource = source
    }

    fun unbind() {
        backdropSource = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never consume touches so folder tap/long-press/drag passes to FolderIcon
        return false
    }

    override fun draw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) {
            super.draw(canvas)
            return
        }
        val path = updatePath(w, h)
        canvas.save()
        canvas.clipPath(path)
        super.draw(canvas)

        if (borderEnabled && borderWidthPx > 0f && borderOpacity > 0f) {
            GlassConfig.drawBorder(
                canvas, path, borderPaint,
                borderWidthPx, borderOpacity, borderFade,
                h
            )
        }

        canvas.restore()
    }
}
