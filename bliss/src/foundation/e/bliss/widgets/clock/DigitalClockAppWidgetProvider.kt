package foundation.e.bliss.widgets.clock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

class DigitalClockAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // In-process ClockWidgetHostView renders directly; no RemoteViews update needed.
    }

    companion object {
        @JvmField
        val COMPONENT_NAME =
            ComponentName("com.ihome.launcher", DigitalClockAppWidgetProvider::class.java.name)

        @JvmStatic
        fun isDigitalClockWidget(provider: ComponentName?): Boolean {
            if (provider == null) return false
            return provider.className == DigitalClockAppWidgetProvider::class.java.name ||
                provider == COMPONENT_NAME ||
                provider.shortClassName.endsWith("DigitalClockAppWidgetProvider")
        }
    }
}
