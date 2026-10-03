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

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import android.os.Build
import com.android.launcher3.util.Executors
import foundation.e.bliss.utils.runOnMainThread

object WallpaperPickerHelper {

    private const val TAG = "WallpaperPickerHelper"
    const val REQUEST_PICK_WALLPAPER = 1005

    /** Launches the system gallery or photo picker to let the user select a wallpaper. */
    @JvmStatic
    fun launchGalleryPicker(activity: Activity) {
        // Prefer modern API 33+ Android Photo Picker (zero storage permissions required)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val intent = Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                    type = "image/*"
                }
                activity.startActivityForResult(intent, REQUEST_PICK_WALLPAPER)
                return
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_PICK_IMAGES failed, falling back to ACTION_GET_CONTENT", e)
            }
        }

        try {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            activity.startActivityForResult(
                Intent.createChooser(intent, "Select Wallpaper"),
                REQUEST_PICK_WALLPAPER
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ACTION_GET_CONTENT, falling back to ACTION_PICK", e)
            try {
                val pickIntent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activity.startActivityForResult(pickIntent, REQUEST_PICK_WALLPAPER)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch gallery picker", ex)
                Toast.makeText(activity, "Cannot open gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Reads and decodes the image at [uri] safely on a worker thread and applies it as the system
     * wallpaper.
     */
    @JvmStatic
    fun handleWallpaperResult(context: Context, uri: Uri?) {
        if (uri == null) return

        if (context is com.android.launcher3.Launcher) {
            WallpaperLoadingOverlay.show(context)
        }

        Executors.THREAD_POOL_EXECUTOR.execute {
            var success = false
            try {
                val resolver = context.contentResolver
                // First decode dimensions to prevent OutOfMemoryError
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                // Determine display size for optimal sample sizing
                val dm = context.resources.displayMetrics
                val maxDim = maxOf(dm.widthPixels, dm.heightPixels) * 2
                var sampleSize = 1
                while (
                    (options.outWidth / sampleSize) > maxDim ||
                        (options.outHeight / sampleSize) > maxDim
                ) {
                    sampleSize *= 2
                }

                // Decode bitmap with calculated inSampleSize
                val decodeOptions =
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                val bitmap: Bitmap? =
                    resolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOptions)
                    }

                if (bitmap != null) {
                    val wm = WallpaperManager.getInstance(context)
                    wm.setBitmap(
                        bitmap,
                        null,
                        true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    )
                    val newId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                    } else {
                        -1
                    }
                    val repo = WallpaperRepository.getInstance(context)
                    repo.saveWallpaperToCache(bitmap, newId)
                    repo.updateDirectly(bitmap)
                    WallpaperLoadingOverlay.dismiss()
                    success = true
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error applying chosen wallpaper", t)
                WallpaperLoadingOverlay.dismiss()
            }

            runOnMainThread {
                if (success) {
                    Toast.makeText(context, "Wallpaper applied", Toast.LENGTH_SHORT).show()
                } else {
                    WallpaperLoadingOverlay.dismiss()
                    Toast.makeText(context, "Failed to apply wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
