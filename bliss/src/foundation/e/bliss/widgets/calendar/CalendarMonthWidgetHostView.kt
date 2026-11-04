package foundation.e.bliss.widgets.calendar

import android.content.Context
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.billing.BillingManager
import foundation.e.bliss.widgets.BaseGlassWidgetHostView
import foundation.e.bliss.widgets.GlassWidgetFactory

class CalendarMonthWidgetHostView(context: Context) :
    BaseGlassWidgetHostView(
        context,
        R.string.calendar_widget_label,
        CalendarMonthView(context).apply {
            isClickable = false
            isFocusable = false
            elevation = 2f
        }
    ) {

    override fun performClick(): Boolean {
        if (!BillingManager.isProUser(context)) {
            val launcher = Launcher.getLauncher(context)
            GlassWidgetFactory.showProWidgetDialog(launcher, CalendarMonthAppWidgetProvider.COMPONENT_NAME)
            return true
        }
        return super.performClick()
    }
}
