package foundation.e.bliss.graphics

import android.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle

class ContinuousCornerGeometry {

    private var cachedWidth: Float = -1f
    private var cachedHeight: Float = -1f
    private var cachedRadius: Float = -1f
    private var cachedSmoothing: Float = -1f

    private val cachedPath = Path()

    /**
     * Returns a cached Path that perfectly represents a continuous corner shape (squircle).
     * The path is only regenerated if dimensions, radius, or smoothing change.
     */
    fun getPath(width: Float, height: Float, radius: Float, smoothing: Float = GlassShapeSpec.IOS_SMOOTHING): Path {
        if (width <= 0f || height <= 0f) {
            cachedPath.rewind()
            return cachedPath
        }
        // Clamp radius so it never exceeds half the shortest side.
        // RoundedPolygon throws "Can't get the direction of a 0-length vector" when
        // corners overlap and collapse a side to zero length.
        val safeRadius = radius.coerceAtMost(minOf(width, height) / 2f)

        if (width == cachedWidth && height == cachedHeight &&
            safeRadius == cachedRadius && smoothing == cachedSmoothing) {
            return cachedPath
        }

        cachedWidth = width
        cachedHeight = height
        cachedRadius = safeRadius
        cachedSmoothing = smoothing

        // Generate the underlying AndroidX geometry
        val polygon = RoundedPolygon.rectangle(
            width = width,
            height = height,
            rounding = CornerRounding(safeRadius, smoothing)
        )

        // Convert polygon cubics to android.graphics.Path
        cachedPath.rewind()
        var first = true
        for (element in polygon.cubics) {
            if (first) {
                cachedPath.moveTo(element.anchor0X, element.anchor0Y)
                first = false
            }
            cachedPath.cubicTo(
                element.control0X, element.control0Y,
                element.control1X, element.control1Y,
                element.anchor1X, element.anchor1Y
            )
        }
        cachedPath.close()

        // RoundedPolygon centers shapes at (0,0). We must translate the path to (width/2, height/2)
        // so its top-left corner matches the view's (0,0) coordinate system.
        cachedPath.offset(width / 2f, height / 2f)

        return cachedPath
    }
}
