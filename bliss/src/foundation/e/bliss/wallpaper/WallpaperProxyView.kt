package foundation.e.bliss.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WallpaperProxyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), WallpaperEnvironmentBuilder.Listener {

    private val envBuilder = WallpaperEnvironmentBuilder.INSTANCE.get(context)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        envBuilder.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        envBuilder.removeListener(this)
    }

    override fun onEnvironmentChanged(environment: WallpaperEnvironment?, bitmapChanged: Boolean) {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val environment = envBuilder.environment ?: return
        environment.transform.toMatrix(matrix)
        canvas.drawBitmap(environment.wallpaper, matrix, paint)
    }
}
