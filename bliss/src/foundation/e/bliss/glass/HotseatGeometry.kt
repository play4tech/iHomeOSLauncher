package foundation.e.bliss.glass

import android.graphics.Rect

/**
 * Immutable data class representing the precise visual geometry of the Hotseat dock.
 * This is computed exactly once by DeviceProfile to guarantee all consumers
 * (Hotseat, DockGlassView, PageIndicator) remain perfectly synchronized.
 */
data class HotseatGeometry(
    val bounds: Rect,         // Exact position and size of the floating dock/glass
    val contentBounds: Rect,  // Exact placement of the icons/content within the dock
    val height: Int,          // Convenience property for bounds height
    val cornerRadius: Float,  // Dynamic corner radius
    val pageIndicatorY: Int   // Pre-calculated Y coordinate for the WorkspacePageIndicator
)
