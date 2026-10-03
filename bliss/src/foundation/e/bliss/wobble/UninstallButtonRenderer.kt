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
package foundation.e.bliss.wobble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.res.ResourcesCompat
import com.android.launcher3.R

class UninstallButtonRenderer(private val mContext: Context, iconSizePx: Int) {
    private val mSize: Int
    private val mPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val mBadgeDrawable: foundation.e.bliss.glass.LiquidGlassMinusDrawable

    init {
        val density = mContext.resources.displayMetrics.density
        mSize = (SIZE_PERCENTAGE * iconSizePx).toInt().coerceAtLeast((22 * density).toInt())
        mBadgeDrawable = foundation.e.bliss.glass.LiquidGlassMinusDrawable(mContext)
    }

    fun draw(canvas: Canvas, iconBounds: Rect) {
        val halfSize = mSize / 2
        val badgeCenterX = (iconBounds.left + halfSize * 0.4f).toInt().coerceAtLeast(halfSize + 2)
        val badgeCenterY = (iconBounds.top + halfSize * 0.4f).toInt().coerceAtLeast(halfSize + 2)
        mBadgeDrawable.setBounds(
            badgeCenterX - halfSize,
            badgeCenterY - halfSize,
            badgeCenterX + halfSize,
            badgeCenterY + halfSize
        )
        mBadgeDrawable.draw(canvas)
    }

    /**
     * Provide a comfortable hit target centered at the badge center.
     *
     * @param iconBounds Bounds of the icon drawable
     * @return Scaled touch bounds for the minus button
     */
    fun getBoundsScaled(iconBounds: Rect): Rect {
        val halfSize = mSize / 2
        val badgeCenterX = (iconBounds.left + halfSize * 0.4f).toInt().coerceAtLeast(halfSize + 2)
        val badgeCenterY = (iconBounds.top + halfSize * 0.4f).toInt().coerceAtLeast(halfSize + 2)
        val density = mContext.resources.displayMetrics.density
        val touchRadius = ((24 * density).toInt()).coerceAtLeast(mSize)
        return Rect(
            badgeCenterX - touchRadius,
            badgeCenterY - touchRadius,
            badgeCenterX + touchRadius,
            badgeCenterY + touchRadius
        )
    }

    companion object {
        private const val SIZE_PERCENTAGE = 0.32f
    }
}
