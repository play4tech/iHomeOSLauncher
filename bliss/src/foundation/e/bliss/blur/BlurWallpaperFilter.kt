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
import com.hoko.blur.HokoBlur
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
                blurBackground = blur(wallpaper, WallpaperBlurRenderer.blurConfigBackground)
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
        val source =
            if (config.scale == 1) {
                wallpaper
            } else {
                Bitmap.createScaledBitmap(
                    wallpaper,
                    wallpaper.width / config.scale,
                    wallpaper.height / config.scale,
                    true
                )
            }
        val blurred =
            HokoBlur.with(context)
                .scheme(HokoBlur.SCHEME_NATIVE)
                .mode(HokoBlur.MODE_STACK)
                .radius(config.radius)
                .forceCopy(false)
                .processor()
                .blur(source)
        return blurred
    }
}
