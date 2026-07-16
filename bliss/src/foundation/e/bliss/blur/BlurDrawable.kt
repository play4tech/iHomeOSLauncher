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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class BlurDrawable
internal constructor(
    private val blurWallpaperProvider: BlurWallpaperProvider,
    private val config: BlurWallpaperProvider.BlurConfig = BlurWallpaperProvider.blurConfigWidget
) : Drawable(), BlurWallpaperProvider.Listener {

    private var blurAlpha = 255
    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var blurBitmap: Bitmap? = null

    private val blurBounds = RectF()
    private var offsetX = 0f
    private var offsetY = 0f
    private val dstRect = RectF()

    private var croppedBitmap: Bitmap? = null
    private var lastOffsetX = -1f
    private var lastOffsetY = -1f
    private var lastBitmap: Bitmap? = null
    private val localOverlayPaint = Paint().apply { isAntiAlias = true }

    override fun draw(canvas: Canvas) {
        val width = blurBounds.right.toInt() - blurBounds.left.toInt()
        val height = blurBounds.bottom.toInt() - blurBounds.top.toInt()
        if (width <= 0 || height <= 0) return
        if (blurAlpha == 0) return
        val wallpapers = blurWallpaperProvider.wallpapers
        blurBitmap = wallpapers?.let { config.getDrawable(it) }
        val scale = config.scale.toFloat()
        val bitmap = blurBitmap ?: blurWallpaperProvider.placeholder ?: return

        // srcRect = region in the (downscaled) bitmap that corresponds to this view's position.
        // The bitmap is `scale` times smaller than screen, so we divide screen coords by scale.
        // srcLeft/srcTop = view's screen position offset by scroll, divided by scale.
        // srcRight/srcBottom = srcLeft/srcTop + view dimensions / scale.
        val srcLeft   = (blurBounds.left + offsetX) / scale
        val srcTop    = (blurBounds.top  + offsetY) / scale
        val srcRight  = srcLeft + width  / scale
        val srcBottom = srcTop  + height / scale

        val x = srcLeft.toInt().coerceIn(0, bitmap.width)
        val y = srcTop.toInt().coerceIn(0, bitmap.height)

        // If x or y is already at the bitmap edge, remaining space is 0 — nothing to draw.
        if (x >= bitmap.width || y >= bitmap.height) return

        var w = (srcRight.toInt() - srcLeft.toInt()).coerceIn(1, bitmap.width - x)
        var h = (srcBottom.toInt() - srcTop.toInt()).coerceIn(1, bitmap.height - y)

        // Ensure we don't try to create an empty bitmap
        if (x + w > bitmap.width) w = bitmap.width - x
        if (y + h > bitmap.height) h = bitmap.height - y


        if (w > 0 && h > 0) {
            if (croppedBitmap == null || lastOffsetX != offsetX || lastOffsetY != offsetY || lastBitmap != bitmap) {
                lastOffsetX = offsetX
                lastOffsetY = offsetY
                lastBitmap = bitmap
                croppedBitmap?.recycle()
                croppedBitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
            }
        } else {
        }

        dstRect.set(0f, 0f, width.toFloat(), height.toFloat())

        val toDraw = croppedBitmap
        if (toDraw != null && !toDraw.isRecycled) {
            canvas.drawBitmap(toDraw, null, dstRect, null)
        }

        if (overlayColor != android.graphics.Color.TRANSPARENT) {
            localOverlayPaint.color = overlayColor
            canvas.drawRect(dstRect, localOverlayPaint)
        }
    }

    var overlayColor: Int = android.graphics.Color.TRANSPARENT

    override fun setAlpha(alpha: Int) {
        blurAlpha = alpha
        blurPaint.alpha = alpha
    }

    override fun getAlpha() = blurAlpha

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) =
        setBlurBounds(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    fun setBlurBounds(left: Float, top: Float, right: Float, bottom: Float) {
        if (
            blurBounds.left != left ||
                blurBounds.top != top ||
                blurBounds.right != right ||
                blurBounds.bottom != bottom
        ) {
            blurBounds.set(left, top, right, bottom)
        }
    }

    fun setOffsets(offsetX: Float, offsetY: Float) {
        this.offsetX = offsetX
        this.offsetY = offsetY
    }

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onWallpaperChanged() {
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    fun startListening() = blurWallpaperProvider.addListener(this)

    fun stopListening() = blurWallpaperProvider.removeListener(this)
}
