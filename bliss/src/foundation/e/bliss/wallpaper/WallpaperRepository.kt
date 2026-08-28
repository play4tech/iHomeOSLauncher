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
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("NewApi")
class WallpaperRepository(val context: Context) {

    private val mWallpaperManager = WallpaperManager.getInstance(context)
    private val mListeners = CopyOnWriteArrayList<Listener>()
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
        val requestId = synchronized(this) { ++latestRequestId }
        Executors.THREAD_POOL_EXECUTOR.execute { updateWallpaper(requestId) }
    }

    @SuppressLint("MissingPermission")
    private fun updateWallpaper(requestId: Long) {
        var wallpaper: Bitmap? = null
        val isLive = mWallpaperManager.wallpaperInfo != null

        if (!isLive) {
            try {
                wallpaper = decodeSystemWallpaper()
                android.util.Log.d(
                    "WallpaperRepository",
                    "decodeSystemWallpaper result: ${if (wallpaper != null) "${wallpaper.width}x${wallpaper.height}" else "null"}"
                )
            } catch (t: Throwable) {
                android.util.Log.e("WallpaperRepository", "Error in decodeSystemWallpaper", t)
            }
        }

        if (wallpaper == null) {
            try {
                val drawable = mWallpaperManager.drawable
                wallpaper = drawable?.toBitmap()
                android.util.Log.d(
                    "WallpaperRepository",
                    "mWallpaperManager.drawable result: ${if (wallpaper != null) "${wallpaper.width}x${wallpaper.height}" else "null"}"
                )
            } catch (t: Throwable) {
                android.util.Log.e("WallpaperRepository", "Error fetching wallpaper drawable", t)
            }
        }

        // Requests run on a shared thread pool. Do not let a slow, earlier read replace a
        // newer system wallpaper after the wallpaper-change broadcast has already arrived.
        if (synchronized(this) { requestId != latestRequestId }) return

        currentWallpaper = wallpaper
        runOnMainThread { mListeners.forEach { it.onWallpaperChanged(wallpaper) } }
    }

    fun updateDirectly(bitmap: Bitmap) {
        currentWallpaper = bitmap
        runOnMainThread { mListeners.forEach { it.onWallpaperChanged(bitmap) } }
    }

    private fun decodeSystemWallpaper(): Bitmap? {
        // The drawable fallback below is cached by WallpaperManager on some OEM builds.
        // Clear that cache before reading so a newly-applied wallpaper is never replaced by
        // the previous bitmap if the file descriptor is unavailable.
        mWallpaperManager.forgetLoadedWallpaper()
        val wallpaperFile: ParcelFileDescriptor =
            mWallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null

        return wallpaperFile.use {
            try {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
            } catch (t: Throwable) {
                android.util.Log.e("WallpaperRepository", "Failed to decode wallpaper fd", t)
                null
            }
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
