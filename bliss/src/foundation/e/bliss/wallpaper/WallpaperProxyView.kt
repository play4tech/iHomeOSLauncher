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
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.android.launcher3.Insettable
import com.example.liquidglass.BlurredBackdropProvider
import com.example.liquidglass.LiquidGlassView
import com.example.liquidglass.SharedBlurredBackdrop
import foundation.e.bliss.glass.GlassConfig

class WallpaperProxyView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr),
    WallpaperEnvironmentBuilder.Listener,
    WallpaperRepository.Listener,
    WallpaperBlurRenderer.Listener,
    Insettable,
    BlurredBackdropProvider {

    private var hasLoggedDraw = false
    private var fallbackGradientPaint: Paint? = null

    private val sharedBlur by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SharedBlurredBackdrop()
        } else {
            null
        }
    }
    private val tmpMatrix = Matrix()
    private val tmpVals = FloatArray(9)

    /** Translation to apply when drawing the node returned by [blurredWallpaper]. */
    override var blurDx = 0f
        private set
    override var blurDy = 0f
        private set

    init {
        setWillNotDraw(false)
        Log.d("WallpaperProxyView", "WallpaperProxyView initialized")
    }

    private val envBuilder = WallpaperEnvironmentBuilder.INSTANCE.get(context)
    private val repository = WallpaperRepository.INSTANCE.get(context)
    private val blurRenderer = WallpaperBlurRenderer.INSTANCE.get(context)
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
        blurRenderer.addListener(this)
        updateFallbackGradient(width, height, repository.currentColors)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d("WallpaperProxyView", "onDetachedFromWindow: Proxy view detached")
        envBuilder.removeListener(this)
        repository.removeListener(this)
        blurRenderer.removeListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sharedBlur?.release()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == android.view.View.VISIBLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sharedBlur?.invalidate()
        }
    }

    /**
     * Blurred wallpaper in the same space onDraw uses: draw it translated by (blurDx, blurDy).
     * Returns null when the old capture path must be used (no wallpaper, or the transform is
     * not a plain scale + translate).
     */
    @RequiresApi(Build.VERSION_CODES.S)
    override fun blurredWallpaper(radius: Float): RenderNode? {
        val env = envBuilder.environment
        val wp = env?.wallpaper ?: repository.currentWallpaper ?: return null
        if (wp.isRecycled) return null
        if (env != null) env.transform.toMatrix(tmpMatrix) else tmpMatrix.reset()
        tmpMatrix.getValues(tmpVals)

        val scale = tmpVals[Matrix.MSCALE_X]
        if (scale <= 0f ||
            tmpVals[Matrix.MSCALE_Y] != scale ||
            tmpVals[Matrix.MSKEW_X] != 0f || tmpVals[Matrix.MSKEW_Y] != 0f
        ) return null

        val w = kotlin.math.ceil(wp.width * scale).toInt()
        val h = kotlin.math.ceil(wp.height * scale).toInt()
        if (w <= 0 || h <= 0) return null

        blurDx = tmpVals[Matrix.MTRANS_X]
        blurDy = tmpVals[Matrix.MTRANS_Y]
        val node = sharedBlur?.get(wp, scale, radius, paint)
        return if (node != null && node.hasDisplayList()) node else null
    }

    override fun onBlurCacheUpdated(blurSizes: WallpaperBlurRenderer.BlurSizes?) {
        postInvalidate()
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
        val rawWp = environment?.wallpaper ?: repository.currentWallpaper

        if (environment != null) {
            environment.transform.toMatrix(matrix)
        } else {
            matrix.reset()
        }

        // Normal screen drawing: ALWAYS draw the clean, crisp wallpaper!
        if (rawWp != null) {
            canvas.drawBitmap(rawWp, matrix, paint)
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
