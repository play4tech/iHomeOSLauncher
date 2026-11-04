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

import android.content.Context
import android.graphics.Bitmap
import com.example.blur.NativeGauss
import foundation.e.bliss.wallpaper.WallpaperBlurRenderer

class BlurWallpaperFilter(private val context: Context) :
    WallpaperFilter<WallpaperBlurRenderer.BlurSizes> {

    override fun apply(
        wallpaper: Bitmap
    ): WallpaperFilter.ApplyTask<WallpaperBlurRenderer.BlurSizes> {
        return WallpaperFilter.ApplyTask.create { emitter ->
            var blurBackground: Bitmap? = null
            var blurDock: Bitmap? = null
            var blurAppGroup: Bitmap? = null
            var blurWidget: Bitmap? = null
            try {
                val blurAmount = foundation.e.bliss.glass.GlassConfig.loadSettings(context).blurAmount
                val bgRadius = if (blurAmount <= 0.001f) 1 else (3.5f + blurAmount * 35f).toInt().coerceIn(1, 25)
                val bgConfig = WallpaperBlurRenderer.BlurConfig({ it.background }, 2, bgRadius)
                blurBackground = blur(wallpaper, bgConfig)
                blurDock = blur(wallpaper, WallpaperBlurRenderer.blurConfigDock)
                blurAppGroup = blur(wallpaper, WallpaperBlurRenderer.blurConfigAppGroup)
                blurWidget = blur(wallpaper, WallpaperBlurRenderer.blurConfigWidget)
                emitter.onSuccess(
                    WallpaperBlurRenderer.BlurSizes(
                        blurBackground,
                        blurDock,
                        blurAppGroup,
                        blurWidget
                    )
                )
            } catch (t: Throwable) {
                blurBackground?.recycle()
                blurDock?.recycle()
                blurAppGroup?.recycle()
                blurWidget?.recycle()
                emitter.onError(t)
            }
        }
    }

    private fun blur(wallpaper: Bitmap, config: WallpaperBlurRenderer.BlurConfig): Bitmap {
        val scaled = if (config.scale == 1) {
            wallpaper
        } else {
            Bitmap.createScaledBitmap(
                wallpaper,
                (wallpaper.width / config.scale).coerceAtLeast(1),
                (wallpaper.height / config.scale).coerceAtLeast(1),
                true
            )
        }
        val target = if (scaled.isMutable && scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, true).also {
                if (scaled !== wallpaper) {
                    scaled.recycle()
                }
            }
        }
        val sigma = config.radius.toFloat().coerceAtLeast(1f)
        try {
            NativeGauss.smartBlur(target, sigma, highQuality = true)
        } catch (t: Throwable) {
            android.util.Log.w("BlurWallpaperFilter", "NativeGauss.smartBlur failed, using box3 fallback", t)
            try {
                NativeGauss.box3Inplace(target, config.radius.coerceAtLeast(1))
            } catch (ignored: Throwable) {}
        }
        return target
    }
}
