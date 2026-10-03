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

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.android.launcher3.R
import foundation.e.bliss.utils.Logger
import java.util.Calendar

class AppUsageStats(private val mContext: Context) {
    private val mUsageStatsManager
        get() = mContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val usageStats: List<UsageStats>
        get() {
            val usageStats = mutableListOf<UsageStats>()
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, -1)

            val stats =
                mUsageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    cal.timeInMillis,
                    System.currentTimeMillis()
                )

            val aggregatedStats = mutableMapOf<String, UsageStats>()
            val statCount = stats.size

            for (i in 0 until statCount) {
                val newStat = stats[i]
                val existingStat = aggregatedStats[newStat.packageName]

                if (existingStat == null) {
                    aggregatedStats[newStat.packageName] = newStat
                } else {
                    existingStat.add(newStat)
                }
            }

            val appOps =
                mContext.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode =
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    mContext.packageName
                )
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                Logger.i(TAG, "The user has not granted access to apps usage.")
                return usageStats
            } else if (aggregatedStats.isNotEmpty()) {
                val statsMap = aggregatedStats.entries

                statsMap
                    .toList()
                    .filter {
                        !mContext.resources
                            .getStringArray(R.array.blacklisted_apps)
                            .contains(it.key) &&
                            mContext.packageManager.getLaunchIntentForPackage(it.key) != null &&
                            it.value.totalTimeInForeground > 0
                    }
                    .apply {
                        sortedWith(
                                Comparator.comparingLong { (_, stat) -> stat.totalTimeInForeground }
                            )
                            .reversed()
                            .forEach { (_, stat) -> usageStats.add(stat) }
                    }
            } else {
                Logger.i(TAG, "The aggregatedStats are empty can't do much")
            }

            return usageStats
        }

    companion object {
        private const val TAG = "AppUsageStats"
    }
}
