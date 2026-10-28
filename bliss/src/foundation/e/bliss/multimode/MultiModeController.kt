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
package foundation.e.bliss.multimode

import android.content.Context
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import foundation.e.bliss.BaseController
import foundation.e.bliss.LauncherAppMonitor
import foundation.e.bliss.LauncherAppMonitorCallback
import foundation.e.bliss.preferences.BlissPrefs
import foundation.e.bliss.wallpaper.WallpaperEnvironmentBuilder
import java.io.FileDescriptor
import java.io.PrintWriter

class MultiModeController(val context: Context, val monitor: LauncherAppMonitor) : BaseController {
    private val idp by lazy { InvariantDeviceProfile.INSTANCE.get(context) }
    private val mAppMonitorCallback: LauncherAppMonitorCallback =
        object : LauncherAppMonitorCallback {
            override fun onLoadAllAppsEnd(apps: ArrayList<AppInfo?>?) {
                MODEL_EXECUTOR.execute(
                    VerifyIdleAppTask(
                        context,
                        apps,
                        null,
                        null,
                        false,
                        monitor.launcher.model.mBgDataModel
                    )
                )
            }

            override fun onAppSharedPreferenceChanged(key: String?) {
                when (key) {
                    BlissPrefs.PREF_SINGLE_LAYER_MODE -> {
                        updateSingleLayerCache()
                        monitor.launcher.model.forceReload()
                    }
                    BlissPrefs.PREF_NOTIF_COUNT -> idp.onConfigChanged(context)
                    else -> Unit
                }
            }

            override fun onLauncherOrientationChanged() {
                WallpaperEnvironmentBuilder.getInstanceNoCreate()?.onConfigurationChanged()
            }

            override fun dump(
                prefix: String?,
                fd: FileDescriptor?,
                w: PrintWriter?,
                dumpAll: Boolean
            ) {
                w?.let {
                    println()
                    println("$prefix $TAG: ${this@MultiModeController}")
                }
            }
        }

    init {
        prefs = LauncherPrefs.get(context)
        updateSingleLayerCache()
        monitor.registerCallback(mAppMonitorCallback)
    }

    override fun dumpState(
        prefix: String?,
        fd: FileDescriptor?,
        writer: PrintWriter?,
        dumpAll: Boolean
    ) {
        writer?.let {
            println()
            println("$prefix $TAG: ${this@MultiModeController}")
        }
    }

    companion object {
        private const val TAG = "MultiModeController"
        private lateinit var prefs: LauncherPrefs

        @Volatile
        private var sIsSingleLayerModeCached: Boolean = true

        @JvmStatic
        fun updateSingleLayerCache() {
            sIsSingleLayerModeCached = if (Companion::prefs.isInitialized) {
                prefs.get(LauncherPrefs.IS_SINGLE_LAYER_ENABLED)
            } else {
                true
            }
        }

        @JvmStatic
        val isSingleLayerMode: Boolean
            get() = sIsSingleLayerModeCached

        @JvmStatic
        val isNotifCountEnabled: Boolean
            get() = if (Companion::prefs.isInitialized) {
                prefs.get(LauncherPrefs.IS_NOTIF_COUNT_ENABLED)
            } else {
                true
            }
    }
}
