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

import android.graphics.Rect

/**
 * Immutable data class representing the precise visual geometry of the Hotseat dock. This is
 * computed exactly once by DeviceProfile to guarantee all consumers (Hotseat, DockGlassView,
 * PageIndicator) remain perfectly synchronized.
 */
data class HotseatGeometry(
    val bounds: Rect, // Exact position and size of the floating dock/glass
    val contentBounds: Rect, // Exact placement of the icons/content within the dock
    val height: Int, // Convenience property for bounds height
    val cornerRadius: Float, // Dynamic corner radius
    val pageIndicatorY: Int, // Pre-calculated Y coordinate for the WorkspacePageIndicator
    val indicatorMargin: Int = 0 // Margin between dock top and workspace page indicator
)
