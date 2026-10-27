package foundation.e.bliss.widgets.calendar

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

class CalendarDayAppWidgetProvider : AppWidgetProvider() {

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
            ComponentName("com.ihome.launcher", CalendarDayAppWidgetProvider::class.java.name)

        @JvmStatic
        fun isCalendarDayWidget(provider: ComponentName?): Boolean {
            if (provider == null) return false
            return provider.className == CalendarDayAppWidgetProvider::class.java.name ||
                provider == COMPONENT_NAME ||
                provider.shortClassName.endsWith("CalendarDayAppWidgetProvider")
        }
    }
}
