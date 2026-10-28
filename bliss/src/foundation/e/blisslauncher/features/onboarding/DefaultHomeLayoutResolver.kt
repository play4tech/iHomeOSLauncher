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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.RemovedAppStore
import foundation.e.bliss.multimode.MultiModeController

/**
 * Resolves standard Android roles and categories to populate an iOS-style curated layout
 * on first run. Places 2x2 Clock and 2x2 Calendar Day widgets at the top of Screen 0,
 * followed by curated app icons below them, keeping the last 2 icon slots on Screen 0 blank,
 * and grouping work profile apps into a "Work" folder.
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
                "com.oplus.gallery",
                "com.sec.android.gallery3d",
                "com.vivo.gallery",
                "com.miui.gallery",
                "com.oneplus.gallery"
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
                "com.ihome.launcher.features.weather",
                "foundation.e.blisslauncher.features.weather",
                "com.google.android.apps.weather",
                "com.coloros.weather",
                "com.coloros.weather2",
                "com.oplus.weather",
                "com.sec.android.daemonapp",
                "com.vivo.weather",
                "com.vivo.weather.provider",
                "com.miui.weather2",
                "net.oneplus.weather",
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
                "com.android.contacts",
                "com.vivo.contacts",
                "com.oneplus.contacts"
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
                "com.oplus.calculator",
                "com.sec.android.app.popupcalculator",
                "com.miui.calculator",
                "com.oneplus.calculator",
                "com.vivo.calculator",
                "com.android.bbkcalculator"
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
                "com.oplus.filemanager",
                "com.sec.android.app.myfiles",
                "com.mi.android.globalFileexplorer",
                "com.vivo.filemanager",
                "com.oneplus.filemanager"
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

        // Clear any old removed-app records so fresh setup starts completely clean
        RemovedAppStore.clearAll(context)

        val db = dbHelper.writableDatabase
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val numCols = idp.numColumns
        val numRows = idp.numRows

        val occupiedCellsByScreen = HashMap<Int, HashSet<Pair<Int, Int>>>()
        val existingComponents = HashSet<ComponentName>()
        val existingPackages = HashSet<String>()
        val existingComponentKeys = HashSet<ComponentKey>()

        val myUser = Process.myUserHandle()
        val userCache = UserCache.INSTANCE.get(context)
        val userSerial = userCache.getSerialNumberForUser(myUser)
        val userManager = context.getSystemService(UserManager::class.java)
        val allProfiles = userManager?.userProfiles ?: listOf(myUser)
        val launcherApps = context.getSystemService(LauncherApps::class.java)

        // 1. Inspect existing items in Favorites.TABLE_NAME_ALL (e.g. dock/hotseat items)
        val projection = arrayOf(
            Favorites._ID,
            Favorites.CONTAINER,
            Favorites.SCREEN,
            Favorites.CELLX,
            Favorites.CELLY,
            Favorites.SPANX,
            Favorites.SPANY,
            Favorites.INTENT,
            Favorites.PROFILE_ID,
            Favorites.ITEM_TYPE,
            Favorites.APPWIDGET_PROVIDER
        )
        var hasClockWidget = false
        var hasCalendarWidget = false
        val cursor = db.query(Favorites.TABLE_NAME_ALL, projection, null, null, null, null, null)
        cursor.use { c ->
            val colContainer = c.getColumnIndexOrThrow(Favorites.CONTAINER)
            val colScreen = c.getColumnIndexOrThrow(Favorites.SCREEN)
            val colCellX = c.getColumnIndexOrThrow(Favorites.CELLX)
            val colCellY = c.getColumnIndexOrThrow(Favorites.CELLY)
            val colSpanX = c.getColumnIndexOrThrow(Favorites.SPANX)
            val colSpanY = c.getColumnIndexOrThrow(Favorites.SPANY)
            val colIntent = c.getColumnIndexOrThrow(Favorites.INTENT)
            val colProfileId = c.getColumnIndexOrThrow(Favorites.PROFILE_ID)
            val colItemType = c.getColumnIndex(Favorites.ITEM_TYPE)
            val colProvider = c.getColumnIndex(Favorites.APPWIDGET_PROVIDER)

            while (c.moveToNext()) {
                val container = c.getLong(colContainer)
                val screen = c.getInt(colScreen)
                val cellX = c.getInt(colCellX)
                val cellY = c.getInt(colCellY)
                val spanX = c.getInt(colSpanX).coerceAtLeast(1)
                val spanY = c.getInt(colSpanY).coerceAtLeast(1)
                val intentStr = c.getString(colIntent)
                val profileSerial = c.getLong(colProfileId)
                val itemUser = userCache.getUserForSerialNumber(profileSerial) ?: myUser
                val itemType = if (colItemType >= 0) c.getInt(colItemType) else -1
                val providerStr = if (colProvider >= 0) c.getString(colProvider) else null

                if (itemType == Favorites.ITEM_TYPE_APPWIDGET && !providerStr.isNullOrEmpty()) {
                    if (providerStr.contains("ClockAppWidgetProvider") || providerStr.contains("DigitalClockAppWidgetProvider")) {
                        hasClockWidget = true
                    }
                    if (providerStr.contains("CalendarDayAppWidgetProvider") || providerStr.contains("CalendarMonthAppWidgetProvider")) {
                        hasCalendarWidget = true
                    }
                }

                if (container == Favorites.CONTAINER_DESKTOP.toLong()) {
                    val occupied = occupiedCellsByScreen.getOrPut(screen) { HashSet() }
                    for (x in cellX until (cellX + spanX)) {
                        for (y in cellY until (cellY + spanY)) {
                            occupied.add(Pair(x, y))
                        }
                    }
                }

                if (!intentStr.isNullOrEmpty()) {
                    try {
                        val parsedIntent = Intent.parseUri(intentStr, 0)
                        parsedIntent.component?.let {
                            existingComponents.add(it)
                            existingPackages.add(it.packageName)
                            existingComponentKeys.add(ComponentKey(it, itemUser))
                        } ?: run {
                            parsedIntent.`package`?.let { existingPackages.add(it) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing intent: $intentStr", e)
                    }
                }
            }
        }

        // 1b. Place 2x2 Clock widget (top-left) and 2x2 Calendar Day widget (top-right) at top of Screen 0
        val clockProvider = ComponentName(context.packageName, "foundation.e.bliss.widgets.clock.ClockAppWidgetProvider")
        val calendarProvider = ComponentName(context.packageName, "foundation.e.bliss.widgets.calendar.CalendarDayAppWidgetProvider")

        val widgetHolder = dbHelper.newLauncherWidgetHolder()
        val appWidgetManager = AppWidgetManager.getInstance(context)

        fun placeWidgetIfMissing(cn: ComponentName, screen: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) {
            try {
                val appWidgetId = widgetHolder.allocateAppWidgetId()
                val bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn)
                if (bound) {
                    val values = ContentValues().apply {
                        put(Favorites._ID, dbHelper.generateNewItemId())
                        put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET)
                        put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                        put(Favorites.SCREEN, screen)
                        put(Favorites.CELLX, cellX)
                        put(Favorites.CELLY, cellY)
                        put(Favorites.SPANX, spanX)
                        put(Favorites.SPANY, spanY)
                        put(Favorites.APPWIDGET_ID, appWidgetId)
                        put(Favorites.APPWIDGET_PROVIDER, cn.flattenToString())
                        put(Favorites.APPWIDGET_SOURCE, Favorites.CONTAINER_DESKTOP)
                        put(Favorites.PROFILE_ID, userSerial)
                        put(Favorites.MODIFIED, System.currentTimeMillis())
                    }
                    dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, values)
                    val occupied = occupiedCellsByScreen.getOrPut(screen) { HashSet() }
                    for (x in cellX until (cellX + spanX)) {
                        for (y in cellY until (cellY + spanY)) {
                            occupied.add(Pair(x, y))
                        }
                    }
                    Log.d(TAG, "Placed widget $cn at screen=$screen ($cellX, $cellY, ${spanX}x${spanY}) id=$appWidgetId")
                } else {
                    Log.w(TAG, "bindAppWidgetIdIfAllowed returned false for $cn")
                    widgetHolder.deleteAppWidgetId(appWidgetId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to place widget $cn", e)
            }
        }

        db.beginTransaction()
        try {
            if (!hasClockWidget) {
                placeWidgetIfMissing(clockProvider, 0, 0, 0, 2, 2)
            }
            if (!hasCalendarWidget) {
                placeWidgetIfMissing(calendarProvider, 0, 2, 0, 2, 2)
            }

            val workActivities = ArrayList<LauncherActivityInfo>()
            val personalActivities = ArrayList<LauncherActivityInfo>()

            for (profile in allProfiles) {
                val userInfo = userCache.getUserInfo(profile)
                val isWork = userInfo.isWork
                val isPrivate = userInfo.isPrivate
                val acts = launcherApps?.getActivityList(null, profile) ?: emptyList()
                if (isWork) {
                    workActivities.addAll(acts)
                } else if (profile == myUser && !isPrivate) {
                    personalActivities.addAll(acts)
                }
            }

            // 2. Resolve curated roles for personal profile
            val curatedToPlace = ArrayList<ComponentName>()
            for (role in CURATED_ROLES) {
                val resolved = resolveRole(context, role, launcherApps, myUser)
                if (resolved != null) {
                    val key = ComponentKey(resolved, myUser)
                    if (existingComponentKeys.contains(key) || existingPackages.contains(resolved.packageName)) {
                        Log.d(TAG, "Role ${role.roleName} resolved to $resolved but already in DB (deduped).")
                    } else {
                        curatedToPlace.add(resolved)
                        existingComponents.add(resolved)
                        existingPackages.add(resolved.packageName)
                        existingComponentKeys.add(key)
                        Log.d(TAG, "Role ${role.roleName} resolved to $resolved to be placed on Home Screen.")
                    }
                } else {
                    Log.d(TAG, "Role ${role.roleName} not found on device.")
                }
            }

            // 3. Collect remaining personal activities (sorted alphabetically)
            val remainingPersonalToPlace = ArrayList<Pair<ComponentName, Long>>()
            val sortedPersonal = personalActivities.sortedBy { it.label?.toString()?.lowercase() ?: "" }
            for (act in sortedPersonal) {
                val comp = act.componentName
                val key = ComponentKey(comp, act.user)
                if (!existingComponentKeys.contains(key)) {
                    val serial = userCache.getSerialNumberForUser(act.user)
                    remainingPersonalToPlace.add(Pair(comp, serial))
                    existingComponentKeys.add(key)
                    existingComponents.add(comp)
                }
            }

            // 4. Reserve the last 2 cells on Screen 0 so it stays clean and not too crowded
            val reservedCellsScreen0 = HashSet<Pair<Int, Int>>()
            val totalCellsScreen0 = numCols * numRows
            for (idx in (totalCellsScreen0 - 2) until totalCellsScreen0) {
                if (idx >= 0) {
                    val rx = idx % numCols
                    val ry = idx / numCols
                    reservedCellsScreen0.add(Pair(rx, ry))
                }
            }

            var currentScreen = 0
            var currentX = 0
            var currentY = 0

            fun getNextAvailableCell(): Triple<Int, Int, Int> {
                while (true) {
                    val occupiedOnScreen = occupiedCellsByScreen.getOrPut(currentScreen) { HashSet() }
                    val cell = Pair(currentX, currentY)
                    val isReserved = (currentScreen == 0 && reservedCellsScreen0.contains(cell))
                    val isOccupied = occupiedOnScreen.contains(cell)

                    if (!isOccupied && !isReserved) {
                        val result = Triple(currentScreen, currentX, currentY)
                        occupiedOnScreen.add(cell)
                        currentX++
                        if (currentX >= numCols) {
                            currentX = 0
                            currentY++
                            if (currentY >= numRows) {
                                currentScreen++
                                currentX = 0
                                currentY = 0
                            }
                        }
                        return result
                    }

                    currentX++
                    if (currentX >= numCols) {
                        currentX = 0
                        currentY++
                        if (currentY >= numRows) {
                            currentScreen++
                            currentX = 0
                            currentY = 0
                        }
                    }
                }
            }

            // 5. Place curated personal apps
            for (component in curatedToPlace) {
                val (screen, cellX, cellY) = getNextAvailableCell()
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setComponent(component)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                val values = ContentValues().apply {
                    put(Favorites._ID, dbHelper.generateNewItemId())
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                    put(Favorites.SCREEN, screen)
                    put(Favorites.CELLX, cellX)
                    put(Favorites.CELLY, cellY)
                    put(Favorites.SPANX, 1)
                    put(Favorites.SPANY, 1)
                    put(Favorites.INTENT, launchIntent.toUri(0))
                    put(Favorites.PROFILE_ID, userSerial)
                    put(Favorites.MODIFIED, System.currentTimeMillis())
                }
                dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, values)
            }

            // 6. Place Work folder if work profile apps exist
            if (workActivities.isNotEmpty()) {
                val (folderScreen, folderCellX, folderCellY) = getNextAvailableCell()
                val folderId = dbHelper.generateNewItemId()
                val folderValues = ContentValues().apply {
                    put(Favorites._ID, folderId)
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER)
                    put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                    put(Favorites.SCREEN, folderScreen)
                    put(Favorites.CELLX, folderCellX)
                    put(Favorites.CELLY, folderCellY)
                    put(Favorites.SPANX, 1)
                    put(Favorites.SPANY, 1)
                    put(Favorites.TITLE, "Work")
                    put(Favorites.PROFILE_ID, userSerial)
                    put(Favorites.MODIFIED, System.currentTimeMillis())
                }
                dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, folderValues)

                var workRank = 0
                for (workAct in workActivities) {
                    val workUserSerial = userCache.getSerialNumberForUser(workAct.user)
                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setComponent(workAct.componentName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    val itemValues = ContentValues().apply {
                        put(Favorites._ID, dbHelper.generateNewItemId())
                        put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                        put(Favorites.CONTAINER, folderId)
                        put(Favorites.SCREEN, 0)
                        put(Favorites.CELLX, 0)
                        put(Favorites.CELLY, 0)
                        put(Favorites.SPANX, 1)
                        put(Favorites.SPANY, 1)
                        put(Favorites.RANK, workRank++)
                        put(Favorites.INTENT, launchIntent.toUri(0))
                        put(Favorites.PROFILE_ID, workUserSerial)
                        put(Favorites.MODIFIED, System.currentTimeMillis())
                    }
                    dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, itemValues)
                    existingComponents.add(workAct.componentName)
                    existingPackages.add(workAct.componentName.packageName)
                }
                Log.d(TAG, "Created 'Work' folder on screen $folderScreen with ${workActivities.size} apps.")
            }

            // 7. Place all remaining personal apps across screens
            for ((component, serial) in remainingPersonalToPlace) {
                val (screen, cellX, cellY) = getNextAvailableCell()
                val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setComponent(component)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                val values = ContentValues().apply {
                    put(Favorites._ID, dbHelper.generateNewItemId())
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                    put(Favorites.SCREEN, screen)
                    put(Favorites.CELLX, cellX)
                    put(Favorites.CELLY, cellY)
                    put(Favorites.SPANX, 1)
                    put(Favorites.SPANY, 1)
                    put(Favorites.INTENT, launchIntent.toUri(0))
                    put(Favorites.PROFILE_ID, serial)
                    put(Favorites.MODIFIED, System.currentTimeMillis())
                }
                dbHelper.dbInsertAndCheck(db, Favorites.TABLE_NAME_ALL, values)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        Log.d(TAG, "Completed default layout population: all apps placed, Screen 0 has Clock and Calendar widgets at top and last 2 slots blank.")
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
