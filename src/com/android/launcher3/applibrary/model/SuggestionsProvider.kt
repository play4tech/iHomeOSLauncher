package com.android.launcher3.applibrary.model

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.model.data.AppInfo
import java.util.Calendar

class SuggestionsProvider(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("AppLibrarySuggestions", Context.MODE_PRIVATE)

    fun recordLaunch(appInfo: AppInfo) {
        val packageName = appInfo.componentName?.packageName ?: return
        val currentCount = prefs.getInt("count_$packageName", 0)
        prefs.edit()
            .putInt("count_$packageName", currentCount + 1)
            .putLong("last_launch_$packageName", System.currentTimeMillis())
            .apply()
    }

    fun getSuggestions(allApps: List<AppInfo>): List<AppInfo> {
        if (allApps.isEmpty()) return emptyList()

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        // Simple heuristic: combine launch count and last launch time
        // A more advanced version would use hour-of-day buckets.
        val scoredApps = allApps.mapNotNull { app ->
            val packageName = app.componentName?.packageName ?: return@mapNotNull null
            val count = prefs.getInt("count_$packageName", 0)
            val lastLaunch = prefs.getLong("last_launch_$packageName", 0L)
            
            // Score: higher count is better. Recent launch gives a small boost.
            val recencyBoost = if (System.currentTimeMillis() - lastLaunch < 1000 * 60 * 60 * 24) 5 else 0
            val score = count + recencyBoost
            
            Pair(app, score)
        }

        // Return top 4
        return scoredApps.sortedByDescending { it.second }.take(4).map { it.first }
    }
}
