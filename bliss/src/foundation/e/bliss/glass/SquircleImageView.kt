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
import android.graphics.Outline
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * An [AppCompatImageView] that clips its canvas to the authentic homescreen squircle shape
 * (superellipse n = 5.0, x^5 + y^5 = r^5), matching [com.android.launcher3.icons.BaseIconFactory] and iOS geometry.
 */
class SquircleImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val clipPath = Path()
    private var lastWidth = -1
    private var lastHeight = -1

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val cornerRadius = view.width * 0.225f
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateClipPath(w, h)
    }

    private fun updateClipPath(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (w == lastWidth && h == lastHeight) return
        lastWidth = w
        lastHeight = h

        clipPath.reset()
        val rx = w / 2f
        val ry = h / 2f
        val n = 5.0f
        for (i in 0..360) {
            val t = (i / 180f * Math.PI).toFloat()
            val cosT = cos(t.toDouble())
            val sinT = sin(t.toDouble())
            val x = (Math.pow(abs(cosT), 2.0 / n) * rx * (if (cosT < 0) -1 else if (cosT > 0) 1 else 0)).toFloat()
            val y = (Math.pow(abs(sinT), 2.0 / n) * ry * (if (sinT < 0) -1 else if (sinT > 0) 1 else 0)).toFloat()
            if (i == 0) {
                clipPath.moveTo(x + rx, y + ry)
            } else {
                clipPath.lineTo(x + rx, y + ry)
            }
        }
        clipPath.close()
    }

    override fun draw(canvas: Canvas) {
        if (!clipPath.isEmpty) {
            val count = canvas.save()
            canvas.clipPath(clipPath)
            super.draw(canvas)
            canvas.restoreToCount(count)
        } else {
            super.draw(canvas)
        }
    }
}
