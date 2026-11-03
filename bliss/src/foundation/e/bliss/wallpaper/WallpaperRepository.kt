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
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.app.WallpaperColors
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.LauncherFiles
import com.android.launcher3.util.Executors
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SimpleBroadcastReceiver
import foundation.e.bliss.utils.runOnMainThread
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("NewApi")
class WallpaperRepository(val context: Context) {

    private val mWallpaperManager = WallpaperManager.getInstance(context)
    private val mListeners = CopyOnWriteArrayList<Listener>()
    private val wallpaperChangeReceiver = SimpleBroadcastReceiver { updateAsync() }
    private var latestRequestId = 0L

    var currentWallpaper: Bitmap? = null
        private set

    var currentColors: WallpaperColors? = null
        private set

    var isLiveWallpaper = false
        private set

    var isFallbackWallpaper = false
        private set

    var isTopLight = false
        private set

    var currentLoadedWallpaperId: Int = -1
        private set

    init {
        wallpaperChangeReceiver.register(context, Intent.ACTION_WALLPAPER_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                currentColors = mWallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                isTopLight = computeIsTopLight(currentWallpaper, currentColors)
                mWallpaperManager.addOnColorsChangedListener(
                    { colors, which ->
                        if ((which and WallpaperManager.FLAG_SYSTEM) != 0) {
                            currentColors = colors
                            isTopLight = computeIsTopLight(currentWallpaper, colors)
                            runOnMainThread {
                                mListeners.forEach {
                                    it.onWallpaperColorsChanged(colors)
                                    it.onTopLuminanceChanged(isTopLight)
                                }
                            }
                        }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
            } catch (t: Throwable) {
                android.util.Log.w("WallpaperRepository", "Failed to register OnColorsChangedListener", t)
            }
        }
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
        var fallback = false
        val isLive = mWallpaperManager.wallpaperInfo != null

        // -------------------------------------------------------------
        // Tier 0: Check Internal FilesDir Cache with Wallpaper-ID check
        // -------------------------------------------------------------
        val prefs = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        val cachedId = prefs.getInt(PREF_CACHED_WALLPAPER_ID, -1)
        val currentWallpaperId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        } else {
            -1
        }
        val cacheFile = File(context.filesDir, CACHED_WALLPAPER_FILE)

        if (cacheFile.exists()) {
            if (currentWallpaperId != -1 && cachedId == currentWallpaperId) {
                try {
                    wallpaper = BitmapFactory.decodeFile(cacheFile.absolutePath)
                    android.util.Log.d(
                        "WallpaperRepository",
                        "Tier 0 hit: loaded cached wallpaper (id=$cachedId) ${wallpaper?.width}x${wallpaper?.height}"
                    )
                } catch (t: Throwable) {
                    android.util.Log.e("WallpaperRepository", "Error decoding cached wallpaper", t)
                }
            } else {
                android.util.Log.d(
                    "WallpaperRepository",
                    "Tier 0 stale cache detected (cachedId=$cachedId, currentId=$currentWallpaperId). Deleting cache."
                )
                cacheFile.delete()
                prefs.edit().remove(PREF_CACHED_WALLPAPER_ID).apply()
            }
        }

        // -------------------------------------------------------------
        // Tier 1: Decode System Wallpaper from ParcelFileDescriptor
        // -------------------------------------------------------------
        if (wallpaper == null && !isLive) {
            try {
                wallpaper = decodeSystemWallpaper()
                android.util.Log.d(
                    "WallpaperRepository",
                    "Tier 1 result: decodeSystemWallpaper ${if (wallpaper != null) "${wallpaper.width}x${wallpaper.height}" else "null"}"
                )
            } catch (t: Throwable) {
                android.util.Log.e("WallpaperRepository", "Error in decodeSystemWallpaper", t)
            }
        }

        // -------------------------------------------------------------
        // Tier 2: Fetch via WallpaperManager.drawable
        // -------------------------------------------------------------
        if (wallpaper == null) {
            try {
                val drawable = mWallpaperManager.drawable
                wallpaper = drawable?.toBitmap()
                android.util.Log.d(
                    "WallpaperRepository",
                    "Tier 2 result: mWallpaperManager.drawable ${if (wallpaper != null) "${wallpaper.width}x${wallpaper.height}" else "null"}"
                )
            } catch (t: Throwable) {
                android.util.Log.e("WallpaperRepository", "Error fetching wallpaper drawable", t)
            }
        }

        // -------------------------------------------------------------
        // Tier 3: Fetch Built-In ROM Fallback Drawable
        // -------------------------------------------------------------
        if (wallpaper == null) {
            if (currentWallpaper != null) {
                android.util.Log.d("WallpaperRepository", "Keeping current wallpaper as fallback")
                wallpaper = currentWallpaper
            } else {
                try {
                    val builtIn = mWallpaperManager.getBuiltInDrawable()
                    wallpaper = builtIn?.toBitmap()
                    if (wallpaper != null) {
                        fallback = true
                        android.util.Log.d(
                            "WallpaperRepository",
                            "Tier 3 result: built-in wallpaper fallback ${wallpaper.width}x${wallpaper.height}"
                        )
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("WallpaperRepository", "Error fetching built-in wallpaper drawable", t)
                }
            }
        }

        // Update WallpaperColors cache if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                currentColors = mWallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            } catch (t: Throwable) {
                android.util.Log.w("WallpaperRepository", "Error fetching WallpaperColors", t)
            }
        }

