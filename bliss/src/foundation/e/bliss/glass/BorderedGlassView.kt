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

/**
 * Common interface for Liquid Glass surfaces that support an outline border.
 */
interface BorderedGlassView {
    // Dark outline border properties
    var borderEnabled: Boolean
    var borderWidthPx: Float
    var borderOpacity: Float
    var borderFade: Float
    var borderPosition: Int
    var borderFadeSpread: Float

    // White outline border properties
    var whiteBorderEnabled: Boolean
    var whiteBorderWidthPx: Float
    var whiteBorderOpacity: Float
    var whiteBorderFade: Float
    var whiteBorderPosition: Int
    var whiteBorderFadeSpread: Float
}
