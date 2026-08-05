package foundation.e.bliss.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.Insettable

import android.util.Log

class WallpaperProxyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), WallpaperEnvironmentBuilder.Listener, Insettable {

    private var hasLoggedDraw = false

    init {
        setWillNotDraw(false)
        Log.d("WallpaperProxyView", "WallpaperProxyView initialized")
    }

    private val envBuilder = WallpaperEnvironmentBuilder.INSTANCE.get(context)
    private val repository = WallpaperRepository.INSTANCE.get(context)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    override fun setInsets(insets: Rect) {
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d("WallpaperProxyView", "onAttachedToWindow: Proxy view attached to window")
        // The proxy can outlive a wallpaper change receiver registration in Workspace.
        // Request a fresh system bitmap whenever the glass source becomes active.
        repository.updateAsync()
        envBuilder.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d("WallpaperProxyView", "onDetachedFromWindow: Proxy view detached")
        envBuilder.removeListener(this)
    }

    override fun onEnvironmentChanged(environment: WallpaperEnvironment?, bitmapChanged: Boolean) {
        Log.d("WallpaperProxyView", "onEnvironmentChanged: environmentIsNull=${environment == null}, hasBitmap=${environment?.wallpaper != null}")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val environment = envBuilder.environment
        if (environment == null) {
            if (!hasLoggedDraw) {
                Log.w("WallpaperProxyView", "onDraw: environment is null, cannot draw wallpaper proxy")
                hasLoggedDraw = true
            }
            return
        }
        if (!hasLoggedDraw) {
            Log.d("WallpaperProxyView", "onDraw: drawing wallpaper bitmap (${environment.wallpaper.width}x${environment.wallpaper.height}) to proxy canvas")
            hasLoggedDraw = true
        }
        environment.transform.toMatrix(matrix)
        canvas.drawBitmap(environment.wallpaper, matrix, paint)
    }
}
