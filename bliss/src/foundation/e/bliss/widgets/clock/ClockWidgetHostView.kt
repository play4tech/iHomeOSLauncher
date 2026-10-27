package foundation.e.bliss.widgets.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.android.launcher3.R
import foundation.e.bliss.widgets.BaseGlassWidgetHostView

/**
 * 2x2 Glass Clock Widget Host View.
 *
 * Backed by [BaseGlassWidgetHostView] which provides the live Liquid Glass squircle
 * with dynamic optical refraction and wallpaper sampling, identical to CalendarDayWidgetHostView.
 */
class ClockWidgetHostView(context: Context) :
    BaseGlassWidgetHostView(
        context,
        R.string.clock_widget_label,
        DigitalClockView(context).apply {
            isClickable = false
            isFocusable = false
            elevation = 2f
        }
    ) {

    override fun performClick(): Boolean {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
            val fallback = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                ?: context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                ?: context.packageManager.getLaunchIntentForPackage("com.coloros.alarm")
                ?: context.packageManager.getLaunchIntentForPackage("com.heytap.clock")
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                return true
            }
        } catch (ignored: Throwable) {}
        return super.performClick()
    }
}
