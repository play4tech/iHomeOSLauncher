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
package foundation.e.bliss.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Authentic iOS-styled Liquid Glass circular minus ('-') badge used on app icons and widgets in jiggle mode.
 * Features ultra-translucent glass body, specular refraction rim, inner lens bevel, and high-contrast white minus bar.
 */
class LiquidGlassMinusDrawable(private val context: Context) : Drawable() {

    private val density = context.resources.displayMetrics.density

    // Ambient drop shadow for separation against bright backgrounds
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Highly translucent liquid glass body
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Specular refraction rim simulating light refracting through circular lens bevel
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Subtle inner bevel highlight ring for 3D glass thickness
    private val innerBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(40, 255, 255, 255)
    }

    // Crisp white minus symbol with soft drop shadow for readability on light icons
    private val minusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var cachedBounds = Rect()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return
        cachedBounds.set(bounds)

        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        val rimStroke = maxOf(1.2f * density, radius * 0.14f)
        val minusStroke = maxOf(2.0f * density, radius * 0.22f)

        rimPaint.strokeWidth = rimStroke
        innerBevelPaint.strokeWidth = maxOf(0.8f * density, rimStroke * 0.6f)
        minusPaint.strokeWidth = minusStroke
        minusPaint.setShadowLayer(1.5f * density, 0f, 0.6f * density, Color.argb(90, 0, 0, 0))

        // Liquid glass body gradient: very lightly white tinted & highly translucent (12% to 24% alpha)
        bodyPaint.shader = LinearGradient(
            cx - radius, cy - radius,
            cx + radius, cy + radius,
            intArrayOf(
                Color.argb(60, 255, 255, 255), // Top-left specular sheen
                Color.argb(28, 255, 255, 255), // Center clear refraction window
                Color.argb(48, 255, 255, 255)  // Bottom-right rim reflection
            ),
            floatArrayOf(0.0f, 0.45f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // Glass specular rim: brilliant refraction highlight on upper-left, subtle reflection on lower-right
        rimPaint.shader = LinearGradient(
            cx - radius, cy - radius,
            cx + radius, cy + radius,
            intArrayOf(
                Color.argb(230, 255, 255, 255), // Brilliant specular highlight (90% white)
                Color.argb(70, 255, 255, 255),  // Soft refraction dispersion
                Color.argb(110, 255, 255, 255), // Internal reflection
                Color.argb(175, 255, 255, 255)  // Secondary rim bounce
            ),
            floatArrayOf(0.0f, 0.40f, 0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = minOf(b.width(), b.height()) / 2f
        if (radius <= 0f) return

        val rimStroke = rimPaint.strokeWidth
        val outerRadius = radius - rimStroke / 2f
        val innerRadius = outerRadius - rimStroke * 0.9f

        // 1. Ambient soft depth shadow
        canvas.drawCircle(cx, cy + 0.6f * density, radius - 0.5f * density, shadowPaint)

        // 2. Translucent liquid glass lens body
        canvas.drawCircle(cx, cy, radius - 0.5f, bodyPaint)

        // 3. Specular rim highlight & refraction edge
        canvas.drawCircle(cx, cy, outerRadius, rimPaint)

        // 4. Inner bevel thickness ring
        if (innerRadius > 0f) {
            canvas.drawCircle(cx, cy, innerRadius, innerBevelPaint)
        }

        // 5. Crisp white minus symbol
        val minusHalfLen = radius * 0.42f
        canvas.drawLine(cx - minusHalfLen, cy, cx + minusHalfLen, cy, minusPaint)
    }

    override fun setAlpha(alpha: Int) {
        bodyPaint.alpha = (alpha * (48f / 255f)).toInt()
        rimPaint.alpha = alpha
        innerBevelPaint.alpha = (alpha * (40f / 255f)).toInt()
        minusPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        minusPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
