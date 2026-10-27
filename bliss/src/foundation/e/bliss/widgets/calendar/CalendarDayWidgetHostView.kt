package foundation.e.bliss.widgets.calendar

import android.content.Context
import android.view.View
import com.android.launcher3.R
import foundation.e.bliss.widgets.BaseGlassWidgetHostView

class CalendarDayWidgetHostView(context: Context) :
    BaseGlassWidgetHostView(
        context,
        R.string.calendar_widget_label,
        CalendarDayView(context).apply {
            isClickable = false
            isFocusable = false
            elevation = 2f
        }
    )
