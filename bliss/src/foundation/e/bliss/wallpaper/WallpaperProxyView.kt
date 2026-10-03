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

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import com.android.launcher3.Insettable

class WallpaperProxyView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr),
    WallpaperEnvironmentBuilder.Listener,
    WallpaperRepository.Listener,
    Insettable {

    private var hasLoggedDraw = false
    private var fallbackGradientPaint: Paint? = null

    init {
        setWillNotDraw(false)
        Log.d("WallpaperProxyView", "WallpaperProxyView initialized")
    }

    private val envBuilder = WallpaperEnvironmentBuilder.INSTANCE.get(context)
    private val repository = WallpaperRepository.INSTANCE.get(context)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    override fun setInsets(insets: Rect) {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d("WallpaperProxyView", "onAttachedToWindow: Proxy view attached to window")
        // The proxy can outlive a wallpaper change receiver registration in Workspace.
        // Request a fresh system bitmap whenever the glass source becomes active.
        repository.updateAsync()
        envBuilder.addListener(this)
        repository.addListener(this)
        updateFallbackGradient(width, height, repository.currentColors)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d("WallpaperProxyView", "onDetachedFromWindow: Proxy view detached")
        envBuilder.removeListener(this)
        repository.removeListener(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateFallbackGradient(w, h, repository.currentColors)
    }

    override fun onWallpaperChanged(bitmap: android.graphics.Bitmap?) {
        // Environment builder handles bitmap changes
    }

    override fun onWallpaperColorsChanged(colors: WallpaperColors?) {
        updateFallbackGradient(width, height, colors)
    }

    private fun updateFallbackGradient(w: Int, h: Int, colors: WallpaperColors?) {
        if (w <= 0 || h <= 0) return
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            colors?.primaryColor?.toArgb() ?: Color.DKGRAY
        } else {
            Color.DKGRAY
        }
        val secondary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            colors?.secondaryColor?.toArgb() ?: primary
        } else {
            primary
        }
        val tertiary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            colors?.tertiaryColor?.toArgb() ?: secondary
        } else {
            secondary
        }

        // Build 3-stop vertical gradient matching system wallpaper palette
        val gradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(primary, secondary, tertiary),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        fallbackGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }
        invalidate()
    }

    override fun onEnvironmentChanged(environment: WallpaperEnvironment?, bitmapChanged: Boolean) {
        Log.d(
            "WallpaperProxyView",
            "onEnvironmentChanged: environmentIsNull=${environment == null}, hasBitmap=${environment?.wallpaper != null}"
        )
        hasLoggedDraw = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val environment = envBuilder.environment
        if (environment != null) {
            if (!hasLoggedDraw) {
                Log.d(
                    "WallpaperProxyView",
                    "onDraw: drawing wallpaper bitmap (${environment.wallpaper.width}x${environment.wallpaper.height}) to proxy canvas"
                )
                hasLoggedDraw = true
            }
            environment.transform.toMatrix(matrix)
            canvas.drawBitmap(environment.wallpaper, matrix, paint)
        } else if (repository.currentWallpaper != null) {
            canvas.drawBitmap(repository.currentWallpaper!!, matrix, paint)
        } else {
            val gradPaint = fallbackGradientPaint
            if (gradPaint != null) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradPaint)
            } else if (!hasLoggedDraw) {
                Log.w(
                    "WallpaperProxyView",
                    "onDraw: environment and fallbackGradient are null"
                )
                hasLoggedDraw = true
            }
        }
    }
}
