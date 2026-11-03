package foundation.e.bliss.widgets.clock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

class ClockAppWidgetProvider : AppWidgetProvider() {

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
            ComponentName("com.ihomeos.launcher", ClockAppWidgetProvider::class.java.name)
        val LEGACY_COMPONENT_NAME =
            ComponentName("com.ihome.launcher", ClockAppWidgetProvider::class.java.name)

        @JvmStatic
        fun isClockWidget(provider: ComponentName?): Boolean {
            if (provider == null) return false
            val className = provider.className
            val shortClassName = provider.shortClassName
            return className == ClockAppWidgetProvider::class.java.name ||
                provider == COMPONENT_NAME ||
                provider == LEGACY_COMPONENT_NAME ||
                shortClassName == ".ClockAppWidgetProvider" ||
                shortClassName == "ClockAppWidgetProvider"
        }
    }
}
