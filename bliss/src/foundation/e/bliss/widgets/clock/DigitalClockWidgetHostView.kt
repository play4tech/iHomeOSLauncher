package foundation.e.bliss.widgets.clock

import android.content.Context
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.billing.BillingManager
import foundation.e.bliss.widgets.GlassWidgetFactory

/**
 * 2x2 Glass Digital Seconds Clock Widget Host View.
 *
 * Backed by [ClockWidgetHostView] and [foundation.e.bliss.widgets.BaseGlassWidgetHostView]
 * which provides the live Liquid Glass squircle with dynamic optical refraction and wallpaper sampling.
 */
class DigitalClockWidgetHostView(context: Context) :
    ClockWidgetHostView(
        context,
        R.string.digital_clock_widget_label,
        DigitalClockView(context).apply {
            isClickable = false
            isFocusable = false
            elevation = 2f
        }
    ) {

    override fun performClick(): Boolean {
        if (!BillingManager.isProUser(context)) {
            val launcher = Launcher.getLauncher(context)
            GlassWidgetFactory.showProWidgetDialog(launcher, DigitalClockAppWidgetProvider.COMPONENT_NAME)
            return true
        }
        return super.performClick()
    }
}
