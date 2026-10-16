/*
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */
package foundation.e.bliss.suggestions

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import com.android.launcher3.R
import foundation.e.bliss.utils.Logger
import java.util.Calendar

class AppUsageStats(private val mContext: Context) {
    private val mUsageStatsManager by lazy {
        mContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    private val mAppOps by lazy {
        mContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    }

    // Cache permission state for 15 seconds to avoid repeated IPC
    private var lastPermissionCheckTime = 0L
    private var cachedHasPermission = false
    private var hasLoggedPermissionDenied = false

    // Cache usage stats result for 30 seconds
    private var lastStatsQueryTime = 0L
    private var cachedUsageStats: List<UsageStats> = emptyList()

    private fun hasPermission(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPermissionCheckTime < PERMISSION_CACHE_DURATION_MS) {
            return cachedHasPermission
        }

        lastPermissionCheckTime = now
        val appOps = mAppOps ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            mContext.packageName
        )
        val granted = mode == AppOpsManager.MODE_ALLOWED
        cachedHasPermission = granted
        return granted
    }

    val usageStats: List<UsageStats>
        get() {
            // 1. Permission check FIRST before doing heavy work
            if (!hasPermission()) {
                if (!hasLoggedPermissionDenied) {
                    Logger.i(TAG, "The user has not granted access to apps usage.")
                    hasLoggedPermissionDenied = true
                }
                return emptyList()
            }

            // User granted permission, reset flag in case permission is revoked later
            hasLoggedPermissionDenied = false

            // 2. Return cached stats if still fresh
            val now = SystemClock.elapsedRealtime()
            if (now - lastStatsQueryTime < STATS_CACHE_DURATION_MS && cachedUsageStats.isNotEmpty()) {
                return cachedUsageStats
            }

            val usageStatsManager = mUsageStatsManager ?: return emptyList()

            try {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -30) // Query last 30 days instead of 1 full year for faster query

                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    cal.timeInMillis,
                    System.currentTimeMillis()
                ) ?: emptyList()

                if (stats.isEmpty()) {
                    cachedUsageStats = emptyList()
                    lastStatsQueryTime = now
                    return emptyList()
                }

                val aggregatedStats = mutableMapOf<String, UsageStats>()
                for (newStat in stats) {
                    val existing = aggregatedStats[newStat.packageName]
                    if (existing == null) {
                        aggregatedStats[newStat.packageName] = newStat
                    } else {
                        existing.add(newStat)
                    }
                }

                if (aggregatedStats.isEmpty()) {
                    cachedUsageStats = emptyList()
                    lastStatsQueryTime = now
                    return emptyList()
                }

                // Batch resolve launchable packages once instead of N separate IPC calls
                val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val launchablePackages = mContext.packageManager
                    .queryIntentActivities(launchIntent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()

                val blacklistedApps = try {
                    mContext.resources.getStringArray(R.array.blacklisted_apps).toSet()
                } catch (_: Exception) {
                    emptySet()
                }

                val result = aggregatedStats.values
                    .filter { stat ->
                        !blacklistedApps.contains(stat.packageName) &&
                            launchablePackages.contains(stat.packageName) &&
                            stat.totalTimeInForeground > 0
                    }
                    .sortedByDescending { it.totalTimeInForeground }

                cachedUsageStats = result
                lastStatsQueryTime = now
                return result
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to query app usage stats", e)
                return emptyList()
            }
        }

    companion object {
        private const val TAG = "AppUsageStats"
        private const val PERMISSION_CACHE_DURATION_MS = 15_000L
        private const val STATS_CACHE_DURATION_MS = 30_000L
    }
}
