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
        // In-process DigitalClockWidgetHostView renders directly; no RemoteViews update needed.
    }

    companion object {
        @JvmField
        val COMPONENT_NAME =
            ComponentName("com.ihomeos.launcher", DigitalClockAppWidgetProvider::class.java.name)
        val LEGACY_COMPONENT_NAME =
            ComponentName("com.ihome.launcher", DigitalClockAppWidgetProvider::class.java.name)

        @JvmStatic
        fun isDigitalClockWidget(provider: ComponentName?): Boolean {
            if (provider == null) return false
            val className = provider.className
            val shortClassName = provider.shortClassName
            return className == DigitalClockAppWidgetProvider::class.java.name ||
                provider == COMPONENT_NAME ||
                provider == LEGACY_COMPONENT_NAME ||
                shortClassName == ".DigitalClockAppWidgetProvider" ||
                shortClassName == "DigitalClockAppWidgetProvider"
        }
    }
}
