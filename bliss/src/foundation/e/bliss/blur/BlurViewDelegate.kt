package foundation.e.bliss.blur

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import androidx.core.graphics.toRectF
import com.android.launcher3.R
import foundation.e.bliss.wallpaper.WallpaperBlurRenderer

class BlurViewDelegate(
    private val view: View,
    private val config: WallpaperBlurRenderer.BlurConfig,
    attrs: AttributeSet? = null
) : View.OnAttachStateChangeListener {

    private val context = view.context

    private var fullBlurDrawable: BlurDrawable? = null
    private var blurAlpha = 255

    private val blurDrawableCallback by lazy {
        object : Drawable.Callback {
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {}

            override fun invalidateDrawable(who: Drawable) {
                view.post(view::invalidate)
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {}
        }
    }

    private val locationInWindow = IntArray(2)

    private val onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateBounds() }
    private val onScrollChangedListener = ViewTreeObserver.OnScrollChangedListener { updateBounds() }
    
    // Kept for API compatibility with parents that might still notify it
    var offsetParentListener: Any? = null

    var blurCornerRadius = 0f

    val outlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, blurCornerRadius)
            }
        }

    var overlayColor: Int = 0
        set(value) {
            field = value
            overlayPaint.color = value
            fullBlurDrawable?.overlayColor = value
        }

    private val overlayPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    init {
        createFullBlurDrawable()
        view.addOnAttachStateChangeListener(this)

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.BlurLayout)
            blurCornerRadius = a.getDimension(R.styleable.BlurLayout_blurCornerRadius, 0f)
            overlayColor = a.getColor(R.styleable.BlurLayout_blurOverlayColor, 0x40FFFFFF)
            a.recycle()
        }
    }

    override fun onViewAttachedToWindow(v: View) {
        fullBlurDrawable?.startListening()
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        view.viewTreeObserver.addOnScrollChangedListener(onScrollChangedListener)
        updateBounds()
    }

    override fun onViewDetachedFromWindow(v: View) {
        fullBlurDrawable?.stopListening()
        view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        view.viewTreeObserver.removeOnScrollChangedListener(onScrollChangedListener)
    }

    fun draw(canvas: Canvas) {
        updateBounds()
        fullBlurDrawable?.apply {
            alpha = blurAlpha
            this.draw(canvas)
        }
        if (overlayColor != 0) {
            canvas.drawRoundRect(canvas.clipBounds.toRectF(), blurCornerRadius, blurCornerRadius, overlayPaint)
        }
    }

    private fun createFullBlurDrawable() {
        fullBlurDrawable?.let { if (view.isAttachedToWindow) it.stopListening() }
        fullBlurDrawable = BlurDrawable(context, config).apply {
            callback = blurDrawableCallback
            overlayColor = this@BlurViewDelegate.overlayColor
            if (view.isAttachedToWindow) startListening()
        }
        updateBounds()
    }

    private fun updateBounds() {
        if (view.width == 0 || view.height == 0) return
        view.getLocationInWindow(locationInWindow)
        val left = locationInWindow[0]
        val top = locationInWindow[1]
        fullBlurDrawable?.setBlurBounds(
            left.toFloat(),
            top.toFloat(),
            (left + view.width).toFloat(),
            (top + view.height).toFloat()
        )
    }
}
