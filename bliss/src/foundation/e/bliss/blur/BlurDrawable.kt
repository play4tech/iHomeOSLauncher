package foundation.e.bliss.blur

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import foundation.e.bliss.wallpaper.WallpaperBlurRenderer
import foundation.e.bliss.wallpaper.WallpaperEnvironment
import foundation.e.bliss.wallpaper.WallpaperEnvironmentBuilder

class BlurDrawable internal constructor(
    private val context: Context,
    private val config: WallpaperBlurRenderer.BlurConfig = WallpaperBlurRenderer.blurConfigWidget
) : Drawable(), WallpaperBlurRenderer.Listener, WallpaperEnvironmentBuilder.Listener {

    private val blurRenderer = WallpaperBlurRenderer.INSTANCE.get(context)
    private val envBuilder = WallpaperEnvironmentBuilder.INSTANCE.get(context)

    private var blurAlpha = 255
    private val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val localOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val blurBounds = RectF()
    private val dstRect = RectF()
    
    private val shaderMatrix = Matrix()
    
    var overlayColor: Int = Color.TRANSPARENT

    override fun draw(canvas: Canvas) {
        val width = blurBounds.right - blurBounds.left
        val height = blurBounds.bottom - blurBounds.top
        
        if (width <= 0 || height <= 0 || blurAlpha == 0) return
        
        val blurSizes = blurRenderer.blurSizes
        val environment = envBuilder.environment
        
        if (blurSizes == null || environment == null) return
        
        val blurBitmap = config.getDrawable(blurSizes)
        
        // Prepare BitmapShader if needed
        if (blurPaint.shader == null || (blurPaint.shader as? BitmapShader)?.hashCode() != blurBitmap.hashCode()) {
            val shader = BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            blurPaint.shader = shader
        }

        // Calculate the shader matrix.
        // 1. Scale up the blurred bitmap by `config.scale` to match the raw wallpaper's coordinate space.
        // 2. Apply the global transform to map the raw wallpaper to the physical display space.
        // 3. Translate by negative bounds to map from the physical display space to this View's local coordinate space.
        shaderMatrix.reset()
        shaderMatrix.setScale(config.scale.toFloat(), config.scale.toFloat())
        
        val globalMatrix = Matrix()
        environment.transform.toMatrix(globalMatrix)
        shaderMatrix.postConcat(globalMatrix)
        
        shaderMatrix.postTranslate(-blurBounds.left, -blurBounds.top)
        
        blurPaint.shader?.setLocalMatrix(shaderMatrix)
        blurPaint.alpha = blurAlpha
        
        dstRect.set(0f, 0f, width, height)
        canvas.drawRect(dstRect, blurPaint)

        if (overlayColor != Color.TRANSPARENT) {
            localOverlayPaint.color = overlayColor
            canvas.drawRect(dstRect, localOverlayPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        blurAlpha = alpha
        blurPaint.alpha = alpha
    }

    override fun getAlpha() = blurAlpha

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) =
        setBlurBounds(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    fun setBlurBounds(left: Float, top: Float, right: Float, bottom: Float) {
        if (blurBounds.left != left || blurBounds.top != top ||
            blurBounds.right != right || blurBounds.bottom != bottom) {
            blurBounds.set(left, top, right, bottom)
            invalidateSelf()
        }
    }

    // Deprecated offsets, kept for API compatibility if any callers remain,
    // though they are no longer necessary as the global matrix handles it.
    fun setOffsets(offsetX: Float, offsetY: Float) {}

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onBlurCacheUpdated(blurSizes: WallpaperBlurRenderer.BlurSizes?) {
        invalidateSelf()
    }

    override fun onEnvironmentChanged(environment: WallpaperEnvironment?, bitmapChanged: Boolean) {
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    fun startListening() {
        blurRenderer.addListener(this)
        envBuilder.addListener(this)
    }

    fun stopListening() {
        blurRenderer.removeListener(this)
        envBuilder.removeListener(this)
    }
}
