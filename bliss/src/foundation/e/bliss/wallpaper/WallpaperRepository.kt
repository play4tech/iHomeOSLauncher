package foundation.e.bliss.wallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.util.Executors
import com.android.launcher3.util.MainThreadInitializedObject
import foundation.e.bliss.utils.runOnMainThread

@SuppressLint("NewApi")
class WallpaperRepository(val context: Context) {

    private val mWallpaperManager = WallpaperManager.getInstance(context)
    private val mListeners = ArrayList<Listener>()
    
    var currentWallpaper: Bitmap? = null
        private set
        
    var isLiveWallpaper = false
        private set

    init {
        updateAsync()
    }
    
    fun setLiveWallpaper(isLive: Boolean) {
        if (isLiveWallpaper != isLive) {
            isLiveWallpaper = isLive
            updateAsync()
        }
    }

    fun updateAsync() {
        Executors.THREAD_POOL_EXECUTOR.execute { updateWallpaper() }
    }

    @SuppressLint("MissingPermission")
    private fun updateWallpaper() {
        val wallpaper = try {
            val drawable = mWallpaperManager.drawable
            if (drawable == null) null
            else {
                val wall = drawable.toBitmap()
                if (isLiveWallpaper) {
                    createTransparentBitmap(wall.width, wall.height)
                } else {
                    wall
                }
            }
        } catch (e: Exception) {
            null
        }
        
        currentWallpaper = wallpaper
        runOnMainThread {
            mListeners.forEach { it.onWallpaperChanged(wallpaper) }
        }
    }
    
    private fun createTransparentBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.argb(68, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    fun addListener(listener: Listener) {
        mListeners.add(listener)
        listener.onWallpaperChanged(currentWallpaper)
    }

    fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }

    interface Listener {
        fun onWallpaperChanged(bitmap: Bitmap?)
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject { context: Context ->
            WallpaperRepository(context)
        }
        
        fun getInstanceNoCreate(): WallpaperRepository {
            return INSTANCE.getNoCreate()
        }
        
        fun getInstance(context: Context): WallpaperRepository {
            return INSTANCE.get(context)
        }
    }
}
