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
package foundation.e.bliss.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.MainThreadInitializedObject
import foundation.e.bliss.utils.runOnMainThread

class WallpaperEnvironmentBuilder(val context: Context) : WallpaperRepository.Listener {

    private val repository = WallpaperRepository.INSTANCE.get(context)
    private val mListeners = ArrayList<Listener>()

    var environment: WallpaperEnvironment? = null
        private set

    private var parallaxOffset: Float = 0.5f

    init {
        repository.addListener(this)
    }

    override fun onWallpaperChanged(bitmap: Bitmap?) {
        rebuildEnvironment(bitmap, parallaxOffset)
    }

    fun setParallaxOffset(offset: Float) {
        if (foundation.e.bliss.multimode.MultiModeController.isSingleLayerMode) {
            if (this.parallaxOffset == 0.5f) return
            this.parallaxOffset = 0.5f
            rebuildEnvironment(repository.currentWallpaper, 0.5f)
            return
        }
        val safeOffset = if (offset.isNaN()) 0.5f else offset.coerceIn(0f, 1f)
        if (this.parallaxOffset == safeOffset) return
        this.parallaxOffset = safeOffset
        rebuildEnvironment(repository.currentWallpaper, safeOffset)
    }

    fun onConfigurationChanged() {
        rebuildEnvironment(repository.currentWallpaper, parallaxOffset)
    }

    private fun rebuildEnvironment(bitmap: Bitmap?, offset: Float) {
        if (bitmap == null) {
            environment = null
            notifyListeners(false)
            return
        }

        val displaySize = DisplayController.INSTANCE.get(context).info.currentSize
        val wallpaperSize = Point(bitmap.width, bitmap.height)

        val baseScale =
            Math.max(
                displaySize.x.toFloat() / wallpaperSize.x,
                displaySize.y.toFloat() / wallpaperSize.y
            )
        val minParallaxScale = (displaySize.x * 1.15f) / wallpaperSize.x
        val scale = Math.max(baseScale, minParallaxScale)

        val scaledWidth = wallpaperSize.x * scale
        val scaledHeight = wallpaperSize.y * scale

        // Center crop vertically
        val dy = (displaySize.y - scaledHeight) / 2f

        // Parallax horizontally
        val safeOffset = if (offset.isNaN()) 0.5f else offset.coerceIn(0f, 1f)
        val availableWidth = displaySize.x - scaledWidth
        var dx = availableWidth / 2f
        if (availableWidth < 0) {
            // Offset goes 0 to 1. 0.5 is centered.
            dx += (availableWidth * (safeOffset - 0.5f) + 0.5f)
        }
        // Strict boundary clamp: bitmap must cover [0, displaySize.x] horizontally
        if (scaledWidth >= displaySize.x) {
            dx = dx.coerceIn(displaySize.x - scaledWidth, 0f)
        }

        val transform =
            WallpaperTransform(scaleX = scale, scaleY = scale, translateX = dx, translateY = dy)

        val cropRect =
            RectF(
                -dx / scale,
                -dy / scale,
                (displaySize.x - dx) / scale,
                (displaySize.y - dy) / scale
            )

        val oldEnv = environment
        environment =
            WallpaperEnvironment(
                wallpaper = bitmap,
                displaySize = displaySize,
                wallpaperSize = wallpaperSize,
                transform = transform,
                cropRect = cropRect,
                parallaxOffset = offset
            )

        // Only signal a "wallpaper changed" event if the actual bitmap reference changed.
        val bitmapChanged = oldEnv == null || oldEnv.wallpaper !== bitmap
        notifyListeners(bitmapChanged)
    }

    private fun notifyListeners(bitmapChanged: Boolean) {
        runOnMainThread {
            mListeners.forEach { it.onEnvironmentChanged(environment, bitmapChanged) }
        }
    }

    fun addListener(listener: Listener) {
        mListeners.add(listener)
        listener.onEnvironmentChanged(environment, true)
    }

    fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }

    interface Listener {
        fun onEnvironmentChanged(environment: WallpaperEnvironment?, bitmapChanged: Boolean)
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject { context: Context ->
            WallpaperEnvironmentBuilder(context)
        }

        @JvmStatic
        fun getInstanceNoCreate(): WallpaperEnvironmentBuilder? {
            return INSTANCE.noCreate
        }

        @JvmStatic
        fun getInstance(context: Context): WallpaperEnvironmentBuilder {
            return INSTANCE.get(context)
        }
    }
}
