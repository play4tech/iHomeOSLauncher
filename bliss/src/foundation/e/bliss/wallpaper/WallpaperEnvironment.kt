package foundation.e.bliss.wallpaper

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF

data class WallpaperTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float
) {
    fun toMatrix(out: Matrix) {
        out.setScale(scaleX, scaleY)
        out.postTranslate(translateX, translateY)
    }
}

data class WallpaperEnvironment(
    val wallpaper: Bitmap,
    val displaySize: Point,
    val wallpaperSize: Point,
    val transform: WallpaperTransform,
    val cropRect: RectF,
    val parallaxOffset: Float
)
