package foundation.e.bliss.widgets

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.android.launcher3.R
import com.android.launcher3.billing.BillingManager
import com.android.launcher3.widget.LauncherAppWidgetHostView
import foundation.e.bliss.widgets.calendar.CalendarDayAppWidgetProvider
import foundation.e.bliss.widgets.calendar.CalendarDayWidgetHostView
import foundation.e.bliss.widgets.calendar.CalendarMonthAppWidgetProvider
import foundation.e.bliss.widgets.calendar.CalendarMonthWidgetHostView
import foundation.e.bliss.widgets.clock.ClockAppWidgetProvider
import foundation.e.bliss.widgets.clock.ClockWidgetHostView
import foundation.e.bliss.widgets.clock.DigitalClockAppWidgetProvider
import foundation.e.bliss.widgets.clock.DigitalClockWidgetHostView
import kotlin.math.roundToInt

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
    fun isProWidget(provider: ComponentName?): Boolean {
        if (provider == null) return false
        return DigitalClockAppWidgetProvider.isDigitalClockWidget(provider) ||
                CalendarMonthAppWidgetProvider.isCalendarMonthWidget(provider)
    }

    @JvmStatic
    fun getWidgetName(provider: ComponentName?): String {
        return when {
            DigitalClockAppWidgetProvider.isDigitalClockWidget(provider) -> "Digital Clock"
            CalendarMonthAppWidgetProvider.isCalendarMonthWidget(provider) -> "Calendar Month"
            ClockAppWidgetProvider.isClockWidget(provider) -> "Analog Clock"
            CalendarDayAppWidgetProvider.isCalendarDayWidget(provider) -> "Calendar Day"
            else -> "Widget"
        }
    }

    @JvmStatic
    fun showProWidgetDialog(activity: Activity, provider: ComponentName?) {
        val widgetName = getWidgetName(provider)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setContentView(R.layout.dialog_pro_required)

        val width = (activity.resources.displayMetrics.widthPixels * 0.85f).roundToInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val bm = BillingManager.getInstance(activity)
        val price = bm.getFormattedPrice()
        val btnUpgrade = dialog.findViewById<TextView>(R.id.btn_dialog_upgrade)
        if (btnUpgrade != null && price.isNotEmpty()) {
            btnUpgrade.text = "Upgrade to Pro ($price)"
        }

        val txtTitle = dialog.findViewById<TextView>(R.id.txt_dialog_title)
        if (txtTitle != null) {
            txtTitle.text = "$widgetName Widget"
        }

        val txtMessage = dialog.findViewById<TextView>(R.id.txt_dialog_message)
        if (txtMessage != null) {
            txtMessage.text = "The $widgetName widget is an exclusive iHomeOS Pro feature. Upgrade to Pro to add and use this widget on your Home Screen."
        }

        val icon = dialog.findViewById<ImageView>(R.id.img_dialog_icon)
        if (icon != null) {
            icon.setImageResource(R.drawable.ic_ios_pro_star)
        }

        btnUpgrade?.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(activity, "Connecting to Google Play...", Toast.LENGTH_SHORT).show()
            bm.launchPurchaseFlow(activity) { error ->
                activity.runOnUiThread {
                    Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.findViewById<View>(R.id.btn_dialog_cancel)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
