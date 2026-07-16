package foundation.e.bliss.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.widget.Toast
import com.android.launcher3.util.MainThreadInitializedObject
import foundation.e.bliss.blur.BlurWallpaperFilter
import foundation.e.bliss.blur.WallpaperFilter
import foundation.e.bliss.utils.runOnMainThread

class WallpaperBlurRenderer(val context: Context) : WallpaperEnvironmentBuilder.Listener {

    private val builder = WallpaperEnvironmentBuilder.INSTANCE.get(context)
    private val mListeners = ArrayList<Listener>()
    
    private val wallpaperFilter = BlurWallpaperFilter(context)
    private var applyTask: WallpaperFilter.ApplyTask<BlurSizes>? = null
    
    var blurSizes: BlurSizes? = null
        private set
        
    private var isFirstBuild = true

    init {
        builder.addListener(this)
    }

    override fun onEnvironmentChanged(environment: WallpaperEnvironment?, bitmapChanged: Boolean) {
        if (environment == null) {
            blurSizes?.recycle()
            blurSizes = null
            notifyListeners()
            return
        }

        if (bitmapChanged || isFirstBuild) {
            isFirstBuild = false
            regenerateBlur(environment.wallpaper)
        }
    }

    private fun regenerateBlur(wallpaper: Bitmap) {
        val vibrantBitmap = applyVibrancy(wallpaper)

        applyTask = wallpaperFilter.apply(vibrantBitmap).setCallback { result: BlurSizes?, error: Throwable? ->
            vibrantBitmap.recycle()
            applyTask = null
            
            if (error == null) {
                blurSizes?.recycle()
                blurSizes = result
                notifyListeners()
            } else {
                if (error is OutOfMemoryError) {
                    runOnMainThread {
                        Toast.makeText(context, "Blur Failed! (OOM)", Toast.LENGTH_LONG).show()
                    }
                }
                blurSizes?.recycle()
                blurSizes = null
                notifyListeners()
            }
        }
    }

    private val mVibrancyPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(1.25f)
        colorFilter = ColorMatrixColorFilter(colorMatrix)
    }

    private fun applyVibrancy(wallpaper: Bitmap): Bitmap {
        val bitmap = Bitmap.createBitmap(wallpaper.width, wallpaper.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(wallpaper, 0f, 0f, mVibrancyPaint)
        return bitmap
    }

    private fun notifyListeners() {
        runOnMainThread {
            mListeners.forEach { it.onBlurCacheUpdated(blurSizes) }
        }
    }

    fun addListener(listener: Listener) {
        mListeners.add(listener)
        listener.onBlurCacheUpdated(blurSizes)
    }

    fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }

    interface Listener {
        fun onBlurCacheUpdated(blurSizes: BlurSizes?)
    }

    data class BlurSizes(
        val background: Bitmap,
        val dock: Bitmap,
        val appGroup: Bitmap,
        val widget: Bitmap
    ) {
        fun recycle() {
            background.recycle()
            dock.recycle()
            appGroup.recycle()
            widget.recycle()
        }
    }

    data class BlurConfig(val getDrawable: (BlurSizes) -> Bitmap, val scale: Int, val radius: Int)

    companion object {
        val INSTANCE = MainThreadInitializedObject { context: Context ->
            WallpaperBlurRenderer(context)
        }
        
        fun getInstanceNoCreate(): WallpaperBlurRenderer {
            return INSTANCE.getNoCreate()
        }
        
        fun getInstance(context: Context): WallpaperBlurRenderer {
            return INSTANCE.get(context)
        }
        
        @JvmField val blurConfigBackground = BlurConfig({ it.background }, 2, 8)
        @JvmField val blurConfigDock = BlurConfig({ it.dock }, 8, 25)
        @JvmField val blurConfigAppGroup = BlurConfig({ it.appGroup }, 6, 8)
        @JvmField val blurConfigWidget = BlurConfig({ it.widget }, 6, 2)
    }
}
