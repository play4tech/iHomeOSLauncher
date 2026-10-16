package com.android.launcher3.applibrary.model

import android.content.Context
import android.content.SharedPreferences
import com.android.launcher3.model.data.AppInfo
import foundation.e.bliss.suggestions.AppUsageStats
import java.util.Calendar

class SuggestionsProvider(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("AppLibrarySuggestions", Context.MODE_PRIVATE)

    private val appUsageStats by lazy { AppUsageStats(context) }

    fun recordLaunch(packageName: String) {
        val currentCount = prefs.getInt("count_$packageName", 0)
        prefs.edit()
            .putInt("count_$packageName", currentCount + 1)
            .putLong("last_launch_$packageName", System.currentTimeMillis())
            .apply()
    }

    fun recordLaunch(appInfo: AppInfo) {
        val packageName = appInfo.componentName?.packageName ?: return
        recordLaunch(packageName)
    }

    fun getSuggestions(allApps: List<AppInfo>): List<AppInfo> {
        if (allApps.isEmpty()) return emptyList()

        // 1. Query Android system UsageStatsManager for actual foreground usage
        val usageScoreMap = mutableMapOf<String, Long>()
        try {
            val statsList = appUsageStats.usageStats
            for (stat in statsList) {
                usageScoreMap[stat.packageName] = stat.totalTimeInForeground
            }
        } catch (ignored: Throwable) {}

        val now = System.currentTimeMillis()

        // 2. Score each app by system foreground time + internal launcher launches + recency boost
        val scoredApps = allApps.map { app ->
            val pkg = app.componentName?.packageName ?: ""
            val internalCount = prefs.getInt("count_$pkg", 0)
            val lastLaunch = prefs.getLong("last_launch_$pkg", 0L)
            val recencyBoost =
                if (now - lastLaunch < 1000L * 60 * 60 * 24 && lastLaunch > 0) 1000L else 0L

            // 1 minute in foreground is comparable to 1 launch
            val fgMinutes = (usageScoreMap[pkg] ?: 0L) / (1000L * 60L)
            val totalScore = (fgMinutes * 10L) + (internalCount * 100L) + recencyBoost

            Pair(app, totalScore)
        }

        val sorted = scoredApps.sortedByDescending { it.second }
        val result = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (pair in sorted) {
            val app = pair.first
            val score = pair.second
            if (score <= 0) break
            val pkg = app.componentName?.packageName ?: continue
            if (seenPackages.add(pkg)) {
                result.add(app)
            }
            if (result.size >= 4) break
        }

        if (result.size < 4) {
            val defaultPriorityPackages = listOf(
                "com.android.dialer", "com.google.android.dialer",
                "com.android.mms", "com.google.android.apps.messaging",
                "com.android.chrome", "org.chromium.chrome", "foundation.e.browser",
                "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera",
                "com.google.android.apps.photos", "com.android.gallery3d",
                "com.android.settings"
            )
            for (pkg in defaultPriorityPackages) {
                if (result.size >= 4) break
                if (seenPackages.contains(pkg)) continue
                val entry = allApps.find { it.componentName?.packageName == pkg }
                if (entry != null) {
                    seenPackages.add(pkg)
                    result.add(entry)
                }
            }
        }

        if (result.size < 4) {
            for (app in allApps) {
                if (result.size >= 4) break
                val pkg = app.componentName?.packageName ?: continue
                if (seenPackages.add(pkg)) {
                    result.add(app)
                }
            }
        }
        return result
    }
}
