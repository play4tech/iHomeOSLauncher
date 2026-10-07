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
     * Returns a cached Path that perfectly represents a continuous corner shape (squircle). The
     * path is only regenerated if dimensions, radius, or smoothing change.
     */
    fun getPath(
        width: Float,
        height: Float,
        radius: Float,
        smoothing: Float = GlassShapeSpec.IOS_SMOOTHING
    ): Path {
        if (width <= 0f || height <= 0f) {
            cachedPath.rewind()
            return cachedPath
        }
        val halfMin = minOf(width, height) / 2f
        if (radius >= halfMin * 0.95f) {
            if (
                width == cachedWidth &&
                height == cachedHeight &&
                radius == cachedRadius &&
                smoothing == cachedSmoothing
            ) {
                return cachedPath
            }
            cachedWidth = width
            cachedHeight = height
            cachedRadius = radius
            cachedSmoothing = smoothing
            cachedPath.rewind()
            cachedPath.addRoundRect(0f, 0f, width, height, halfMin, halfMin, Path.Direction.CW)
            return cachedPath
        }

        // Clamp radius taking smoothing factor into account to prevent overlapping corner curves.
        // RoundedPolygon throws "Can't get the direction of a 0-length vector" when
        // corners overlap and collapse an edge to zero length.
        val maxSafeRadius = halfMin / (1f + smoothing * 0.5f)
        val safeRadius = radius.coerceIn(0f, maxSafeRadius)

        if (
            width == cachedWidth &&
                height == cachedHeight &&
                safeRadius == cachedRadius &&
                smoothing == cachedSmoothing
        ) {
            return cachedPath
        }

        cachedWidth = width
        cachedHeight = height
        cachedRadius = safeRadius
        cachedSmoothing = smoothing

        cachedPath.rewind()
        try {
            // Generate the underlying AndroidX geometry
            val polygon =
                RoundedPolygon.rectangle(
                    width = width,
                    height = height,
                    rounding = CornerRounding(safeRadius, smoothing)
                )

            // Convert polygon cubics to android.graphics.Path
            var first = true
            for (element in polygon.cubics) {
                if (first) {
                    cachedPath.moveTo(element.anchor0X, element.anchor0Y)
                    first = false
                }
                cachedPath.cubicTo(
                    element.control0X,
                    element.control0Y,
                    element.control1X,
                    element.control1Y,
                    element.anchor1X,
                    element.anchor1Y
                )
            }
            cachedPath.close()

            // RoundedPolygon centers shapes at (0,0). We must translate the path to (width/2,
            // height/2)
            // so its top-left corner matches the view's (0,0) coordinate system.
            cachedPath.offset(width / 2f, height / 2f)
        } catch (t: Throwable) {
            // Safe fallback to standard rounded rect if polygon generation fails on degenerate
            // dimensions
            cachedPath.rewind()
            cachedPath.addRoundRect(
                0f,
                0f,
                width,
                height,
                safeRadius,
                safeRadius,
                Path.Direction.CW
            )
        }

        return cachedPath
    }
}
