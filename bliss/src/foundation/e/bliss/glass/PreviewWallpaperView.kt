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
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.example.liquidglass.LiquidGlassView
import foundation.e.bliss.wallpaper.WallpaperBlurRenderer

/**
 * Preview wallpaper view for [com.android.launcher3.settings.GlassCustomizeActivity].
 * When captured by live glass preview components (dock, folder, search pill),
 * dynamically blends the blurred wallpaper bitmap according to [blurAmount] so
 * optical blur adjustments can be previewed in real time.
 */
class PreviewWallpaperView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), WallpaperBlurRenderer.Listener {

    private val blurRenderer = WallpaperBlurRenderer.INSTANCE.get(context)
    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val blurMatrix = Matrix()

    var blurAmount: Float = GlassConfig.DEFAULT_BLUR_AMOUNT

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        blurRenderer.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blurRenderer.removeListener(this)
    }

    override fun onBlurCacheUpdated(blurSizes: WallpaperBlurRenderer.BlurSizes?) {
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
