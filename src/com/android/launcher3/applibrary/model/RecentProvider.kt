package com.android.launcher3.applibrary.model

import android.content.Context
import android.content.pm.PackageManager
import com.android.launcher3.model.data.AppInfo

class RecentProvider(private val context: Context) {

    fun getRecentlyAdded(allApps: List<AppInfo>): List<AppInfo> {
        if (allApps.isEmpty()) return emptyList()

        val pm = context.packageManager
        
        val appsWithInstallTime = allApps.mapNotNull { app ->
            val pkg = app.componentName?.packageName ?: return@mapNotNull null
            try {
                val packageInfo = pm.getPackageInfo(pkg, 0)
                Pair(app, packageInfo.firstInstallTime)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        // Return top 8 most recently installed
        return appsWithInstallTime
            .sortedByDescending { it.second }
            .take(8)
            .map { it.first }
    }
}
