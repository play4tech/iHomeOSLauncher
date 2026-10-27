package foundation.e.bliss.widgets

import android.graphics.Rect
import foundation.e.bliss.glass.FolderGlassView

interface GlassWidgetHostView {
    fun getGlassView(): FolderGlassView?
    fun getSquircleBoundsInSelf(outRect: Rect)
    fun getSquircleRadius(): Float
}
