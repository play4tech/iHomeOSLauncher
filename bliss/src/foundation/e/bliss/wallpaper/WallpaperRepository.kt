package foundation.e.bliss.wallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.util.Executors
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SimpleBroadcastReceiver
import foundation.e.bliss.utils.runOnMainThread

@SuppressLint("NewApi")
class WallpaperRepository(val context: Context) {

    private val mWallpaperManager = WallpaperManager.getInstance(context)
    private val mListeners = ArrayList<Listener>()
    private val wallpaperChangeReceiver = SimpleBroadcastReceiver { updateAsync() }
    private var latestRequestId = 0L
    
    var currentWallpaper: Bitmap? = null
        private set
        
    var isLiveWallpaper = false
        private set

    init {
        wallpaperChangeReceiver.register(context, Intent.ACTION_WALLPAPER_CHANGED)
        updateAsync()
    }
    
    fun setLiveWallpaper(isLive: Boolean) {
        if (isLiveWallpaper != isLive) {
            isLiveWallpaper = isLive
            updateAsync()
        }
    }

    fun updateAsync() {
        val requestId = synchronized(this) {
            ++latestRequestId
        }
        Executors.THREAD_POOL_EXECUTOR.execute { updateWallpaper(requestId) }
    }

    @SuppressLint("MissingPermission")
    private fun updateWallpaper(requestId: Long) {
        var wallpaper: Bitmap? = null
        val isLive = mWallpaperManager.wallpaperInfo != null
        
        if (!isLive) {
            try {
                wallpaper = decodeSystemWallpaper()
                android.util.Log.d("WallpaperRepository", "decodeSystemWallpaper result: ${if (wallpaper != null) "${wallpaper.width}x${wallpaper.height}" else "null"}")
            } catch (e: Exception) {
                android.util.Log.e("WallpaperRepository", "Error in decodeSystemWallpaper", e)
            }
        }

        if (wallpaper == null) {
            try {
                val drawable = mWallpaperManager.drawable
                wallpaper = drawable?.toBitmap()
                android.util.Log.d("WallpaperRepository", "mWallpaperManager.drawable result: ${if (wallpaper != null) "${wallpaper.width}x${wallpaper.height}" else "null"}")
            } catch (e: Exception) {
                android.util.Log.e("WallpaperRepository", "Error fetching wallpaper drawable", e)
            }
        }

        // Requests run on a shared thread pool. Do not let a slow, earlier read replace a
        // newer system wallpaper after the wallpaper-change broadcast has already arrived.
        if (synchronized(this) { requestId != latestRequestId }) return

        currentWallpaper = wallpaper
        runOnMainThread {
            mListeners.forEach { it.onWallpaperChanged(wallpaper) }
        }
    }

    private fun decodeSystemWallpaper(): Bitmap? {
        // The drawable fallback below is cached by WallpaperManager on some OEM builds.
        // Clear that cache before reading so a newly-applied wallpaper is never replaced by
        // the previous bitmap if the file descriptor is unavailable.
        mWallpaperManager.forgetLoadedWallpaper()
        val wallpaperFile: ParcelFileDescriptor = mWallpaperManager
            .getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null

        return wallpaperFile.use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
        }
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
