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
package foundation.e.bliss.blur

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import com.android.launcher3.R
import foundation.e.bliss.wallpaper.WallpaperBlurRenderer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class BlurViewDelegate(
    private val view: View,
    private val config: WallpaperBlurRenderer.BlurConfig,
    attrs: AttributeSet? = null
) : View.OnAttachStateChangeListener {

    private val context = view.context

    private var fullBlurDrawable: BlurDrawable? = null
    private var blurAlpha = 255

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {}

            override fun invalidateDrawable(who: Drawable) {
                view.post(view::invalidate)
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        }
    }

    private val locationInWindow = IntArray(2)

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateBounds() }
    private val onScrollChangedListener =
        ViewTreeObserver.OnScrollChangedListener { updateBounds() }

    // Kept for API compatibility with parents that might still notify it
    var offsetParentListener: Any? = null

    var blurCornerRadius = 0f

    private var cachedPathWidth = 0f
    private var cachedPathHeight = 0f
    private val cachedPath = android.graphics.Path()

    private fun getOrCreateDockPath(
        width: Float,
        height: Float,
        radiusRatio: Float = 0.65f,
        n: Double = 5.0,
        segments: Int = 16
    ): android.graphics.Path {
        if (width == cachedPathWidth && height == cachedPathHeight && !cachedPath.isEmpty) {
            return cachedPath
        }
        cachedPathWidth = width
        cachedPathHeight = height
        cachedPath.rewind()

        if (width <= 0f || height <= 0f) return cachedPath

        if (blurCornerRadius > 0f) {
            cachedPath.addRoundRect(
                0f,
                0f,
                width,
                height,
                blurCornerRadius,
                blurCornerRadius,
                android.graphics.Path.Direction.CW
            )
            return cachedPath
        }

        val r = min(height / 2f * radiusRatio, min(width, height) / 2f)
        fun qx(t: Double) = (r * cos(t).pow(2.0 / n)).toFloat()
        fun qy(t: Double) = (r * sin(t).pow(2.0 / n)).toFloat()

        cachedPath.moveTo(r, 0f)
        cachedPath.lineTo(width - r, 0f)
        for (i in 1..segments) {
            val t = (Math.PI / 2) * i / segments
            cachedPath.lineTo(width - qx(t), qy(t))
        } // top-right
        cachedPath.lineTo(width, height - r)
        for (i in 1..segments) {
            val t = (Math.PI / 2) * i / segments
            cachedPath.lineTo(width - qy(t), height - qx(t))
        } // bottom-right
        cachedPath.lineTo(r, height)
        for (i in 1..segments) {
            val t = (Math.PI / 2) * i / segments
            cachedPath.lineTo(qx(t), height - qy(t))
        } // bottom-left
        cachedPath.lineTo(0f, r)
        for (i in 1..segments) {
            val t = (Math.PI / 2) * i / segments
            cachedPath.lineTo(qy(t), qx(t))
        } // top-left
        cachedPath.close()
        return cachedPath
    }

    val outlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width > 0 && view.height > 0) {
                    val path = getOrCreateDockPath(view.width.toFloat(), view.height.toFloat())
                    outline.setConvexPath(path)
                }
            }
        }

    var overlayColor: Int = 0
        set(value) {
            field = value
            overlayPaint.color = value
            fullBlurDrawable?.overlayColor = value
        }

    private val overlayPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        createFullBlurDrawable()
        view.addOnAttachStateChangeListener(this)

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.BlurLayout)
            blurCornerRadius = a.getDimension(R.styleable.BlurLayout_blurCornerRadius, 0f)
            overlayColor = a.getColor(R.styleable.BlurLayout_blurOverlayColor, 0x40FFFFFF)
            a.recycle()
        }
    }

    override fun onViewAttachedToWindow(v: View) {
        fullBlurDrawable?.startListening()
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        view.viewTreeObserver.addOnScrollChangedListener(onScrollChangedListener)
        updateBounds()
    }

    override fun onViewDetachedFromWindow(v: View) {
        fullBlurDrawable?.stopListening()
        view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        view.viewTreeObserver.removeOnScrollChangedListener(onScrollChangedListener)
    }

    fun draw(canvas: Canvas) {
        updateBounds()
        fullBlurDrawable?.apply {
            alpha = blurAlpha
            this.draw(canvas)
        }
        if (overlayColor != 0) {
            val bounds = canvas.clipBounds
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w > 0f && h > 0f) {
                val path = getOrCreateDockPath(w, h)
                val count = canvas.save()
                canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
                canvas.drawPath(path, overlayPaint)
                canvas.restoreToCount(count)
            }
        }
    }

    private fun createFullBlurDrawable() {
        fullBlurDrawable?.let { if (view.isAttachedToWindow) it.stopListening() }
        fullBlurDrawable =
            BlurDrawable(context, config).apply {
                callback = blurDrawableCallback
                overlayColor = this@BlurViewDelegate.overlayColor
                if (view.isAttachedToWindow) startListening()
            }
        updateBounds()
    }

    private fun updateBounds() {
        if (view.width == 0 || view.height == 0) return
        view.getLocationInWindow(locationInWindow)
        val left = locationInWindow[0]
        val top = locationInWindow[1]
        fullBlurDrawable?.setBlurBounds(
            left.toFloat(),
            top.toFloat(),
            (left + view.width).toFloat(),
            (top + view.height).toFloat()
        )
    }
}
