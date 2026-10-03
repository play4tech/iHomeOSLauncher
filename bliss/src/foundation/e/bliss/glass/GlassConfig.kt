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

import android.graphics.Color
import com.example.liquidglass.GlassAccessibilityMode
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * Master configuration object defining single source of truth for all Liquid Glass surfaces across
 * the launcher (Dock, Closed Folder, Opened Folder, App Library, Search Bar).
 */
object GlassConfig {

    /** Default blur radius for standard liquid glass surfaces. */
    const val DEFAULT_BLUR_RADIUS: Float = 4f

    /** Default chromatic dispersion factor. */
    const val DEFAULT_DISPERSION: Float = 0.2f

    /** Default tint alpha for iOS frosted aesthetic. */
    const val DEFAULT_TINT_ALPHA: Float = 0.25f

    /** Default tint color RGB (pure white overlay). */
    const val DEFAULT_TINT_RED: Float = 1.0f
    const val DEFAULT_TINT_GREEN: Float = 1.0f
    const val DEFAULT_TINT_BLUE: Float = 1.0f

    /**
     * Applies standard master glass parameters to QWEA0 [LiquidGlassView].
     */
    @JvmStatic
    fun applyDefaultConfig(glassView: LiquidGlassView) {
        // --- Liquid Glass 2.0 lens pipeline (API 33+, silently no-ops below) ---
        glassView.material = GlassMaterial.REGULAR      // REGULAR (readability) | CLEAR (over media)
        glassView.refractionHeight = 160f               // edge displacement at the rim. px
        glassView.bevelWidth = 48f                      // glass "thickness" band. px, 2-200
        glassView.refractionFalloff = 2f                // inverse-power falloff: >0 = bending piles up at the rim, 0 = square bevel
        glassView.dispersionStrength = 0.10f            // rim spectral fringe, 0-1. >0.25 reads as rainbow
        glassView.enableEdgeHighlight = false           // remove artificial border lines
        glassView.enableSensorHighlight = false         // disable tilt-induced highlight discrepancies
        glassView.enableAdaptiveTint = true             // tint follows backdrop luminance

        glassView.blurAmount = 0.12f
        glassView.setGlassTint(Color.WHITE, DEFAULT_TINT_ALPHA)
        glassView.enablePressEffect = false
        glassView.enableDynamicBackground = false
        glassView.collectFrameStats = false
        glassView.accessibilityMode = GlassAccessibilityMode.AUTO
        glassView.cornerRadius = 0f
    }
}
