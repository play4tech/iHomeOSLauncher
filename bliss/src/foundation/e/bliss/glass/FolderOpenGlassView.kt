package foundation.e.bliss.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.android.launcher3.R
import com.qmdeve.liquidglass.widget.LiquidGlassView
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

class FolderOpenGlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    private val geometry = ContinuousCornerGeometry()
    private val cornerRadiusPx = 38f * context.resources.displayMetrics.density

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width <= 0 || view.height <= 0) return
                val path = geometry.getPath(
                    width = view.width.toFloat(),
                    height = view.height.toFloat(),
                    radius = cornerRadiusPx,
                    smoothing = GlassShapeSpec.IOS_SMOOTHING
                )
                outline.setConvexPath(path)
            }
        }
        clipToOutline = true
        // Shape handled by canvas clipping below; skip the library's own rounding.
        setCornerRadius(0f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isClickable = false
        isFocusable = false
        isEnabled = false
        bind(findWallpaperProxy())
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
            return
        }
        val path = geometry.getPath(width.toFloat(), height.toFloat(), cornerRadiusPx, GlassShapeSpec.IOS_SMOOTHING)
        canvas.save()
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    private fun findWallpaperProxy(): ViewGroup? {
        var p = parent
        while (p is ViewGroup) {
            val proxy = p.findViewById<View>(R.id.glass_wallpaper_proxy)
            if (proxy is ViewGroup) {
                return proxy
            }
            p = p.parent
        }
        return null
    }
}
