package com.android.launcher3.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * A FastBitmapDrawable that cross-fades between a source bitmap (e.g. collapsed mini-cluster icon)
 * and a target bitmap (e.g. expanded alphabetical app icon) during folder expansion and collapse.
 */
class CrossFadeBitmapDrawable(
    private val sourceBitmap: Bitmap,
    targetBitmap: Bitmap,
    iconColor: Int = 0
) : FastBitmapDrawable(targetBitmap, iconColor) {

    private val sourcePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var crossFadeProgress: Float = 0f // 0f = 100% source, 1f = 100% target

    fun setCrossFadeProgress(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (crossFadeProgress != p) {
            crossFadeProgress = p
            invalidateSelf()
        }
    }

    fun getCrossFadeProgress(): Float = crossFadeProgress

    override fun drawInternal(canvas: Canvas, bounds: Rect) {
        if (crossFadeProgress <= 0.001f) {
            sourcePaint.alpha = mPaint.alpha
            canvas.drawBitmap(sourceBitmap, null, bounds, sourcePaint)
        } else if (crossFadeProgress >= 0.999f) {
            super.drawInternal(canvas, bounds)
        } else {
            val baseAlpha = mPaint.alpha
            val targetAlpha = (baseAlpha * crossFadeProgress).toInt()
            val srcAlpha = (baseAlpha * (1f - crossFadeProgress)).toInt()

            sourcePaint.alpha = srcAlpha
            canvas.drawBitmap(sourceBitmap, null, bounds, sourcePaint)

            mPaint.alpha = targetAlpha
            super.drawInternal(canvas, bounds)
            mPaint.alpha = baseAlpha
        }
    }
}
