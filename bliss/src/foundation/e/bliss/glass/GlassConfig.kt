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

import com.qmdeve.liquidglass.widget.LiquidGlassView

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
     * Applies standard master glass parameters to any [LiquidGlassView]. Enforces uniform optical
     * blur, dispersion, tint, and disables drag/elastic/touch physics.
     */
    @JvmStatic
    fun applyDefaultConfig(glassView: LiquidGlassView) {
        glassView.setBlurRadius(DEFAULT_BLUR_RADIUS)
        glassView.setDispersion(DEFAULT_DISPERSION)
        glassView.setTintAlpha(DEFAULT_TINT_ALPHA)
        glassView.setTintColorRed(DEFAULT_TINT_RED)
        glassView.setTintColorGreen(DEFAULT_TINT_GREEN)
        glassView.setTintColorBlue(DEFAULT_TINT_BLUE)
        glassView.setDraggableEnabled(false)
        glassView.setElasticEnabled(false)
        glassView.setTouchEffectEnabled(false)
        glassView.setCornerRadius(0f)
    }
}
