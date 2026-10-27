package foundation.e.bliss.widgets.calendar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

class CalendarMonthAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // In-process host view renders directly; no RemoteViews update needed.
    }

    companion object {
        @JvmField
        val COMPONENT_NAME =
            ComponentName("com.ihome.launcher", CalendarMonthAppWidgetProvider::class.java.name)

        @JvmStatic
        fun isCalendarMonthWidget(provider: ComponentName?): Boolean {
            if (provider == null) return false
            return provider.className == CalendarMonthAppWidgetProvider::class.java.name ||
                provider == COMPONENT_NAME ||
                provider.shortClassName.endsWith("CalendarMonthAppWidgetProvider")
        }
    }
}
