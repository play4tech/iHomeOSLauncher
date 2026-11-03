package foundation.e.bliss.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import com.android.launcher3.widget.LauncherAppWidgetHostView
import foundation.e.bliss.widgets.calendar.CalendarDayAppWidgetProvider
import foundation.e.bliss.widgets.calendar.CalendarDayWidgetHostView
import foundation.e.bliss.widgets.calendar.CalendarMonthAppWidgetProvider
import foundation.e.bliss.widgets.calendar.CalendarMonthWidgetHostView
import foundation.e.bliss.widgets.clock.ClockAppWidgetProvider
import foundation.e.bliss.widgets.clock.ClockWidgetHostView
import foundation.e.bliss.widgets.clock.DigitalClockAppWidgetProvider
import foundation.e.bliss.widgets.clock.DigitalClockWidgetHostView

object GlassWidgetFactory {

    @JvmStatic
    fun isGlassWidget(provider: ComponentName?): Boolean {
        if (provider == null) return false
        return ClockAppWidgetProvider.isClockWidget(provider) ||
                DigitalClockAppWidgetProvider.isDigitalClockWidget(provider) ||
                CalendarDayAppWidgetProvider.isCalendarDayWidget(provider) ||
                CalendarMonthAppWidgetProvider.isCalendarMonthWidget(provider)
    }

    @JvmStatic
    fun createHostView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): LauncherAppWidgetHostView? {
        val provider = appWidget?.provider ?: return null
        return when {
            DigitalClockAppWidgetProvider.isDigitalClockWidget(provider) -> {
                DigitalClockWidgetHostView(context).apply { setAppWidget(appWidgetId, appWidget) }
            }
            ClockAppWidgetProvider.isClockWidget(provider) -> {
                ClockWidgetHostView(context).apply { setAppWidget(appWidgetId, appWidget) }
            }
            CalendarDayAppWidgetProvider.isCalendarDayWidget(provider) -> {
                CalendarDayWidgetHostView(context).apply { setAppWidget(appWidgetId, appWidget) }
            }
            CalendarMonthAppWidgetProvider.isCalendarMonthWidget(provider) -> {
                CalendarMonthWidgetHostView(context).apply { setAppWidget(appWidgetId, appWidget) }
            }
            else -> null
        }
    }
}
