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
 *
 * Configured with the official default liquid glass values from QWEA0/Liquid-Glass-Android (iOS 26 Liquid Glass).
 */
object GlassConfig {

    /** Default blur amount (1/16th) matching QWEA0 LiquidGlassView default. */
    const val DEFAULT_BLUR_AMOUNT: Float = 0.0625f

    /** Default chromatic dispersion factor matching QWEA0 LiquidGlassView default. */
    const val DEFAULT_DISPERSION: Float = 0.10f

    /**
     * Applies standard master glass parameters from QWEA0 [LiquidGlassView].
     */
    @JvmStatic
    fun applyDefaultConfig(glassView: LiquidGlassView) {
        // --- Liquid Glass 2.0 lens pipeline (API 33+, silently no-ops below) ---
        glassView.material = GlassMaterial.REGULAR      // REGULAR (readability) | CLEAR (over media)
        glassView.refractionHeight = 160f               // edge displacement at the rim. px (default: 160f)
        glassView.bevelWidth = 48f                      // glass "thickness" band. px, 2-200 (default: 48f)
        glassView.refractionFalloff = 2f                // inverse-power falloff: >0 = bending piles up at the rim (default: 2f)
        glassView.dispersionStrength = 0.10f            // rim spectral fringe, 0-1 (default: 0.10f)
        glassView.enableEdgeHighlight = true            // signature iOS specular edge highlight (default: true)
        glassView.edgeHighlightBorderWidth = 1.5f       // border highlight width in px (default: 1.5f)
        glassView.edgeHighlightOpacity = 100f           // specular highlight opacity (default: 100f)
        glassView.enableSensorHighlight = true          // specular highlight tracks device tilt via gravity sensor (default in iOS showcase)
        glassView.enableAdaptiveTint = true             // tint follows backdrop luminance dynamically (default: true)

        // --- Optical blur, saturation, aberration & physics defaults ---
        glassView.blurAmount = 0.0625f                  // QWEA0 library default (1/16 = 0.0625f)
        glassView.saturation = 140f                     // QWEA0 library default (140% saturation boost)
        glassView.aberrationIntensity = 2f              // QWEA0 library default (2f)
        glassView.displacementScale = 70f               // QWEA0 library default (70f)
        glassView.elasticity = 0.15f                    // QWEA0 library default (0.15f)
        glassView.glassTint = Color.TRANSPARENT         // QWEA0 library default (TRANSPARENT, no milky wash)
        glassView.useShaderPipeline = true              // AGSL lens pipeline on API 33+
        glassView.adaptiveLensScale = true              // adaptive lens scale for smaller components (default: true)
        glassView.refractionNoFold = false              // fold-back lens profile creates edge compression ring (default: false)
        glassView.refractionOutward = false             // inward sampling matches iOS optics (default: false)
        glassView.edgeSoftness = 0f                     // edge softness (default: 0f)

        glassView.enablePressEffect = false             // disable press scaling on structural launcher panels
        glassView.enableDynamicBackground = true        // required so glass captures backdrop dynamically
        glassView.collectFrameStats = false
        glassView.accessibilityMode = GlassAccessibilityMode.AUTO
        glassView.cornerRadius = 0f
    }
}
