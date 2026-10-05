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
 */

package foundation.e.blisslauncher.features.onboarding

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.RemovedAppStore
import foundation.e.bliss.multimode.MultiModeController

/**
 * Resolves standard Android roles and categories to populate an iOS-style curated Page 0
 * on first run. Any apps not placed on Page 0 or in the dock are recorded in RemovedAppStore
 * so they remain accessible exclusively in the App Library.
 */
object DefaultHomeLayoutResolver {
    private const val TAG = "DefaultHomeResolver"

    private class RoleDescriptor(
        val roleName: String,
        val intents: List<Intent>,
        val packageFallbacks: List<String> = emptyList()
    )

    private val CURATED_ROLES = listOf(
        RoleDescriptor(
            "Photos",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
                Intent(Intent.ACTION_VIEW).setType("image/*")
            ),
            listOf(
                "com.google.android.apps.photos",
                "com.android.gallery3d",
                "com.coloros.gallery3d",
                "com.sec.android.gallery3d"
            )
        ),
        RoleDescriptor(
            "Calendar",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            ),
            listOf(
                "com.google.android.calendar",
                "com.android.calendar",
                "com.bbk.calendar"
            )
        ),
        RoleDescriptor(
            "Clock",
            listOf(
                Intent(AlarmClock.ACTION_SHOW_ALARMS),
                Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK")
            ),
            listOf(
                "com.google.android.deskclock",
                "com.android.deskclock",
                "com.coloros.alarmclock",
                "com.sec.android.app.clockpackage"
            )
        ),
        RoleDescriptor(
            "Maps",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            ),
            listOf(
                "com.google.android.apps.maps"
            )
        ),
        RoleDescriptor(
            "Weather",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_WEATHER")
            ),
            listOf(
                "foundation.e.blisslauncher.features.weather",
                "com.google.android.apps.weather",
                "com.coloros.weather",
                "com.sec.android.daemonapp",
                "com.accuweather.android"
            )
        ),
        RoleDescriptor(
            "Mail",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
                Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
            ),
            listOf(
                "com.google.android.gm",
                "com.android.email",
                "com.fsck.k9"
            )
        ),
        RoleDescriptor(
            "Contacts",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS),
                Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            ),
            listOf(
                "com.google.android.contacts",
                "com.android.contacts"
            )
        ),
        RoleDescriptor(
            "Calculator",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            ),
            listOf(
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.coloros.calculator",
                "com.sec.android.app.popupcalculator"
            )
        ),
        RoleDescriptor(
            "Files",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES),
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            ),
            listOf(
                "com.google.android.documentsui",
                "com.android.documentsui",
                "com.google.android.apps.nbu.files",
                "com.coloros.filemanager",
                "com.sec.android.app.myfiles"
            )
        ),
        RoleDescriptor(
            "Music",
            listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            ),
            listOf(
                "com.google.android.apps.youtube.music",
                "com.android.music",
                "com.spotify.music"
            )
        ),
        RoleDescriptor(
            "Play Store",
            emptyList(),
            listOf(
                "com.android.vending"
            )
        ),
        RoleDescriptor(
            "Settings",
            listOf(
                Intent(Settings.ACTION_SETTINGS)
            ),
            listOf(
                "com.android.settings"
            )
        )
    )

    @JvmStatic
    fun populateCuratedPage0(context: Context, dbHelper: DatabaseHelper) {
        if (!MultiModeController.isSingleLayerMode) {
            return
        }

        val db = dbHelper.writableDatabase
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val numCols = idp.numColumns
        val numRows = idp.numRows

        val occupiedCells = HashSet<Pair<Int, Int>>()
        val existingComponents = HashSet<ComponentName>()
        val existingPackages = HashSet<String>()

        // 1. Inspect existing items in Favorites.TABLE_NAME_ALL
        val projection = arrayOf(
            Favorites._ID,
            Favorites.CONTAINER,
            Favorites.SCREEN,
            Favorites.CELLX,
            Favorites.CELLY,
            Favorites.SPANX,
            Favorites.SPANY,
            Favorites.INTENT
        )
        val cursor = db.query(Favorites.TABLE_NAME_ALL, projection, null, null, null, null, null)
        cursor.use { c ->
            val colContainer = c.getColumnIndexOrThrow(Favorites.CONTAINER)
            val colScreen = c.getColumnIndexOrThrow(Favorites.SCREEN)
            val colCellX = c.getColumnIndexOrThrow(Favorites.CELLX)
            val colCellY = c.getColumnIndexOrThrow(Favorites.CELLY)
            val colSpanX = c.getColumnIndexOrThrow(Favorites.SPANX)
            val colSpanY = c.getColumnIndexOrThrow(Favorites.SPANY)
            val colIntent = c.getColumnIndexOrThrow(Favorites.INTENT)

            while (c.moveToNext()) {
                val container = c.getLong(colContainer)
                val screen = c.getInt(colScreen)
                val cellX = c.getInt(colCellX)
                val cellY = c.getInt(colCellY)
                val spanX = c.getInt(colSpanX).coerceAtLeast(1)
                val spanY = c.getInt(colSpanY).coerceAtLeast(1)
                val intentStr = c.getString(colIntent)

                // If on Desktop screen 0, mark all cells as occupied
                if (container == Favorites.CONTAINER_DESKTOP.toLong() && screen == 0) {
                    for (x in cellX until (cellX + spanX)) {
                        for (y in cellY until (cellY + spanY)) {
                            occupiedCells.add(Pair(x, y))
                        }
                    }
                }

                if (!intentStr.isNullOrEmpty()) {
                    try {
                        val parsedIntent = Intent.parseUri(intentStr, 0)
                        parsedIntent.component?.let {
                            existingComponents.add(it)
                            existingPackages.add(it.packageName)
                        } ?: run {
                            parsedIntent.`package`?.let { existingPackages.add(it) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing intent: $intentStr", e)
                    }
                }
            }
        }

        val myUser = Process.myUserHandle()
        val userSerial = UserCache.INSTANCE.get(context).getSerialNumberForUser(myUser)
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val allActivities = launcherApps?.getActivityList(null, myUser) ?: emptyList()

        // 2. Resolve each curated role
        val resolvedToPlace = ArrayList<ComponentName>()
        for (role in CURATED_ROLES) {
            val resolved = resolveRole(context, role, launcherApps, myUser)
            if (resolved != null) {
                if (existingComponents.contains(resolved) || existingPackages.contains(resolved.packageName)) {
                    Log.d(TAG, "Role ${role.roleName} resolved to $resolved but already in DB (deduped).")
                } else {
                    resolvedToPlace.add(resolved)
                    existingComponents.add(resolved)
                    existingPackages.add(resolved.packageName)
                    Log.d(TAG, "Role ${role.roleName} resolved to $resolved to be placed on Page 0.")
                }
            } else {
                Log.d(TAG, "Role ${role.roleName} not found on device.")
            }
        }

        // 3. Place resolved apps into available cells on Page 0 (row by row, skipping occupied)
        var currentX = 0
        var currentY = 0
        for (component in resolvedToPlace) {
            var placed = false
            while (currentY < numRows) {
                while (currentX < numCols) {
                    val cell = Pair(currentX, currentY)
                    if (!occupiedCells.contains(cell)) {
                        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            setComponent(component)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        }
                        val values = ContentValues().apply {
                            put(Favorites._ID, dbHelper.generateNewItemId())
                            put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                            put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                            put(Favorites.SCREEN, 0)
                            put(Favorites.CELLX, currentX)
                            put(Favorites.CELLY, currentY)
                            put(Favorites.SPANX, 1)
                            put(Favorites.SPANY, 1)
                            put(Favorites.INTENT, launchIntent.toUri(0))
                            put(Favorites.PROFILE_ID, userSerial)
                            put(Favorites.MODIFIED, System.currentTimeMillis())
                        }
                        dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, values)
                        occupiedCells.add(cell)
                        placed = true
                        currentX++
                        break
                    }
                    currentX++
                }
                if (placed) break
                currentX = 0
                currentY++
            }
            if (!placed) {
                Log.w(TAG, "Screen 0 full, cannot place $component")
                break
            }
        }

        // 4. Mark all other installed apps in RemovedAppStore so VerifyIdleAppTask won't create additional pages
        for (act in allActivities) {
            val comp = act.componentName
            if (!existingComponents.contains(comp)) {
                RemovedAppStore.markRemoved(context, comp.flattenToString())
                Log.d(TAG, "Marked unplaced app in RemovedAppStore: $comp")
            }
        }
    }

    private fun resolveRole(
        context: Context,
        role: RoleDescriptor,
        launcherApps: LauncherApps?,
        user: android.os.UserHandle
    ): ComponentName? {
        val pm = context.packageManager

        // First try standard intents
        for (intent in role.intents) {
            try {
                val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfo?.activityInfo != null) {
                    val pkg = resolveInfo.activityInfo.packageName
                    val comp = findLauncherActivity(launcherApps, pkg, user)
                    if (comp != null) return comp
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // Second try known package fallbacks
        for (pkg in role.packageFallbacks) {
            val comp = findLauncherActivity(launcherApps, pkg, user)
            if (comp != null) return comp
        }

        // Third: for Weather, search installed packages containing 'weather'
        if (role.roleName == "Weather") {
            val all = launcherApps?.getActivityList(null, user) ?: emptyList()
            for (act in all) {
                if (act.applicationInfo.packageName.contains("weather", ignoreCase = true)) {
                    return act.componentName
                }
            }
        }

        return null
    }

    private fun findLauncherActivity(
        launcherApps: LauncherApps?,
        packageName: String,
        user: android.os.UserHandle
    ): ComponentName? {
        if (launcherApps == null) return null
        try {
            val activities = launcherApps.getActivityList(packageName, user)
            if (activities.isNotEmpty()) {
                return activities[0].componentName
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }
}
