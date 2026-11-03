package foundation.e.bliss.widgets.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.view.View
import com.android.launcher3.R
import foundation.e.bliss.widgets.BaseGlassWidgetHostView

/**
 * 2x2 Glass Analog Clock Widget Host View.
 *
 * Backed by [BaseGlassWidgetHostView] which provides the live Liquid Glass squircle
 * with dynamic optical refraction and wallpaper sampling, identical to CalendarDayWidgetHostView.
 */
open class ClockWidgetHostView @JvmOverloads constructor(
    context: Context,
    labelResId: Int = R.string.clock_widget_label,
    contentView: View = IosAnalogClockView(context).apply {
        isClickable = false
        isFocusable = false
        elevation = 2f
    }
) : BaseGlassWidgetHostView(context, labelResId, contentView) {

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
