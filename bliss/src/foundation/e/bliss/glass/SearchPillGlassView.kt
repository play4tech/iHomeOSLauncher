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
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.android.launcher3.R
import com.example.liquidglass.GlassAccessibilityMode
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * Liquid glass refraction surface for the Home Screen search pill on the page indicator.
 * Provides backdrop blur and edge refraction identical to the dock.
 */
class SearchPillGlassView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LiquidGlassView(context, attrs), BorderedGlassView {

    private val clipPath = Path()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    override var borderEnabled: Boolean = GlassConfig.DEFAULT_BORDER_ENABLED
    override var borderWidthPx: Float = GlassConfig.DEFAULT_BORDER_WIDTH * context.resources.displayMetrics.density
    override var borderOpacity: Float = GlassConfig.DEFAULT_BORDER_OPACITY
    override var borderFade: Float = GlassConfig.DEFAULT_BORDER_FADE

    init {
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (view.width <= 0 || view.height <= 0) return
                    val radius = view.height / 2f
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        clipToOutline = true

        GlassConfig.applyDefaultConfig(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            cornerRadius = h / 2f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isClickable = true
        isFocusable = false
        isEnabled = true
        if (height > 0) {
            cornerRadius = height / 2f
        }
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
        val radius = height / 2f
        clipPath.reset()
        clipPath.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        super.draw(canvas)

        if (borderEnabled && borderWidthPx > 0f && borderOpacity > 0f) {
            GlassConfig.drawBorder(
                canvas, clipPath, borderPaint,
                borderWidthPx, borderOpacity, borderFade,
                height.toFloat()
            )
        }

        canvas.restore()
    }

    private var touchDown = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDown = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchDown) return false
                val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                if (event.x < -slop || event.x > width + slop || event.y < -slop || event.y > height + slop) {
                    touchDown = false
                    return false
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (touchDown) {
                    touchDown = false
                    if (event.x >= 0 && event.x <= width && event.y >= 0 && event.y <= height) {
                        val launcher = com.android.launcher3.Launcher.getLauncher(context)
                        if (launcher != null && launcher.workspace != null && !launcher.workspace.isWobbling) {
                            com.android.launcher3.applibrary.view.SpotlightSearchOverlay.show(launcher)
                            playSoundEffect(android.view.SoundEffectConstants.CLICK)
                            return true
                        }
                    }
                }
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                touchDown = false
                return false
            }
        }
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