        // Requests run on a shared thread pool. Do not let a slow, earlier read replace a
        // newer system wallpaper after the wallpaper-change broadcast has already arrived.
        if (synchronized(this) { requestId != latestRequestId }) return

        isFallbackWallpaper = fallback
        currentWallpaper = wallpaper
        currentLoadedWallpaperId = currentWallpaperId
        val colors = currentColors
        isTopLight = computeIsTopLight(wallpaper, colors)
        runOnMainThread {
            mListeners.forEach {
                it.onWallpaperChanged(wallpaper)
                it.onWallpaperColorsChanged(colors)
                it.onTopLuminanceChanged(isTopLight)
            }
        }
    }

    fun saveWallpaperToCache(bitmap: Bitmap, wallpaperId: Int) {
        Executors.THREAD_POOL_EXECUTOR.execute {
            try {
                val cacheFile = File(context.filesDir, CACHED_WALLPAPER_FILE)
                val tempFile = File(context.filesDir, "${CACHED_WALLPAPER_FILE}.tmp")
                FileOutputStream(tempFile).use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                }
                if (tempFile.renameTo(cacheFile)) {
                    val prefs = context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                    prefs.edit().putInt(PREF_CACHED_WALLPAPER_ID, wallpaperId).apply()
                    android.util.Log.d("WallpaperRepository", "Saved wallpaper cache (id=$wallpaperId)")
                }
            } catch (t: Throwable) {
                android.util.Log.e("WallpaperRepository", "Failed to save wallpaper cache", t)
            }
        }
    }

    fun updateDirectly(bitmap: Bitmap) {
        isFallbackWallpaper = false
        currentWallpaper = bitmap
        currentLoadedWallpaperId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        } else {
            -1
        }
        isTopLight = computeIsTopLight(bitmap, currentColors)
        runOnMainThread {
            mListeners.forEach {
                it.onWallpaperChanged(bitmap)
                it.onTopLuminanceChanged(isTopLight)
            }
        }
    }

    fun checkWallpaperChanged(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val systemId = mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            if (systemId != -1 && systemId != currentLoadedWallpaperId) {
                android.util.Log.d(
                    "WallpaperRepository",
                    "Wallpaper change detected via checkWallpaperChanged: current=$currentLoadedWallpaperId, system=$systemId"
                )
                updateAsync()
                return true
            }
        }
        return false
    }

    fun computeIsTopLight(bitmap: Bitmap?, colors: WallpaperColors?): Boolean {
        if (bitmap != null) {
            try {
                val sampleHeight = (bitmap.height * 0.12f).toInt().coerceAtLeast(1)
                val width = bitmap.width
                val stepX = (width / 24).coerceAtLeast(1)
                val stepY = (sampleHeight / 8).coerceAtLeast(1)
                var totalLuminance = 0.0
                var count = 0
                for (y in 0 until sampleHeight step stepY) {
                    for (x in 0 until width step stepX) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16 and 0xFF) / 255.0
                        val g = (pixel shr 8 and 0xFF) / 255.0
                        val b = (pixel and 0xFF) / 255.0
                        val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
                        totalLuminance += lum
                        count++
                    }
                }
                if (count > 0) {
                    val avgLum = totalLuminance / count
                    return avgLum > 0.55
                }
            } catch (t: Throwable) {
                android.util.Log.w("WallpaperRepository", "Error calculating top luminance", t)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colors != null) {
            return (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        }
        return false
    }

    private fun decodeSystemWallpaper(): Bitmap? {
        // Isolate hidden API in its own try-catch so failure does not block getWallpaperFile
        try {
            mWallpaperManager.forgetLoadedWallpaper()
        } catch (t: Throwable) {
            android.util.Log.w("WallpaperRepository", "forgetLoadedWallpaper failed", t)
        }

        val dm = context.resources.displayMetrics
        val maxDim = maxOf(dm.widthPixels, dm.heightPixels) * 2

        var pfd = try {
            mWallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null
        } catch (e: SecurityException) {
            android.util.Log.w("WallpaperRepository", "Permission denied accessing wallpaper file: ${e.message}")
            return null
        } catch (t: Throwable) {
            android.util.Log.w("WallpaperRepository", "Error getting wallpaper file", t)
            return null
        }
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }

            var canRewind = false
            try {
                android.system.Os.lseek(pfd.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
                canRewind = true
            } catch (e: Exception) {
                android.util.Log.w("WallpaperRepository", "lseek failed, reopening pfd", e)
            }

            if (!canRewind) {
                try {
                    pfd.close()
                } catch (_: Exception) {}
                pfd = mWallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null
            }

            var sampleSize = 1
            while ((options.outWidth / sampleSize) > maxDim ||
                (options.outHeight / sampleSize) > maxDim
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, decodeOptions)
            android.util.Log.d(
                "WallpaperRepository",
                "Decoded system wallpaper (sampleSize=$sampleSize): ${bitmap?.width}x${bitmap?.height}"
            )
            bitmap
        } catch (t: Throwable) {
            android.util.Log.e("WallpaperRepository", "Failed to decode wallpaper fd", t)
            null
        } finally {
            try {
                pfd.close()
            } catch (_: Exception) {}
        }
    }

    fun addListener(listener: Listener) {
        mListeners.add(listener)
        listener.onWallpaperChanged(currentWallpaper)
        listener.onWallpaperColorsChanged(currentColors)
        listener.onTopLuminanceChanged(isTopLight)
    }

    fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }

    interface Listener {
        fun onWallpaperChanged(bitmap: Bitmap?)
        fun onWallpaperColorsChanged(colors: WallpaperColors?) {}
        fun onTopLuminanceChanged(isTopLight: Boolean) {}
    }

    companion object {
        const val CACHED_WALLPAPER_FILE = "cached_wallpaper.webp"
        const val PREF_CACHED_WALLPAPER_ID = "cached_wallpaper_id"

        val INSTANCE = MainThreadInitializedObject { context: Context ->
            WallpaperRepository(context)
        }

        @JvmStatic
        fun getInstanceNoCreate(): WallpaperRepository? {
            return INSTANCE.getNoCreate()
        }

        @JvmStatic
        fun getInstance(context: Context): WallpaperRepository {
            return INSTANCE.get(context)
        }
    }
}
