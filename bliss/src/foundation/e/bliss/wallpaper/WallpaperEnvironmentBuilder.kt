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
        if (this.parallaxOffset == offset) return
        this.parallaxOffset = offset
        rebuildEnvironment(repository.currentWallpaper, offset)
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

        val scale = Math.max(
            displaySize.x.toFloat() / wallpaperSize.x,
            displaySize.y.toFloat() / wallpaperSize.y
        )

        val scaledWidth = wallpaperSize.x * scale
        val scaledHeight = wallpaperSize.y * scale

        // Center crop vertically
        val dy = (displaySize.y - scaledHeight) / 2f

        // Parallax horizontally
        val availableWidth = displaySize.x - scaledWidth
        var dx = availableWidth / 2f
        if (availableWidth < 0) {
            // Offset goes 0 to 1. 0.5 is centered.
            dx += (availableWidth * (offset - 0.5f) + 0.5f)
        }

        val transform = WallpaperTransform(
            scaleX = scale,
            scaleY = scale,
            translateX = dx,
            translateY = dy
        )

        val cropRect = RectF(
            -dx / scale,
            -dy / scale,
            (displaySize.x - dx) / scale,
            (displaySize.y - dy) / scale
        )

        val oldEnv = environment
        environment = WallpaperEnvironment(
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
        
        fun getInstanceNoCreate(): WallpaperEnvironmentBuilder {
            return INSTANCE.getNoCreate()
        }
        
        fun getInstance(context: Context): WallpaperEnvironmentBuilder {
            return INSTANCE.get(context)
        }
    }
}
