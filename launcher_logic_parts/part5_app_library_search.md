# Part 5: App Library, Spotlight Search, Categories & Indexing

Modular code logic bundle designed to stay within Gemini attachment size limits (16 files).

## Table of Contents

- [src/com/android/launcher3/applibrary/model/CategoryProvider.kt](#srccomandroidlauncher3applibrarymodelCategoryProviderkt)
- [src/com/android/launcher3/applibrary/model/AppLibraryModel.kt](#srccomandroidlauncher3applibrarymodelAppLibraryModelkt)
- [src/com/android/launcher3/applibrary/model/AppLibraryCategory.kt](#srccomandroidlauncher3applibrarymodelAppLibraryCategorykt)
- [src/com/android/launcher3/applibrary/model/SuggestionsProvider.kt](#srccomandroidlauncher3applibrarymodelSuggestionsProviderkt)
- [src/com/android/launcher3/applibrary/view/SpotlightSearchOverlay.java](#srccomandroidlauncher3applibraryviewSpotlightSearchOverlayjava)
- [src/com/android/launcher3/applibrary/view/AppLibraryDashboardView.java](#srccomandroidlauncher3applibraryviewAppLibraryDashboardViewjava)
- [src/com/android/launcher3/applibrary/view/AppLibraryPage.java](#srccomandroidlauncher3applibraryviewAppLibraryPagejava)
- [src/com/android/launcher3/applibrary/view/AppLibraryBodyContainer.java](#srccomandroidlauncher3applibraryviewAppLibraryBodyContainerjava)
- [src/com/android/launcher3/applibrary/view/CategoryCardView.java](#srccomandroidlauncher3applibraryviewCategoryCardViewjava)
- [src/com/android/launcher3/applibrary/view/MiniClusterView.java](#srccomandroidlauncher3applibraryviewMiniClusterViewjava)
- [src/com/android/launcher3/applibrary/view/AppLibraryCategoryExpandedView.java](#srccomandroidlauncher3applibraryviewAppLibraryCategoryExpandedViewjava)
- [src/com/android/launcher3/applibrary/view/AppLibraryIndexScrollerView.java](#srccomandroidlauncher3applibraryviewAppLibraryIndexScrollerViewjava)
- [src/com/android/launcher3/applibrary/view/ChangeCategoryPickerView.kt](#srccomandroidlauncher3applibraryviewChangeCategoryPickerViewkt)
- [src/com/android/launcher3/applibrary/view/AppLibraryFolderTransition.kt](#srccomandroidlauncher3applibraryviewAppLibraryFolderTransitionkt)
- [src/com/android/launcher3/applibrary/view/ExpandedCategoryAdapter.java](#srccomandroidlauncher3applibraryviewExpandedCategoryAdapterjava)
- [src/com/android/launcher3/applibrary/view/AppLibrarySearchAdapter.java](#srccomandroidlauncher3applibraryviewAppLibrarySearchAdapterjava)

---

# src/com/android/launcher3/applibrary/model/CategoryProvider.kt

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/model/CategoryProvider.kt`  
**Lines**: 494 | **Bytes**: 24803

```kotlin
package com.android.launcher3.applibrary.model

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.UserHandle
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.pm.UserCache

class CategoryProvider(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "app_library_category_overrides"
        private const val RESOLVED_CACHE_PREFS = "app_library_resolved_categories"

        @Volatile
        private var INSTANCE: CategoryProvider? = null

        @JvmStatic
        fun getInstance(context: Context): CategoryProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CategoryProvider(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, AppLibraryCategory>()
    private val diskPrefs by lazy {
        context.getSharedPreferences(RESOLVED_CACHE_PREFS, Context.MODE_PRIVATE)
    }

    init {
        try {
            val all = diskPrefs.all
            for ((key, value) in all) {
                if (value is String) {
                    try {
                        categoryCache[key] = AppLibraryCategory.valueOf(value)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    @Volatile
    private var bulkAppCategoryMap: Map<String, Int>? = null

    private fun getAppInfoCategory(packageName: String): Int? {
        var map = bulkAppCategoryMap
        if (map == null) {
            val newMap = mutableMapOf<String, Int>()
            try {
                val pm = context.packageManager
                val installed = pm.getInstalledApplications(0)
                for (app in installed) {
                    newMap[app.packageName.lowercase()] = app.category
                }
            } catch (e: Exception) {
                // fallback if getInstalledApplications fails
            }
            bulkAppCategoryMap = newMap
            map = newMap
        }
        return map?.get(packageName) ?: try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (e: Exception) {
            null
        }
    }

    fun getCategory(itemInfo: ItemInfo): AppLibraryCategory {
        val packageName = (itemInfo.targetComponent?.packageName ?: itemInfo.targetPackage ?: "").lowercase().trim()
        val appName = itemInfo.title?.toString()?.lowercase()?.trim() ?: ""
        return getCategory(packageName, appName, itemInfo.user)
    }

    fun getCategory(appInfo: AppInfo): AppLibraryCategory {
        val packageName = (appInfo.componentName?.packageName ?: appInfo.targetPackage ?: "").lowercase().trim()
        val appName = appInfo.title?.toString()?.lowercase()?.trim() ?: ""
        return getCategory(packageName, appName, appInfo.user)
    }

    fun getCategory(packageName: String, appName: String, user: UserHandle?): AppLibraryCategory {
        val normPkg = packageName.lowercase().trim()
        if (normPkg.isEmpty()) return AppLibraryCategory.OTHER

        val cacheKey = "$normPkg#${user?.hashCode() ?: 0}"
        categoryCache[cacheKey]?.let { return it }

        val category = resolveCategory(normPkg, appName.lowercase().trim(), user)
        if (appName.isNotBlank() || category != AppLibraryCategory.OTHER) {
            categoryCache[cacheKey] = category
            try {
                diskPrefs.edit().putString(cacheKey, category.name).apply()
            } catch (e: Exception) {
                // ignore
            }
        }
        return category
    }

    private fun resolveCategory(packageName: String, appName: String, user: UserHandle?): AppLibraryCategory {
        // 0. User Category Override (Highest priority)
        getUserCategoryOverride(packageName, user)?.let { return it }

        // 1. Work Profile App
        if (user != null) {
            try {
                if (UserCache.getInstance(context).getUserInfo(user).isWork) {
                    return AppLibraryCategory.WORK
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 2. Known Package DB (Highest priority for accuracy)
        getCategoryByPackage(packageName)?.let { return it }

        // 3. Developer Rules (e.g. Adobe, Microsoft)
        getCategoryByDeveloper(packageName)?.let { return it }

        // 4. Keyword Rules based on App Name
        getCategoryByKeyword(appName)?.let { return it }

        // 5. ApplicationInfo.category (API 26+)
        val appCat = getAppInfoCategory(packageName)
        if (appCat != null) {
            getCategoryByAppInfoCategory(appCat, appName, packageName)?.let { return it }
        }

        // 6. Fallback
        return AppLibraryCategory.OTHER
    }

    fun getUserCategoryOverride(packageName: String, user: UserHandle?): AppLibraryCategory? {
        val normPkg = packageName.lowercase().trim()
        if (normPkg.isEmpty()) return null
        val userSerial = if (user != null) {
            try {
                UserCache.getInstance(context).getSerialNumberForUser(user)
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val catName = prefs.getString("${userSerial}#$normPkg", null)
            ?: prefs.getString(normPkg, null)
        if (catName != null) {
            return try {
                AppLibraryCategory.valueOf(catName)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun setCategoryOverride(packageName: String, user: UserHandle?, category: AppLibraryCategory) {
        val normPkg = packageName.lowercase().trim()
        if (normPkg.isEmpty()) return
        val cacheKey = "$normPkg#${user?.hashCode() ?: 0}"
        categoryCache[cacheKey] = category
        try {
            diskPrefs.edit().putString(cacheKey, category.name).apply()
        } catch (e: Exception) {
            // ignore
        }
        val userSerial = if (user != null) {
            try {
                UserCache.getInstance(context).getSerialNumberForUser(user)
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${userSerial}#$normPkg", category.name)
            .apply()
    }

    fun clearCategoryOverride(packageName: String, user: UserHandle?) {
        val normPkg = packageName.lowercase().trim()
        if (normPkg.isEmpty()) return
        val cacheKey = "$normPkg#${user?.hashCode() ?: 0}"
        categoryCache.remove(cacheKey)
        try {
            diskPrefs.edit().remove(cacheKey).apply()
        } catch (e: Exception) {
            // ignore
        }
        val userSerial = if (user != null) {
            try {
                UserCache.getInstance(context).getSerialNumberForUser(user)
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${userSerial}#$normPkg")
            .remove(normPkg)
            .apply()
    }

    private fun getCategoryByDeveloper(packageName: String): AppLibraryCategory? {
        if (packageName.startsWith("com.adobe.")) return AppLibraryCategory.CREATIVITY
        if (packageName.startsWith("com.microsoft.")) return AppLibraryCategory.PRODUCTIVITY
        if (packageName.startsWith("com.jetbrains.")) return AppLibraryCategory.PRODUCTIVITY
        if (packageName.startsWith("com.google.android.apps.docs")) return AppLibraryCategory.PRODUCTIVITY
        if (packageName.startsWith("com.google.android.apps.fitness")) return AppLibraryCategory.HEALTH_FITNESS
        return null
    }

    private fun getCategoryByPackage(packageName: String): AppLibraryCategory? {
        return when (packageName) {
            // Social: Dialers, Messaging, Contacts, Social Media & Chat
            "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
            "com.coloros.phonemanager", "com.oplus.dialer", "com.coloros.dialer",
            "com.oneplus.dialer", "com.android.incallui",
            "com.google.android.contacts", "com.android.contacts", "com.samsung.android.app.contacts",
            "com.coloros.contacts", "com.oplus.contacts", "com.oneplus.contacts", "com.vivo.contacts",
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
            "com.coloros.mms", "com.oplus.mms", "com.oneplus.mms", "com.miui.mms",
            "com.whatsapp", "com.whatsapp.w4b", "com.facebook.katana", "com.facebook.orca",
            "com.facebook.lite", "com.instagram.android", "com.twitter.android",
            "com.snapchat.android", "org.telegram.messenger", "org.telegram.plus",
            "org.thoughtcrime.securesms", "com.discord", "com.viber.voip", "com.skype.raider",
            "com.tencent.mm", "jp.naver.line.android", "com.kakao.talk",
            "com.google.android.apps.tachyon", "us.zoom.videomeetings",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> AppLibraryCategory.SOCIAL

            // Utilities: System essentials, App Store, Settings, Clock, Calculator, Files, Browsers
            "com.android.vending", "com.android.settings", "com.google.android.settings",
            "com.coloros.settings", "com.oplus.settings", "com.samsung.android.settings",
            "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
            "com.coloros.alarmclock", "com.oplus.alarmclock",
            "com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator",
            "com.coloros.calculator", "com.oplus.calculator", "com.miui.calculator", "com.oneplus.calculator",
            "com.vivo.calculator", "com.android.bbkcalculator",
            "com.miui.compass", "com.vivo.compass", "com.coloros.Compass2", "com.oplus.compass",
            "com.miui.securitycenter", "com.iqoo.secure", "com.vivo.imanager",
            "com.google.android.documentsui", "com.android.documentsui", "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles", "com.coloros.filemanager", "com.oplus.filemanager",
            "com.mi.android.globalFileexplorer", "com.vivo.filemanager", "com.oneplus.filemanager",
            "com.vivo.easyshare", "com.xiaomi.midrop", "com.coloros.backuprestore",
            "com.google.android.apps.weather", "com.sec.android.daemonapp", "com.coloros.weather2",
            "com.oplus.weather", "com.vivo.weather", "com.vivo.weather.provider", "com.miui.weather2",
            "net.oneplus.weather",
            "com.android.chrome", "org.mozilla.firefox", "com.microsoft.emmx",
            "com.opera.browser", "com.brave.browser", "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser", "com.vivo.browser", "com.mi.globalbrowser", "com.android.browser",
            "com.vivo.FMRadio",
            "com.google.android.soundpicker", "com.android.soundrecorder", "com.coloros.soundrecorder",
            "com.oplus.soundrecorder", "com.sec.android.app.voicenote", "com.android.stk" -> AppLibraryCategory.UTILITIES

            // Finance: Wallets, UPI, Payments, Banks, Crypto, Investing
            "com.google.android.apps.walletnf", "com.google.android.apps.nbu.paisa.user",
            "com.paypal.android.p2pmobile", "com.squareup.cash", "com.venmo",
            "net.one97.paytm", "com.phonepe.app", "in.org.npci.upiapp",
            "com.chase.sig.android", "com.infonow.bofa", "com.wf.wellsfargomobile",
            "com.americanexpress.android.acctsvcs.us", "com.capitalone.mobile", "com.citi.citimobile",
            "com.revolut.revolut", "co.uk.monzo", "com.discoverfinancial.mobile",
            "com.usbank.mobilebanking", "com.sbi.lotusintouch", "com.msf.kbank.mobile",
            "com.csam.icici.bank.imobile", "com.hdfcbank.corebanking", "com.axis.mobile",
            "com.robinhood.android", "com.coinbase.android", "com.binance.dev",
            "com.creditkarma.mobile", "com.zerodha.kite3", "com.nextbillion.groww",
            "com.webull.mn", "com.etrade.mobilepro" -> AppLibraryCategory.FINANCE

            // Productivity: Email, Calendars, Notes, Office, Cloud docs & storage
            "com.google.android.gm", "com.android.email", "com.microsoft.office.outlook",
            "com.samsung.android.email.provider", "com.yahoo.mobile.client.android.mail",
            "com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar",
            "com.coloros.calendar", "com.oplus.calendar", "com.xiaomi.calendar", "com.bbk.calendar",
            "com.vivo.calendar", "com.oneplus.calendar",
            "com.google.android.keep", "com.coloros.note", "com.oplus.note", "com.oneplus.note",
            "com.samsung.android.app.notes", "com.miui.notes", "com.vivo.notes", "com.evernote",
            "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
            "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
            "com.microsoft.office.officehubrow", "com.microsoft.office.onenote", "com.wps.moffice_eng",
            "notion.id", "com.dropbox.android", "com.microsoft.skydrive",
            "com.todoist", "com.ticktick.task", "com.trello", "com.asana.app", "com.slack" -> AppLibraryCategory.PRODUCTIVITY

            // Creativity: Photos, Camera, Design, Art, Photo/Video editing
            "com.google.android.apps.photos", "com.google.android.camera", "com.android.camera2",
            "com.android.camera", "com.sec.android.app.camera", "com.oplus.camera",
            "com.coloros.camera", "com.oneplus.camera", "com.vivo.camera",
            "com.google.android.gallery3d", "com.android.gallery3d",
            "com.sec.android.gallery3d", "com.coloros.gallery3d", "com.oplus.gallery",
            "com.vivo.gallery", "com.miui.gallery", "com.oneplus.gallery",
            "com.adobe.psmobile", "com.adobe.lrmobile", "com.niksoftware.snapseed",
            "com.canva.editor", "com.picsart.studio", "com.camerasideas.instashot",
            "com.capcut.android" -> AppLibraryCategory.CREATIVITY

            // Entertainment: Music, Video, Streaming
            "com.spotify.music", "com.netflix.mediaclient", "com.google.android.youtube",
            "com.google.android.apps.youtube.music", "tv.twitch.android.app", "com.amazon.mp3",
            "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.hbo.hbonow",
            "com.apple.android.music", "com.soundcloud.android", "com.shazam.android" -> AppLibraryCategory.ENTERTAINMENT

            // Games
            "com.nianticlabs.pokemongo", "com.supercell.clashofclans", "com.king.candycrushsaga",
            "com.supercell.clashroyale", "com.roblox.client", "com.epicgames.portal",
            "com.ea.gp.fifamobile", "com.krafton.pubg", "com.tencent.ig" -> AppLibraryCategory.GAMES

            // Travel: Maps, Rides, Nav
            "com.google.android.apps.maps", "com.waze", "com.moovit",
            "com.ubercab", "com.lyft.android", "com.booking", "com.airbnb.android",
            "com.tripadvisor.tripadvisor", "com.olacabs.customer" -> AppLibraryCategory.TRAVEL

            // Shopping & Food
            "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.ubercab.eats",
            "com.walmart.android", "com.target.ui", "com.flipkart.android",
            "com.myntra.android", "com.zomato", "in.swiggy.android",
            "com.doordash.driverapp", "com.dd.doordash", "com.grubhub.android",
            "com.instacart.client" -> AppLibraryCategory.SHOPPING_FOOD

            // Information & Reading
            "com.reddit.frontpage", "com.google.android.apps.magazines",
            "com.google.android.apps.books", "com.amazon.kindle", "com.medium.reader" -> AppLibraryCategory.INFORMATION_READING

            // Health & Fitness
            "com.google.android.apps.fitness", "com.strava", "com.myfitnesspal.android",
            "com.fitbit.FitbitMobile", "com.nike.plusgps" -> AppLibraryCategory.HEALTH_FITNESS

            // Education
            "com.duolingo", "org.khanacademy.android", "com.coursera.android" -> AppLibraryCategory.EDUCATION

            else -> null
        }
    }

    private fun getCategoryByKeyword(appName: String): AppLibraryCategory? {
        // App Stores & Essential Utilities first (prevent "store" triggering shopping)
        if (appName.contains("play store") || appName.contains("app store") ||
            appName.contains("setting") || appName.contains("clock") ||
            appName.contains("alarm") || appName.contains("timer") ||
            appName.contains("stopwatch") || appName.contains("calc") ||
            appName.contains("weather") || appName.contains("forecast") ||
            appName.contains("file") || appName.contains("tool") ||
            appName.contains("util") || appName.contains("browser") ||
            appName.contains("compass") || appName.contains("flashlight") ||
            appName.contains("recorder")) {
            return AppLibraryCategory.UTILITIES
        }

        // Finance: Banking, Wallets, Payments, Crypto, Investments
        val isFinance = appName.contains("bank") || appName.contains("finance") ||
                appName.contains("financial") || appName.contains("wallet") ||
                appName.contains("money") || appName.contains("crypto") ||
                appName.contains("bitcoin") || appName.contains("invest") ||
                appName.contains("stock") || appName.contains("trading") ||
                appName.contains("credit") || appName.contains("loan") ||
                appName.contains("budget") || appName.contains("tax") ||
                appName.contains("upi") || appName.contains("fund") ||
                (appName.contains("pay") && !appName.contains("player"))
        if (isFinance) return AppLibraryCategory.FINANCE

        // Productivity: Mail, Calendar, Notes, Docs, Cloud, Office, Tasks
        if (appName.contains("mail") || appName.contains("email") || appName.contains("gmail") ||
            appName.contains("calendar") || appName.contains("agenda") ||
            appName.contains("note") || appName.contains("task") || appName.contains("todo") ||
            appName.contains("doc") || appName.contains("sheet") || appName.contains("slide") ||
            appName.contains("office") || appName.contains("word") || appName.contains("excel") ||
            appName.contains("powerpoint") || appName.contains("drive") || appName.contains("cloud") ||
            appName.contains("pdf") || appName.contains("scanner") || appName.contains("remind") ||
            appName.contains("planner") || appName.contains("workspace") || appName.contains("notion") ||
            appName.contains("slack") || appName.contains("trello")) {
            return AppLibraryCategory.PRODUCTIVITY
        }

        // Social: Chat, Calls, Contacts, Phone, Social Media
        if (appName.contains("phone") || appName.contains("dialer") ||
            appName.contains("contact") || appName.contains("message") ||
            appName.contains("sms") || appName.contains("chat") ||
            appName.contains("messenger") || appName.contains("social") ||
            appName.contains("meet") || appName.contains("call") || appName.contains("talk")) {
            return AppLibraryCategory.SOCIAL
        }

        // Games
        if (appName.contains("game") || appName.contains("puzzle") ||
            appName.contains("rpg") || appName.contains("arcade") ||
            appName.contains("casino") || appName.contains("racing") ||
            appName.contains("quest")) {
            return AppLibraryCategory.GAMES
        }

        // Creativity: Photo, Camera, Video edit, Art
        if (appName.contains("photo") || appName.contains("camera") ||
            appName.contains("gallery") || appName.contains("draw") ||
            appName.contains("paint") || appName.contains("edit") ||
            appName.contains("design") || appName.contains("art") ||
            appName.contains("canvas") || appName.contains("studio")) {
            return AppLibraryCategory.CREATIVITY
        }

        // Entertainment: Music, Video, TV, Movies, Audio
        if (appName.contains("music") || appName.contains("audio") ||
            appName.contains("video") || appName.contains("tv") ||
            appName.contains("movie") || appName.contains("player") ||
            appName.contains("podcast") || appName.contains("stream") ||
            appName.contains("radio") || appName.contains("song")) {
            return AppLibraryCategory.ENTERTAINMENT
        }

        // Travel: Maps, Rides, Nav, Flights
        if (appName.contains("travel") || appName.contains("map") ||
            appName.contains("nav") || appName.contains("transit") ||
            appName.contains("ride") || appName.contains("taxi") ||
            appName.contains("cab") || appName.contains("flight") ||
            appName.contains("hotel") || appName.contains("trip")) {
            return AppLibraryCategory.TRAVEL
        }

        // Shopping & Food (stores, markets, delivery)
        if (appName.contains("shop") || appName.contains("store") ||
            appName.contains("food") || appName.contains("eat") ||
            appName.contains("restaurant") || appName.contains("delivery") ||
            appName.contains("grocery") || appName.contains("order")) {
            return AppLibraryCategory.SHOPPING_FOOD
        }

        // Health & Fitness
        if (appName.contains("health") || appName.contains("fitness") ||
            appName.contains("workout") || appName.contains("gym") ||
            appName.contains("diet") || appName.contains("run") ||
            appName.contains("step") || appName.contains("meditation") ||
            appName.contains("yoga")) {
            return AppLibraryCategory.HEALTH_FITNESS
        }

        // Information & Reading
        if (appName.contains("news") || appName.contains("reader") ||
            appName.contains("book") || appName.contains("ebook") ||
            appName.contains("magazine") || appName.contains("comic") ||
            appName.contains("article")) {
            return AppLibraryCategory.INFORMATION_READING
        }

        // Education
        if (appName.contains("learn") || appName.contains("study") ||
            appName.contains("course") || appName.contains("quiz") ||
            appName.contains("dictionary") || appName.contains("translate") ||
            appName.contains("school") || appName.contains("academy")) {
            return AppLibraryCategory.EDUCATION
        }

        return null
    }

    private fun getCategoryByAppInfoCategory(
        category: Int,
        appName: String,
        packageName: String
    ): AppLibraryCategory? {
        return when (category) {
            ApplicationInfo.CATEGORY_AUDIO -> AppLibraryCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_VIDEO -> AppLibraryCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_GAME -> AppLibraryCategory.GAMES
            ApplicationInfo.CATEGORY_IMAGE -> AppLibraryCategory.CREATIVITY
            ApplicationInfo.CATEGORY_MAPS -> AppLibraryCategory.TRAVEL
            ApplicationInfo.CATEGORY_NEWS -> AppLibraryCategory.INFORMATION_READING
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> {
                // Disambiguate finance vs productivity
                if (appName.contains("bank") || appName.contains("pay") ||
                    appName.contains("wallet") || appName.contains("money") ||
                    appName.contains("finance") || packageName.contains("bank") ||
                    packageName.contains("pay") || packageName.contains("wallet")
                ) {
                    AppLibraryCategory.FINANCE
                } else {
                    AppLibraryCategory.PRODUCTIVITY
                }
            }
            ApplicationInfo.CATEGORY_SOCIAL -> AppLibraryCategory.SOCIAL
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppLibraryCategory.UTILITIES
            else -> null
        }
    }
}

```

---

# src/com/android/launcher3/applibrary/model/AppLibraryModel.kt

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/model/AppLibraryModel.kt`  
**Lines**: 140 | **Bytes**: 4889

```kotlin
package com.android.launcher3.applibrary.model

import android.content.Context
import com.android.launcher3.model.data.AppInfo

class AppLibraryModel(private val context: Context) {

    val categoryProvider = CategoryProvider.getInstance(context)
    val suggestionsProvider = SuggestionsProvider(context)
    val recentProvider = RecentProvider(context)

    // Maps a Category to a list of AppInfos
    private val categorizedApps = mutableMapOf<AppLibraryCategory, MutableList<AppInfo>>()
    
    // Ordered categories for display
    private var orderedCategories = emptyList<AppLibraryCategory>()

    private var allApps: List<AppInfo> = emptyList()
    private var cachedSuggestions: List<AppInfo>? = null

    private val listeners = mutableListOf<AppLibraryUpdateListener>()

    interface AppLibraryUpdateListener {
        fun onAppLibraryUpdated()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppLibraryModel? = null

        @JvmStatic
        fun getInstance(context: Context): AppLibraryModel {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppLibraryModel(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun addListener(listener: AppLibraryUpdateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        if (orderedCategories.isNotEmpty()) {
            listener.onAppLibraryUpdated()
        }
    }

    fun removeListener(listener: AppLibraryUpdateListener) {
        listeners.remove(listener)
    }

    private var pendingSequence = 0L

    fun updateApps(apps: List<AppInfo>) {
        this.allApps = apps
        val seq = ++pendingSequence
        com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR.execute {
            if (seq != pendingSequence) {
                android.util.Log.d("AppLibraryModel", "updateApps skipped: superseded by seq $pendingSequence")
                return@execute
            }
            val t0 = System.currentTimeMillis()
            val newCategorized = mutableMapOf<AppLibraryCategory, MutableList<AppInfo>>()

            // Distribute to categories
            for (app in apps) {
                val category = categoryProvider.getCategory(app)
                newCategorized.getOrPut(category) { mutableListOf() }.add(app)
            }
            val t1 = System.currentTimeMillis()

            // Sort categories by predefined order, skipping empty ones
            val ordered = mutableListOf<AppLibraryCategory>()
            // Suggestions is special and always first if it has content
            ordered.add(AppLibraryCategory.SUGGESTIONS)

            AppLibraryCategory.values().forEach { cat ->
                if (cat != AppLibraryCategory.SUGGESTIONS) {
                    val list = newCategorized[cat]
                    if (!list.isNullOrEmpty()) {
                        ordered.add(cat)
                        // Sort apps alphabetically within category
                        list.sortBy { it.title?.toString()?.lowercase() ?: "" }
                    }
                }
            }
            val t2 = System.currentTimeMillis()

            val suggestions = suggestionsProvider.getSuggestions(apps)
            val t3 = System.currentTimeMillis()

            android.util.Log.d("AppLibraryModel", "updateApps seq $seq timing: categorize=${t1 - t0}ms, sort=${t2 - t1}ms, suggestions=${t3 - t2}ms, total=${t3 - t0}ms")

            com.android.launcher3.util.Executors.MAIN_EXECUTOR.execute {
                if (seq != pendingSequence) return@execute
                categorizedApps.clear()
                categorizedApps.putAll(newCategorized)
                cachedSuggestions = suggestions
                orderedCategories = ordered
                notifyListeners()
            }
        }
    }

    fun refreshCategories() {
        if (allApps.isNotEmpty()) {
            updateApps(allApps)
        }
    }

    fun getOrderedCategories(): List<AppLibraryCategory> {
        return orderedCategories
    }

    fun getAppsForCategory(category: AppLibraryCategory): List<AppInfo> {
        return when (category) {
            AppLibraryCategory.SUGGESTIONS -> cachedSuggestions ?: emptyList()
            else -> categorizedApps[category] ?: emptyList()
        }
    }

    fun recordAppLaunch(app: AppInfo) {
        val packageName = app.componentName?.packageName ?: return
        recordPackageLaunch(packageName)
    }

    fun recordPackageLaunch(packageName: String?) {
        if (packageName.isNullOrEmpty()) return
        com.android.launcher3.util.Executors.MODEL_EXECUTOR.execute {
            suggestionsProvider.recordLaunch(packageName)
        }
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener.onAppLibraryUpdated()
        }
    }
}
```

---

# src/com/android/launcher3/applibrary/model/AppLibraryCategory.kt

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/model/AppLibraryCategory.kt`  
**Lines**: 20 | **Bytes**: 542

```kotlin
package com.android.launcher3.applibrary.model

enum class AppLibraryCategory(val title: String) {
    SUGGESTIONS("Suggestions"),
    WORK("Work"),
    SOCIAL("Social"),
    PRODUCTIVITY("Productivity"),
    FINANCE("Finance"),
    UTILITIES("Utilities"),
    CREATIVITY("Creativity"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    TRAVEL("Travel"),
    SHOPPING_FOOD("Shopping & Food"),
    HEALTH_FITNESS("Health & Fitness"),
    INFORMATION_READING("Information & Reading"),
    EDUCATION("Education"),
    OTHER("Other")
}
```

---

# src/com/android/launcher3/applibrary/model/SuggestionsProvider.kt

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/model/SuggestionsProvider.kt`  
**Lines**: 105 | **Bytes**: 3965

```kotlin
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
```

---

# src/com/android/launcher3/applibrary/view/SpotlightSearchOverlay.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/SpotlightSearchOverlay.java`  
**Lines**: 758 | **Bytes**: 29342

```java
/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Arrays;

/**
 * iOS Spotlight-style search overlay that opens in-place over whatever home screen
 * page is currently active, without navigating away to the App Library page.
 */
public class SpotlightSearchOverlay extends AbstractFloatingView {

    private static final Interpolator IOS_SPRING_INTERPOLATOR = new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    private static final Interpolator IOS_EXIT_INTERPOLATOR = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private final Launcher mLauncher;
    private AppLibraryOverlayGlassView mBackdropGlass;
    private View mSearchContent;
    private View mSearchRow;
    private ExtendedEditText mSearchInput;
    private ImageView mSearchClear;
    private TextView mSearchCancel;
    private RecyclerView mSearchResultsView;
    private AppLibraryIndexScrollerView mIndexScroller;
    private AppLibrarySearchAdapter mSearchAdapter;

    private ValueAnimator mOpenCloseAnimator;
    private boolean mIsClosing = false;
    private boolean mKeyboardHiddenForClose = false;
    private String mLastFilterQuery = "";
    private final com.android.launcher3.allapps.AllAppsStore.OnUpdateListener mAppsUpdateListener;

    public SpotlightSearchOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpotlightSearchOverlay(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mAppsUpdateListener = () -> {
            if (mLauncher != null && mLauncher.getAppsView() != null
                    && mLauncher.getAppsView().getAppsStore() != null && mSearchAdapter != null) {
                AppInfo[] apps = mLauncher.getAppsView().getAppsStore().getApps();
                if (apps != null) {
                    mSearchAdapter.setApps(Arrays.asList(apps));
                }
            }
        };
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackdropGlass = findViewById(R.id.spotlight_search_backdrop_glass);
        mSearchContent = findViewById(R.id.spotlight_search_content);
        mSearchRow = findViewById(R.id.spotlight_search_row);
        mSearchInput = findViewById(R.id.spotlight_search_input);
        mSearchClear = findViewById(R.id.spotlight_search_clear);
        mSearchCancel = findViewById(R.id.spotlight_search_cancel);
        mSearchResultsView = findViewById(R.id.spotlight_search_results);
        mIndexScroller = findViewById(R.id.spotlight_index_scroller);

        if (mSearchContent != null) {
            int statusBarHeight = mLauncher.getDeviceProfile().getInsets().top;
            if (statusBarHeight <= 0) {
                statusBarHeight = (int) (44f * getResources().getDisplayMetrics().density);
            }
            mSearchContent.setPadding(0, statusBarHeight + (int) (10f * getResources().getDisplayMetrics().density), 0, 0);
        }

        mSearchAdapter = new AppLibrarySearchAdapter(mLauncher);
        mSearchResultsView.setLayoutManager(new LinearLayoutManager(getContext()));
        mSearchResultsView.setItemAnimator(null);
        mSearchResultsView.setAdapter(mSearchAdapter);

        if (mIndexScroller != null) {
            mIndexScroller.setOnSectionSelectedListener(section -> {
                if (section != null && section.length() > 0) {
                    int pos = mSearchAdapter.getPositionForSection(section.charAt(0));
                    if (pos >= 0) {
                        RecyclerView.LayoutManager lm = mSearchResultsView.getLayoutManager();
                        if (lm instanceof LinearLayoutManager) {
                            ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 0);
                        }
                    }
                }
            });
        }

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!mIsOpen || mIsClosing) {
                    return;
                }
                String query = s != null ? s.toString() : "";
                if (mSearchClear != null) {
                    mSearchClear.setVisibility(query.isEmpty() ? GONE : VISIBLE);
                }
                String trimmed = query.trim();
                if (mIndexScroller != null) {
                    mIndexScroller.setVisibility(trimmed.isEmpty() ? VISIBLE : GONE);
                }
                if (!trimmed.equals(mLastFilterQuery)) {
                    mLastFilterQuery = trimmed;
                    mSearchAdapter.filter(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (mSearchInput != null) {
            mSearchInput.setOnBackKeyListener(() -> {
                Log.d("SpotlightBack", "mSearchInput.onKeyPreIme back key listener fired");
                close(true);
                return true;
            });
            mSearchInput.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        close(true);
                    }
                    return true;
                }
                return false;
            });
        }

        if (mSearchClear != null) {
            mSearchClear.setOnClickListener(v -> mSearchInput.setText(""));
        }

        if (mSearchCancel != null) {
            mSearchCancel.setOnClickListener(v -> close(true));
        }

        if (mBackdropGlass != null) {
            mBackdropGlass.setOnClickListener(v -> close(true));
        }
    }

    public static SpotlightSearchOverlay getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_SPOTLIGHT_SEARCH);
    }

    public static SpotlightSearchOverlay show(Launcher launcher) {
        DragLayer dragLayer = launcher.getDragLayer();
        for (int i = dragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof SpotlightSearchOverlay) {
                SpotlightSearchOverlay existing = (SpotlightSearchOverlay) child;
                if (existing.mIsOpen && !existing.mIsClosing) {
                    return existing;
                }
                if (existing.mOpenCloseAnimator != null) {
                    existing.mOpenCloseAnimator.cancel();
                    existing.mOpenCloseAnimator = null;
                }
                dragLayer.removeView(existing);
            }
        }

        SpotlightSearchOverlay overlay = (SpotlightSearchOverlay) LayoutInflater.from(launcher)
                .inflate(R.layout.spotlight_search_overlay, launcher.getDragLayer(), false);

        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.ignoreInsets = true;
        overlay.setLayoutParams(lp);

        overlay.prepareForEntrance();
        overlay.mIsOpen = true;
        overlay.mIsClosing = false;
        overlay.mKeyboardHiddenForClose = false;
        launcher.getDragLayer().addView(overlay);
        launcher.onDragLayerHierarchyChanged();

        // Load apps
        AppInfo[] apps = launcher.getAppsView() != null && launcher.getAppsView().getAppsStore() != null
                ? launcher.getAppsView().getAppsStore().getApps() : null;
        if (apps != null && apps.length > 0) {
            overlay.mSearchAdapter.setApps(Arrays.asList(apps));
        }

        // Animate entrance
        overlay.post(overlay::animateOpen);
        return overlay;
    }

    public void prepareForEntrance() {
        final float density = getResources().getDisplayMetrics().density;
        final float contentSlidePx = 40f * density;
        final float rowSlidePx = -24f * density;
        final float cancelSlidePx = 20f * density;

        setAlpha(0f);
        if (mBackdropGlass != null) {
            mBackdropGlass.setAlpha(0f);
        }
        if (mSearchContent != null) {
            mSearchContent.setAlpha(1f);
            mSearchContent.setTranslationY(0f);
            mSearchContent.setScaleX(1f);
            mSearchContent.setScaleY(1f);
        }
        if (mSearchRow != null) {
            mSearchRow.setTranslationY(rowSlidePx);
            mSearchRow.setScaleX(0.92f);
            mSearchRow.setScaleY(0.92f);
            mSearchRow.setAlpha(0f);
        }
        if (mSearchCancel != null) {
            mSearchCancel.setTranslationX(cancelSlidePx);
            mSearchCancel.setAlpha(0f);
        }
        if (mSearchResultsView != null) {
            mSearchResultsView.setTranslationY(contentSlidePx);
            mSearchResultsView.setScaleX(0.94f);
            mSearchResultsView.setScaleY(0.94f);
            mSearchResultsView.setAlpha(0f);
        }
        if (mIndexScroller != null) {
            mIndexScroller.setAlpha(0f);
        }
    }

    private void animateOpen() {
        if (mIsClosing || !mIsOpen) {
            return;
        }
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }

        final float density = getResources().getDisplayMetrics().density;
        final float contentSlidePx = 40f * density;
        final float rowSlidePx = -24f * density;
        final float cancelSlidePx = 20f * density;

        prepareForEntrance();
        setAlpha(1f);

        Workspace<?> ws = mLauncher.getWorkspace();

        mOpenCloseAnimator = ValueAnimator.ofFloat(0f, 1f);
        mOpenCloseAnimator.setDuration(340);
        mOpenCloseAnimator.setInterpolator(IOS_SPRING_INTERPOLATOR);
        mOpenCloseAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (mBackdropGlass != null) {
                mBackdropGlass.setAlpha(progress);
            }
            if (mSearchRow != null) {
                mSearchRow.setTranslationY((1f - progress) * rowSlidePx);
                float rowScale = 0.92f + 0.08f * progress;
                mSearchRow.setScaleX(rowScale);
                mSearchRow.setScaleY(rowScale);
                mSearchRow.setAlpha(Math.min(1f, progress * 1.5f));
            }
            if (mSearchCancel != null) {
                mSearchCancel.setTranslationX((1f - progress) * cancelSlidePx);
                mSearchCancel.setAlpha(Math.min(1f, progress * 2f));
            }
            if (mSearchResultsView != null) {
                mSearchResultsView.setTranslationY((1f - progress) * contentSlidePx);
                float contentScale = 0.94f + 0.06f * progress;
                mSearchResultsView.setScaleX(contentScale);
                mSearchResultsView.setScaleY(contentScale);
                mSearchResultsView.setAlpha(Math.min(1f, progress * 1.3f));
            }
            if (mIndexScroller != null) {
                mIndexScroller.setAlpha(progress);
            }
            if (ws != null) {
                float wsScale = 1f - 0.05f * progress;
                ws.setScaleX(wsScale);
                ws.setScaleY(wsScale);
            }
        });
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCancelled || mIsClosing || !mIsOpen) {
                    return;
                }
                setAlpha(1f);
                if (mBackdropGlass != null) mBackdropGlass.setAlpha(1f);
                if (mSearchRow != null) {
                    mSearchRow.setTranslationY(0f);
                    mSearchRow.setScaleX(1f);
                    mSearchRow.setScaleY(1f);
                    mSearchRow.setAlpha(1f);
                }
                if (mSearchCancel != null) {
                    mSearchCancel.setTranslationX(0f);
                    mSearchCancel.setAlpha(1f);
                }
                if (mSearchResultsView != null) {
                    mSearchResultsView.setTranslationY(0f);
                    mSearchResultsView.setScaleX(1f);
                    mSearchResultsView.setScaleY(1f);
                    mSearchResultsView.setAlpha(1f);
                }
                if (mIndexScroller != null) {
                    mIndexScroller.setAlpha(1f);
                }
                if (ws != null) {
                    ws.setScaleX(0.95f);
                    ws.setScaleY(0.95f);
                }
                showKeyboard();
            }
        });
        mOpenCloseAnimator.start();
    }

    public void showKeyboard() {
        if (mIsClosing || !mIsOpen) {
            return;
        }
        mInternalKeyboardHide = false;
        mKeyboardHiddenForClose = false;
        if (mSearchInput != null) {
            mSearchInput.requestFocus();
            postDelayed(() -> {
                if (mIsClosing || !mIsOpen) {
                    return;
                }
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 60);
        }
    }

    public void hideKeyboard() {
        mInternalKeyboardHide = true;
        if (mKeyboardHiddenForClose) {
            return;
        }
        mKeyboardHiddenForClose = true;
        if (mSearchInput != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsClosing && animate) {
            return;
        }
        mIsClosing = true;
        mIsOpen = false;
        unregisterBackCallback();
        hideKeyboard();

        if (mBackdropGlass != null) mBackdropGlass.animate().cancel();
        if (mSearchContent != null) mSearchContent.animate().cancel();
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) ws.animate().cancel();

        if (!animate) {
            if (mOpenCloseAnimator != null) {
                mOpenCloseAnimator.cancel();
            }
            resetTransforms(ws);
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
            if (mLauncher != null) {
                mLauncher.onDragLayerHierarchyChanged();
            }
            return;
        }

        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }

        if (mSearchResultsView != null) {
            mSearchResultsView.stopScroll();
            mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        // Keep root ViewGroup at 1f scale/alpha so LiquidGlassViews never render inside a scaled/translucent saveLayer
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);

        final float density = getResources().getDisplayMetrics().density;
        final float slideOffsetPx = 28f * density;
        final float startBackdropAlpha = mBackdropGlass != null ? mBackdropGlass.getAlpha() : 1f;
        final float startContentAlpha = mSearchContent != null ? mSearchContent.getAlpha() : 1f;
        final float startContentTy = mSearchContent != null ? mSearchContent.getTranslationY() : 0f;
        final float startContentScale = mSearchContent != null ? mSearchContent.getScaleX() : 1f;
        final float startWsScale = ws != null ? ws.getScaleX() : 0.95f;

        mOpenCloseAnimator = ValueAnimator.ofFloat(0f, 1f);
        mOpenCloseAnimator.setDuration(250);
        mOpenCloseAnimator.setInterpolator(IOS_EXIT_INTERPOLATOR);
        mOpenCloseAnimator.addUpdateListener(animation -> {
            float p = (float) animation.getAnimatedValue();
            float inv = 1f - p;
            if (mBackdropGlass != null) {
                mBackdropGlass.setAlpha(startBackdropAlpha * inv);
            }
            if (mSearchContent != null) {
                mSearchContent.setAlpha(startContentAlpha * inv);
                mSearchContent.setTranslationY(startContentTy + (slideOffsetPx - startContentTy) * p);
                float s = startContentScale + (0.94f - startContentScale) * p;
                mSearchContent.setScaleX(s);
                mSearchContent.setScaleY(s);
            }
            if (ws != null) {
                float w = startWsScale + (1f - startWsScale) * p;
                ws.setScaleX(w);
                ws.setScaleY(w);
            }
        });
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mSearchResultsView != null) {
                    mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
                }
                // Only restore workspace scale if a new Spotlight overlay hasn't opened
                if (ws != null && getOpen(mLauncher) == null) {
                    ws.setScaleX(1f);
                    ws.setScaleY(1f);
                }
                resetTransforms(null);
                setVisibility(GONE);
                if (mLauncher != null) {
                    mLauncher.onDragLayerHierarchyChanged();
                }
                // Defer detaching the focused EditText slightly so any in-flight IME hide animation finishes cleanly
                postDelayed(() -> {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(SpotlightSearchOverlay.this);
                    }
                }, 120);
            }
        });
        mOpenCloseAnimator.start();
    }

    private void resetTransforms(Workspace<?> ws) {
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
        if (mBackdropGlass != null) {
            mBackdropGlass.setAlpha(1f);
        }
        if (mSearchContent != null) {
            mSearchContent.setAlpha(1f);
            mSearchContent.setTranslationY(0f);
            mSearchContent.setScaleX(1f);
            mSearchContent.setScaleY(1f);
        }
        if (ws != null) {
            ws.setScaleX(1f);
            ws.setScaleY(1f);
        }
    }

    private OnBackInvokedCallback mBackCallback;
    private boolean mImeWasVisible;
    private boolean mInternalKeyboardHide;
    private final ViewTreeObserver.OnGlobalLayoutListener mImeLayoutListener = this::checkRootImeVisibility;

    private void checkRootImeVisibility() {
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            handleImeVisibility(rootInsets.isVisible(WindowInsets.Type.ime()));
        }
    }

    private void handleImeVisibility(boolean imeVisible) {
        if (mIsClosing || !mIsOpen) {
            mImeWasVisible = imeVisible;
            return;
        }
        if (mImeWasVisible && !imeVisible) {
            if (mInternalKeyboardHide) {
                mInternalKeyboardHide = false;
            } else {
                Log.d("SpotlightBack", "IME hidden externally while Spotlight open");
                mImeWasVisible = false;
                return;
            }
        }
        mImeWasVisible = imeVisible;
    }

    @Override
    public void onBackStarted(@NonNull BackEvent backEvent) {
        Log.d("SpotlightBack", "onBackStarted progress=" + backEvent.getProgress());
        super.onBackStarted(backEvent);
        if (mIsClosing) {
            return;
        }
        if (mOpenCloseAnimator != null && mOpenCloseAnimator.isRunning()) {
            mOpenCloseAnimator.cancel();
        }
        if (mBackdropGlass != null) mBackdropGlass.animate().cancel();
        if (mSearchContent != null) mSearchContent.animate().cancel();
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) ws.animate().cancel();
        if (mSearchResultsView != null) {
            mSearchResultsView.stopScroll();
            mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
        }
    }

    @Override
    public void onBackProgressed(@NonNull BackEvent backEvent) {
        Log.d("SpotlightBack", "onBackProgressed progress=" + backEvent.getProgress());
        if (mIsClosing) {
            return;
        }
        float progress = backEvent.getProgress();
        if (mBackdropGlass != null) {
            mBackdropGlass.setAlpha(1f - 0.35f * progress);
        }
        if (mSearchContent != null) {
            float scale = 1f - 0.06f * progress;
            mSearchContent.setScaleX(scale);
            mSearchContent.setScaleY(scale);
            mSearchContent.setAlpha(1f - 0.25f * progress);
            mSearchContent.setTranslationY(progress * 20f * getResources().getDisplayMetrics().density);
        }
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) {
            float wsScale = 0.95f + 0.05f * progress;
            ws.setScaleX(wsScale);
            ws.setScaleY(wsScale);
        }
    }

    @Override
    public void onBackCancelled() {
        Log.d("SpotlightBack", "onBackCancelled called");
        if (mIsClosing) {
            return;
        }
        if (mSearchResultsView != null) {
            mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
        }
        if (mBackdropGlass != null) {
            mBackdropGlass.animate().alpha(1f).setDuration(160).start();
        }
        if (mSearchContent != null) {
            mSearchContent.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(160).start();
        }
        Workspace<?> ws = mLauncher.getWorkspace();
        if (ws != null) {
            ws.animate().scaleX(0.95f).scaleY(0.95f).setDuration(160).start();
        }
    }

    @Override
    public void onBackInvoked() {
        Log.d("SpotlightBack", "onBackInvoked called");
        close(true);
    }

    private void unregisterBackCallback() {
        if (Utilities.ATLEAST_T && mBackCallback != null) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null && mLauncher != null) {
                    dispatcher = mLauncher.getOnBackInvokedDispatcher();
                }
                if (dispatcher != null) {
                    dispatcher.unregisterOnBackInvokedCallback(mBackCallback);
                }
            } catch (Throwable ignored) {}
            mBackCallback = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setOnApplyWindowInsetsListener((v, insets) -> {
            checkRootImeVisibility();
            return v.onApplyWindowInsets(insets);
        });
        getViewTreeObserver().addOnGlobalLayoutListener(mImeLayoutListener);

        if (mLauncher != null && mLauncher.getAppsView() != null
                && mLauncher.getAppsView().getAppsStore() != null) {
            mLauncher.getAppsView().getAppsStore().addUpdateListener(mAppsUpdateListener);
        }

        if (Utilities.ATLEAST_T) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null && mLauncher != null) {
                    dispatcher = mLauncher.getOnBackInvokedDispatcher();
                }
                if (dispatcher != null) {
                    if (Utilities.ATLEAST_U) {
                        mBackCallback = new OnBackAnimationCallback() {
                            @Override
                            public void onBackStarted(@NonNull BackEvent backEvent) {
                                SpotlightSearchOverlay.this.onBackStarted(backEvent);
                            }

                            @Override
                            public void onBackProgressed(@NonNull BackEvent backEvent) {
                                SpotlightSearchOverlay.this.onBackProgressed(backEvent);
                            }

                            @Override
                            public void onBackCancelled() {
                                SpotlightSearchOverlay.this.onBackCancelled();
                            }

                            @Override
                            public void onBackInvoked() {
                                SpotlightSearchOverlay.this.onBackInvoked();
                            }
                        };
                        dispatcher.registerOnBackInvokedCallback(
                                OnBackInvokedDispatcher.PRIORITY_OVERLAY, mBackCallback);
                    } else {
                        mBackCallback = () -> close(true);
                        dispatcher.registerOnBackInvokedCallback(
                                OnBackInvokedDispatcher.PRIORITY_OVERLAY, mBackCallback);
                    }
                }
            } catch (Throwable t) {
                Log.w("SpotlightSearchOverlay", "Failed to register OnBackInvokedCallback", t);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        if (mLauncher != null && mLauncher.getWorkspace() != null && getOpen(mLauncher) == null) {
            mLauncher.getWorkspace().setScaleX(1f);
            mLauncher.getWorkspace().setScaleY(1f);
        }
        if (mLauncher != null && mLauncher.getAppsView() != null
                && mLauncher.getAppsView().getAppsStore() != null) {
            mLauncher.getAppsView().getAppsStore().removeUpdateListener(mAppsUpdateListener);
        }
        getViewTreeObserver().removeOnGlobalLayoutListener(mImeLayoutListener);
        unregisterBackCallback();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsClosing || !mIsOpen) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                close(true);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_SPOTLIGHT_SEARCH) != 0;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }
}
```

---

# src/com/android/launcher3/applibrary/view/AppLibraryDashboardView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/AppLibraryDashboardView.java`  
**Lines**: 358 | **Bytes**: 13139

```java
package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AppLibraryDashboardView extends RecyclerView {

    public interface OnOverscrollListener {
        void onOverscrollChanged(float translationY);
    }

    private OnOverscrollListener mOverscrollListener;
    private float mLastTouchY = 0f;
    private float mInitialDownY = 0f;
    private float mOverscrollY = 0f;
    private boolean mIsOverscrolling = false;
    private int mTouchSlop;
    private ValueAnimator mReboundAnimator;

    public AppLibraryDashboardView(@NonNull Context context) {
        this(context, null);
    }

    public AppLibraryDashboardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryDashboardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnOverscrollListener(OnOverscrollListener listener) {
        mOverscrollListener = listener;
    }

    private void init() {
        // iOS App Library uses a 2-column grid
        GridLayoutManager layoutManager =
                new GridLayoutManager(getContext(), 2);
        setLayoutManager(layoutManager);
        setHasFixedSize(true);
        setItemViewCacheSize(14);

        // Disable default item animations for a smoother iOS-like feel when updating
        setItemAnimator(null);
        setClipToPadding(false);
        setClipChildren(false);

        // Enable EdgeEffect and drive physical translationY rubber-band stretch via custom EdgeEffectFactory
        setOverScrollMode(OVER_SCROLL_ALWAYS);
        setEdgeEffectFactory(new EdgeEffectFactory() {
            @NonNull
            @Override
            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
                return new DashboardRubberBandEdgeEffect(getContext(), direction);
            }
        });
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    private class DashboardRubberBandEdgeEffect extends EdgeEffect {
        private final int mDirection;

        DashboardRubberBandEdgeEffect(Context context, int direction) {
            super(context);
            mDirection = direction;
        }

        @Override
        public void onPull(float deltaDistance) {
            handlePull(deltaDistance);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
            return deltaDistance;
        }

        @Override
        public float getDistance() {
            if (getHeight() <= 0) return 0f;
            if (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) {
                return mOverscrollY < -0.5f ? -mOverscrollY / getHeight() : 0f;
            } else if (mDirection == EdgeEffectFactory.DIRECTION_TOP) {
                return mOverscrollY > 0.5f ? mOverscrollY / getHeight() : 0f;
            }
            return 0f;
        }

        private void handlePull(float deltaDistance) {
            if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                mReboundAnimator.cancel();
            }
            mIsOverscrolling = true;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }

            // deltaDistance is relative to view height.
            // DIRECTION_BOTTOM: dragging up beyond bottom -> negative translationY
            // DIRECTION_TOP: dragging down beyond top -> positive translationY
            float sign = (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) ? -1f : 1f;
            float pixelDelta = deltaDistance * (getHeight() > 0 ? getHeight() : 1000f);

            float maxPull = getHeight() > 0 ? getHeight() * 0.45f : 600f;
            float progress = Math.min(1f, Math.abs(mOverscrollY) / maxPull);
            float damping = 0.42f * (1f - progress * 0.65f);

            // If pulling back toward zero, reduce resistance
            if ((mOverscrollY > 0 && sign * deltaDistance < 0) || (mOverscrollY < 0 && sign * deltaDistance > 0)) {
                damping = 0.75f;
            }

            mOverscrollY += sign * pixelDelta * damping;

            // Clamp to zero if crossing back
            if (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM && mOverscrollY > 0f) {
                mOverscrollY = 0f;
            } else if (mDirection == EdgeEffectFactory.DIRECTION_TOP && mOverscrollY < 0f) {
                mOverscrollY = 0f;
            }

            setTranslationY(mOverscrollY);
            notifyOverscroll(mOverscrollY);
        }

        @Override
        public void onRelease() {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            boolean isOverscrolledInMyDirection = (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM && mOverscrollY < -0.5f)
                    || (mDirection == EdgeEffectFactory.DIRECTION_TOP && mOverscrollY > 0.5f);
            if (isOverscrolledInMyDirection) {
                animateRebound();
            } else if (Math.abs(mOverscrollY) <= 0.5f) {
                mIsOverscrolling = false;
            }
        }

        @Override
        public void onAbsorb(int velocity) {
            int signedVelocity = (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) ? velocity : -velocity;
            playFlingBounce(signedVelocity);
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        @Override
        public boolean isFinished() {
            if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                return false;
            }
            if (mDirection == EdgeEffectFactory.DIRECTION_BOTTOM) {
                return mOverscrollY >= -0.5f;
            } else if (mDirection == EdgeEffectFactory.DIRECTION_TOP) {
                return mOverscrollY <= 0.5f;
            }
            return true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            setClipBounds(new Rect(0, 0, w, h));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                mReboundAnimator.cancel();
            }
            mLastTouchY = e.getY();
            mInitialDownY = e.getY();
            if (Math.abs(mOverscrollY) > 0.5f) {
                mIsOverscrolling = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (mReboundAnimator != null && mReboundAnimator.isRunning()) {
                    mReboundAnimator.cancel();
                }
                mLastTouchY = e.getY();
                mInitialDownY = e.getY();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (Math.abs(mOverscrollY) > 0.5f && (mReboundAnimator == null || !mReboundAnimator.isRunning())) {
                    animateRebound();
                }
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        boolean canScroll = canScrollVertically(velocityY > 0 ? 1 : -1);
        if (!canScroll && Math.abs(velocityY) > 250) {
            playFlingBounce(velocityY);
            return true;
        }
        return super.fling(velocityX, velocityY);
    }

    private void playFlingBounce(int velocityY) {
        if (Math.abs(velocityY) < 250) return;
        if (mReboundAnimator != null) {
            mReboundAnimator.cancel();
        }
        mIsOverscrolling = true;
        float density = getResources().getDisplayMetrics().density;
        float maxBounce = 55f * density;
        float distance = Math.min(Math.abs(velocityY) * 0.045f, maxBounce);
        float peakY = (velocityY > 0 ? -1f : 1f) * distance;
        float startY = mOverscrollY;

        // Phase 1: Decelerate outward to peak (iOS spring compression)
        ValueAnimator outwardAnim = ValueAnimator.ofFloat(startY, peakY);
        mReboundAnimator = outwardAnim;
        outwardAnim.setDuration(160);
        outwardAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f));
        outwardAnim.addUpdateListener(a -> {
            mOverscrollY = (float) a.getAnimatedValue();
            setTranslationY(mOverscrollY);
            notifyOverscroll(mOverscrollY);
        });
        outwardAnim.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCancelled) return;
                // Phase 2: Spring back from peak to 0 (smooth iOS recovery)
                ValueAnimator reboundAnim = ValueAnimator.ofFloat(peakY, 0f);
                mReboundAnimator = reboundAnim;
                reboundAnim.setDuration(280);
                reboundAnim.setInterpolator(new PathInterpolator(0.25f, 1f, 0.5f, 1f));
                reboundAnim.addUpdateListener(ra -> {
                    mOverscrollY = (float) ra.getAnimatedValue();
                    setTranslationY(mOverscrollY);
                    notifyOverscroll(mOverscrollY);
                });
                reboundAnim.addListener(new AnimatorListenerAdapter() {
                    private boolean mReboundCancelled = false;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mReboundCancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!mReboundCancelled) {
                            mOverscrollY = 0f;
                            setTranslationY(0f);
                            notifyOverscroll(0f);
                            mIsOverscrolling = false;
                        }
                    }
                });
                reboundAnim.start();
            }
        });
        outwardAnim.start();
    }

    private void animateRebound() {
        if (mReboundAnimator != null) {
            mReboundAnimator.cancel();
        }
        mReboundAnimator = ValueAnimator.ofFloat(mOverscrollY, 0f);
        mReboundAnimator.setDuration(280);
        mReboundAnimator.setInterpolator(new PathInterpolator(0.25f, 1f, 0.5f, 1f));
        mReboundAnimator.addUpdateListener(animation -> {
            mOverscrollY = (float) animation.getAnimatedValue();
            setTranslationY(mOverscrollY);
            notifyOverscroll(mOverscrollY);
        });
        mReboundAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;
            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mCancelled) {
                    mOverscrollY = 0f;
                    setTranslationY(0f);
                    notifyOverscroll(0f);
                    mIsOverscrolling = false;
                }
            }
        });
        mReboundAnimator.start();
    }

    private void notifyOverscroll(float translationY) {
        if (mOverscrollListener != null) {
            mOverscrollListener.onOverscrollChanged(translationY);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mReboundAnimator != null) {
            mReboundAnimator.cancel();
        }
    }
}
```

---

# src/com/android/launcher3/applibrary/view/AppLibraryPage.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/AppLibraryPage.java`  
**Lines**: 1033 | **Bytes**: 44770

```java
package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.applibrary.model.AppLibraryModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.PackageUserKey;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppLibraryPage extends FrameLayout implements Insettable {

    private View mContentContainer;
    private View mSearchRow;
    private View mSearchContainer;
    private View mSearchContent;
    private ExtendedEditText mSearchInput;
    private TextView mSearchCancel;
    private AppLibraryDashboardView mDashboardView;
    private AppLibraryDashboardAdapter mDashboardAdapter;
    private FrameLayout mSearchResultsContainer;
    private RecyclerView mSearchResultsView;
    private AppLibrarySearchAdapter mSearchAdapter;
    private AppLibraryIndexScrollerView mIndexScroller;
    private FrameLayout mExpansionOverlay;
    private View mSearchBackdrop;
    private AppLibraryOverlayGlassView mSearchBackdropGlass;
    private AppLibrarySearchGlassView mSearchGlass;

    private AppLibraryModel mAppLibraryModel;
    private int mSavedScrollY = 0;
    private boolean mInSearchMode = false;
    private boolean mIsClearingSearch = false;
    private boolean mAppsStoreListenerRegistered = false;
    private ValueAnimator mTransitionAnimator;
    private OnBackInvokedCallback mBackCallback;
    private long mLastSearchExitedTime = 0;
    private boolean mImeWasVisible = false;
    private boolean mInternalKeyboardHide = false;
    private boolean mBackGestureActive = false;
    private final ViewTreeObserver.OnGlobalLayoutListener mImeLayoutListener = this::checkRootImeVisibility;
    private final com.android.launcher3.allapps.AllAppsStore.OnUpdateListener mAppsStoreUpdateListener = () -> {
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null) {
            AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
            if (apps != null && apps.length > 0) {
                updateApps(Arrays.asList(apps));
            }
        }
    };
    private final com.android.launcher3.applibrary.model.AppLibraryModel.AppLibraryUpdateListener mAppLibraryUpdateListener = () -> {
        if (mAppLibraryModel == null || mDashboardAdapter == null) return;
        Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> newData = new LinkedHashMap<>();
        for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
            newData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
        }
        mDashboardAdapter.updateData(newData);
    };

    /** Listener that only runs its end logic if the animator was NOT cancelled. */
    private abstract static class EndOnlyListener extends AnimatorListenerAdapter {
        private boolean mCancelled;
        @Override public void onAnimationCancel(Animator a) { mCancelled = true; }
        @Override public void onAnimationEnd(Animator a) { if (!mCancelled) onFinished(); }
        abstract void onFinished();
    }

    /** Stop every ViewPropertyAnimator a back-gesture cancel may have left running. */
    private void cancelViewAnimators() {
        if (mDashboardView != null) mDashboardView.animate().cancel();
        if (mSearchResultsContainer != null) mSearchResultsContainer.animate().cancel();
        if (mSearchBackdrop != null) mSearchBackdrop.animate().cancel();
    }

    /** The one place that guarantees the category grid is shown. */
    private void settleDashboardVisible() {
        if (mDashboardView == null) return;
        mDashboardView.animate().cancel();
        mDashboardView.setVisibility(VISIBLE);
        mDashboardView.setAlpha(1f);
        mDashboardView.setScaleX(1f);
        mDashboardView.setScaleY(1f);
    }

    /** Final state of "search open". */
    private void applySearchSteadyState() {
        if (mSearchContent != null) mSearchContent.setTranslationX(0f);

        ViewGroup.LayoutParams lp = mSearchCancel.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mSearchCancel.setLayoutParams(lp);
        mSearchCancel.setVisibility(VISIBLE);
        mSearchCancel.setAlpha(1f);
        mSearchCancel.setTranslationX(0f);

        mDashboardView.animate().cancel();
        mDashboardView.setVisibility(GONE);
        mDashboardView.setAlpha(1f);      // never leave a hidden view at alpha 0
        mDashboardView.setScaleX(1f);
        mDashboardView.setScaleY(1f);

        mSearchResultsContainer.setVisibility(VISIBLE);
        mSearchResultsContainer.setAlpha(1f);
        mSearchResultsContainer.setTranslationY(0f);
        if (mSearchBackdrop != null) {
            mSearchBackdrop.setVisibility(VISIBLE);
            mSearchBackdrop.setAlpha(1f);
        }
    }

    /** Final state of "browsing the App Library". Called when an exit animation completes. */
    private void applyBrowseSteadyState(float targetShift) {
        if (mInSearchMode) return;        // user re-entered search in the meantime

        if (mSearchResultsView != null) {
            mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
        }
        mIsClearingSearch = true;
        try {
            mSearchInput.setText("");
            mSearchInput.clearFocus();
        } finally {
            mIsClearingSearch = false;
        }
        if (mSearchContent != null) mSearchContent.setTranslationX(targetShift);

        mSearchCancel.setVisibility(GONE);
        ViewGroup.LayoutParams lp = mSearchCancel.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        mSearchCancel.setLayoutParams(lp);
        mSearchCancel.setAlpha(1f);
        mSearchCancel.setTranslationX(0f);

        if (mSearchBackdrop != null) {
            mSearchBackdrop.setVisibility(GONE);
            mSearchBackdrop.setAlpha(1f);
        }
        mSearchResultsContainer.setVisibility(GONE);
        mSearchResultsContainer.setAlpha(1f);
        mSearchResultsContainer.setTranslationY(0f);

        settleDashboardVisible();         // <- the guarantee
    }

    public AppLibraryPage(Context context) {
        this(context, null);
    }

    public AppLibraryPage(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryPage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mAppLibraryModel = AppLibraryModel.getInstance(getContext());
        mDashboardAdapter = new AppLibraryDashboardAdapter(getContext());

        // Inflate layout contents
        LayoutInflater.from(getContext()).inflate(R.layout.app_library_page, this, true);

        mContentContainer = findViewById(R.id.app_library_content_container);
        mSearchRow = findViewById(R.id.app_library_search_row);
        if (mSearchRow != null) {
            float rowElevation = 12f * getResources().getDisplayMetrics().density;
            mSearchRow.setElevation(rowElevation);
            mSearchRow.setTranslationZ(rowElevation);
        }
        mSearchContainer = findViewById(R.id.app_library_search_container);
        mSearchContent = findViewById(R.id.app_library_search_content);
        mSearchInput = findViewById(R.id.app_library_search_input);
        mSearchCancel = findViewById(R.id.app_library_search_cancel);
        mSearchGlass = findViewById(R.id.app_library_search_glass);
        mDashboardView = findViewById(R.id.app_library_dashboard);
        mSearchResultsContainer = findViewById(R.id.app_library_search_results_container);
        mSearchResultsView = findViewById(R.id.app_library_search_results);
        mIndexScroller = findViewById(R.id.app_library_index_scroller);
        mExpansionOverlay = findViewById(R.id.app_library_expansion_overlay);
        mSearchBackdrop = findViewById(R.id.app_library_search_backdrop);
        mSearchBackdropGlass = findViewById(R.id.app_library_search_backdrop_glass);
        if (mSearchBackdrop != null) {
            mSearchBackdrop.setOnClickListener(v -> exitSearchMode());
        }

        mDashboardView.setAdapter(mDashboardAdapter);
        if (mAppLibraryModel != null && !mAppLibraryModel.getOrderedCategories().isEmpty()) {
            Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> initialData = new LinkedHashMap<>();
            for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
                initialData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
            }
            mDashboardAdapter.updateData(initialData);
        }
        mDashboardView.setOnOverscrollListener(translationY -> {
            invalidateGlassSurfaces();
        });

        Launcher launcher = Launcher.getLauncher(getContext());
        mSearchAdapter = new AppLibrarySearchAdapter(launcher);
        mSearchResultsView.setLayoutManager(new LinearLayoutManager(getContext()));
        mSearchResultsView.setAdapter(mSearchAdapter);

        ViewCompat.setOnApplyWindowInsetsListener(mSearchResultsView, (v, windowInsets) -> {
            handleImeVisibility(windowInsets.isVisible(WindowInsetsCompat.Type.ime()));
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            Insets navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int bottomPad = Math.max(imeInsets.bottom, navInsets.bottom);
            int baseBottom = (int) (48 * getResources().getDisplayMetrics().density);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    Math.max(baseBottom, bottomPad + (int) (12 * getResources().getDisplayMetrics().density)));
            return windowInsets;
        });



        // Search Container & Input focus & click listeners
        mSearchContainer.setOnClickListener(v -> {
            enterSearchMode();
            showKeyboard();
        });

        if (mSearchContent != null) {
            mSearchContent.setOnClickListener(v -> {
                enterSearchMode();
                showKeyboard();
            });
        }

        mSearchInput.setOnClickListener(v -> {
            enterSearchMode();
            showKeyboard();
        });

        mSearchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !mIsClearingSearch) {
                enterSearchMode();
            }
        });

        mSearchInput.setOnBackKeyListener(() -> {
            android.util.Log.d("BACK_DEBUG", "mSearchInput.onBackKey() called, mInSearchMode=" + mInSearchMode);
            if (mInSearchMode) {
                exitSearchMode(true);
                return true;
            }
            return false;
        });

        mSearchInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                android.util.Log.d("BACK_DEBUG", "mSearchInput.onKey(KEYCODE_BACK) called, action=" + event.getAction() + ", mInSearchMode=" + mInSearchMode);
                if (mInSearchMode) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        exitSearchMode(true);
                    }
                    return true;
                }
            }
            return false;
        });

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mSearchCancel.setOnClickListener(v -> exitSearchMode());

        mIndexScroller.setOnSectionSelectedListener(section -> {
            if (section != null && section.length() > 0) {
                int pos = mSearchAdapter.getPositionForSection(section.charAt(0));
                if (pos >= 0) {
                    RecyclerView.LayoutManager lm = mSearchResultsView.getLayoutManager();
                    if (lm instanceof LinearLayoutManager) {
                        ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 0);
                    } else {
                        mSearchResultsView.scrollToPosition(pos);
                    }
                }
            }
        });

        // Setup updates from AllAppsStore
        post(this::refreshApps);
    }

    public void updateApps(List<AppInfo> apps) {
        if (apps != null) {
            if (mAppLibraryModel != null) {
                mAppLibraryModel.updateApps(apps);
            }
            if (mSearchAdapter != null) {
                mSearchAdapter.setApps(apps);
            }
        }
    }

    public void refreshApps() {
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null && mContentContainer != null
                && AppLibraryCategoryExpandedView.getOpen(launcher) == null) {
            mContentContainer.setAlpha(1f);
            mContentContainer.setScaleX(1f);
            mContentContainer.setScaleY(1f);
        }
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null) {

            if (!mAppsStoreListenerRegistered) {
                mAppsStoreListenerRegistered = true;
                launcher.getAppsView().getAppsStore().addUpdateListener(mAppsStoreUpdateListener);
            }

            // Immediately bind existing categorized data if adapter is empty
            if (mAppLibraryModel != null && !mAppLibraryModel.getOrderedCategories().isEmpty()
                    && mDashboardAdapter != null && mDashboardAdapter.getItemCount() == 0) {
                Map<com.android.launcher3.applibrary.model.AppLibraryCategory, List<AppInfo>> newData = new LinkedHashMap<>();
                for (com.android.launcher3.applibrary.model.AppLibraryCategory cat : mAppLibraryModel.getOrderedCategories()) {
                    newData.put(cat, mAppLibraryModel.getAppsForCategory(cat));
                }
                mDashboardAdapter.updateData(newData);
            }

            AppInfo[] apps = launcher.getAppsView().getAppsStore().getApps();
            if (apps != null && apps.length > 0) {
                if (mSearchAdapter != null && mSearchAdapter.getItemCount() == 0) {
                    mSearchAdapter.setApps(Arrays.asList(apps));
                }
                // Only trigger updateApps if model has not categorized yet
                if (mAppLibraryModel != null && mAppLibraryModel.getOrderedCategories().isEmpty()) {
                    updateApps(Arrays.asList(apps));
                }
            }
        }
    }

    public void enterSearchMode() {
        Log.d("AppLib", "enterSearchMode inSearch=" + mInSearchMode
                + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
        if (mInSearchMode) return;
        mInSearchMode = true;
        cancelViewAnimators();
        if (mSearchResultsView != null) mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
        refreshApps();
        registerBackCallback();
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null) {
            launcher.onDragLayerHierarchyChanged();
        }

        if (mTransitionAnimator != null) {
            mTransitionAnimator.cancel();
        }

        // Prepare Search Backdrop
        if (mSearchBackdrop != null) {
            mSearchBackdrop.setVisibility(VISIBLE);
            mSearchBackdrop.setAlpha(0f);
            if (mSearchBackdropGlass != null) {
                mSearchBackdropGlass.ensureBound();
            }
        }

        // Prepare Search Results Container
        mSearchResultsContainer.setVisibility(VISIBLE);
        mSearchResultsContainer.setAlpha(0f);
        final float slideOffsetPx = 28f * getResources().getDisplayMetrics().density;
        mSearchResultsContainer.setTranslationY(slideOffsetPx);

        String query = mSearchInput.getText() != null ? mSearchInput.getText().toString() : "";
        mSearchAdapter.filter(query);
        mIndexScroller.setVisibility((query.trim().isEmpty()) ? VISIBLE : GONE);

        // Prepare Cancel Button
        mSearchCancel.setVisibility(VISIBLE);
        mSearchCancel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int targetCancelWidth = Math.max(mSearchCancel.getMeasuredWidth(),
                (int) (60 * getResources().getDisplayMetrics().density));
        mSearchCancel.setAlpha(0f);
        mSearchCancel.setTranslationX(targetCancelWidth * 0.4f);

        // Setup Dashboard for exit transition (scale downwards from top)
        mDashboardView.setVisibility(VISIBLE);
        mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
        mDashboardView.setPivotY(0f);

        final float startShift = mSearchContent != null ? mSearchContent.getTranslationX() : 0f;

        mTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
        mTransitionAnimator.setDuration(260);
        mTransitionAnimator.setInterpolator(new PathInterpolator(0.25f, 0.1f, 0.25f, 1f));
        mTransitionAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();

            // Search Content slide to left
            if (mSearchContent != null) {
                mSearchContent.setTranslationX((1f - progress) * startShift);
            }

            // Cancel button alpha and translation
            mSearchCancel.setAlpha(progress);
            mSearchCancel.setTranslationX((1f - progress) * (targetCancelWidth * 0.4f));

            // Search Backdrop fade-in
            if (mSearchBackdrop != null) {
                mSearchBackdrop.setAlpha(progress);
            }

            // Dashboard fade-out & subtle scale down
            mDashboardView.setAlpha(1f - progress);
            float dashScale = 1f - (0.04f * progress);
            mDashboardView.setScaleX(dashScale);
            mDashboardView.setScaleY(dashScale);

            // Search Results fade-in & upward slide
            mSearchResultsContainer.setAlpha(progress);
            mSearchResultsContainer.setTranslationY((1f - progress) * slideOffsetPx);
        });
        mTransitionAnimator.addListener(new EndOnlyListener() {
            @Override
            void onFinished() {
                if (mInSearchMode) applySearchSteadyState();
            }
        });
        mTransitionAnimator.start();
    }

    public void exitSearchMode() {
        exitSearchMode(true);
    }

    public void exitSearchMode(boolean animate) {
        Log.d("AppLib", "exitSearchMode(animate=" + animate + ") inSearch=" + mInSearchMode
                + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
        if (!mInSearchMode) return;
        mIsClearingSearch = true;
        try {
            mInSearchMode = false;
            mLastSearchExitedTime = SystemClock.uptimeMillis();
            hideKeyboard();

            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null) {
                if (launcher.getWorkspace() != null) {
                    launcher.getWorkspace().onAppLibrarySearchExited();
                }
                launcher.onDragLayerHierarchyChanged();
            }

            post(this::unregisterBackCallback);

            if (mTransitionAnimator != null) {
                mTransitionAnimator.cancel();
            }
            cancelViewAnimators();     // kill any onBackCancelled / onBackProgressed leftovers

            final float targetShift = calculateSearchCenteredShift();

            if (!animate) {
                mSearchInput.setText("");
                mSearchInput.clearFocus();
                if (mSearchContent != null) {
                    mSearchContent.setTranslationX(targetShift);
                }

                mSearchCancel.setVisibility(GONE);
                ViewGroup.LayoutParams lp = mSearchCancel.getLayoutParams();
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                mSearchCancel.setLayoutParams(lp);
                mSearchCancel.setAlpha(1f);
                mSearchCancel.setTranslationX(0f);

                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setVisibility(GONE);
                    mSearchBackdrop.setAlpha(1f);
                }

                mSearchResultsContainer.setVisibility(GONE);
                mSearchResultsContainer.setAlpha(1f);
                mSearchResultsContainer.setTranslationY(0f);

                settleDashboardVisible();
                return;
            }

            if (mSearchResultsView != null) {
                mSearchResultsView.stopScroll();
                mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
            }

            // Capture current (potentially scrubbed) states for smooth handoff
            final float startResultsAlpha = mSearchResultsContainer != null ? mSearchResultsContainer.getAlpha() : 1f;
            final float startResultsTy = mSearchResultsContainer != null ? mSearchResultsContainer.getTranslationY() : 0f;
            final float startBackdropAlpha = mSearchBackdrop != null ? mSearchBackdrop.getAlpha() : 1f;
            final boolean dashAlreadyVisible = mDashboardView.getVisibility() == VISIBLE && mDashboardView.getAlpha() > 0f;
            final float startDashAlpha = dashAlreadyVisible ? mDashboardView.getAlpha() : 0f;
            final float startDashScale = dashAlreadyVisible ? mDashboardView.getScaleX() : 0.96f;

            // Restore Dashboard to VISIBLE behind the fading search results
            mDashboardView.setVisibility(VISIBLE);
            mDashboardView.setAlpha(startDashAlpha);
            mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
            mDashboardView.setPivotY(0f);
            mDashboardView.setScaleX(startDashScale);
            mDashboardView.setScaleY(startDashScale);

            final float slideOffsetPx = 20f * getResources().getDisplayMetrics().density;
            final int startCancelWidth = mSearchCancel.getWidth() > 0
                    ? mSearchCancel.getWidth()
                    : (int) (60 * getResources().getDisplayMetrics().density);

            final float exitStartShift = mSearchContent != null ? mSearchContent.getTranslationX() : 0f;

            mTransitionAnimator = ValueAnimator.ofFloat(0f, 1f);
            mTransitionAnimator.setDuration(250);
            mTransitionAnimator.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
            mTransitionAnimator.addUpdateListener(animation -> {
                float p = (float) animation.getAnimatedValue();
                float inv = 1f - p;

                // Search Content slide back to center
                if (mSearchContent != null) {
                    mSearchContent.setTranslationX(exitStartShift + (targetShift - exitStartShift) * p);
                }

                // Cancel button alpha and translation
                mSearchCancel.setAlpha(inv);
                mSearchCancel.setTranslationX(p * (startCancelWidth * 0.4f));

                // Search Backdrop fade-out
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setAlpha(startBackdropAlpha * inv);
                }

                // Dashboard fade-in & scale up
                mDashboardView.setAlpha(startDashAlpha + (1f - startDashAlpha) * p);
                float dashScale = startDashScale + (1f - startDashScale) * p;
                mDashboardView.setScaleX(dashScale);
                mDashboardView.setScaleY(dashScale);

                // Search Results fade-out
                mSearchResultsContainer.setAlpha(startResultsAlpha * inv);
                mSearchResultsContainer.setTranslationY(startResultsTy + (slideOffsetPx - startResultsTy) * p);
            });
            mTransitionAnimator.addListener(new EndOnlyListener() {
                @Override
                void onFinished() {
                    applyBrowseSteadyState(targetShift);
                }
            });
            mTransitionAnimator.start();
        } finally {
            mIsClearingSearch = false;
        }
    }

    public boolean isInSearchMode() {
        return mInSearchMode;
    }

    public boolean canHandleBack() {
        return mInSearchMode;
    }

    public boolean wasRecentlyInSearchMode() {
        return SystemClock.uptimeMillis() - mLastSearchExitedTime < 500;
    }

    public boolean handleBack() {
        if (mInSearchMode) {
            exitSearchMode(true);
            return true;
        }
        return wasRecentlyInSearchMode();
    }

    private void registerBackCallback() {
        if (Utilities.ATLEAST_T && mBackCallback == null) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null) {
                    Launcher launcher = Launcher.getLauncher(getContext());
                    if (launcher != null) {
                        dispatcher = launcher.getOnBackInvokedDispatcher();
                    }
                }
                if (dispatcher != null) {
                    if (Utilities.ATLEAST_U) {
                        mBackCallback = getOnBackAnimationCallback();
                    } else {
                        mBackCallback = this::exitSearchMode;
                    }
                    dispatcher.registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_OVERLAY, mBackCallback);
                }
            } catch (Throwable t) {
                Log.w("AppLibraryPage", "Failed to register OnBackInvokedCallback", t);
            }
        }
    }

    private void unregisterBackCallback() {
        if (Utilities.ATLEAST_T && mBackCallback != null) {
            try {
                OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();
                if (dispatcher == null) {
                    Launcher launcher = Launcher.getLauncher(getContext());
                    if (launcher != null) {
                        dispatcher = launcher.getOnBackInvokedDispatcher();
                    }
                }
                if (dispatcher != null) {
                    dispatcher.unregisterOnBackInvokedCallback(mBackCallback);
                }
            } catch (Throwable ignored) {}
            mBackCallback = null;
        }
    }

    @NonNull
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public OnBackAnimationCallback getOnBackAnimationCallback() {
        return new OnBackAnimationCallback() {
            @Override
            public void onBackStarted(@NonNull BackEvent backEvent) {
                Log.d("AppLib", "onBackStarted inSearch=" + mInSearchMode
                        + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                        + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                        + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
                if (!mInSearchMode) return;                  // exit already owns the views
                mBackGestureActive = true;
                if (mTransitionAnimator != null && mTransitionAnimator.isRunning()) {
                    mTransitionAnimator.cancel();            // EndOnlyListener: no end logic runs
                }
                cancelViewAnimators();
                if (mSearchResultsView != null) {
                    mSearchResultsView.stopScroll();
                    mSearchResultsView.setLayerType(LAYER_TYPE_HARDWARE, null);
                }
                // The IME hide below is ours: don't let handleImeVisibility() start a 2nd exit.
                if (mImeWasVisible) mInternalKeyboardHide = true;
                hideKeyboard();

                mDashboardView.setVisibility(VISIBLE);
                mDashboardView.setPivotX(mDashboardView.getWidth() / 2f);
                mDashboardView.setPivotY(0f);
            }

            @Override
            public void onBackProgressed(@NonNull BackEvent backEvent) {
                if (!mInSearchMode) return;                  // late event after exit started
                float progress = backEvent.getProgress();
                if (mSearchResultsContainer != null) {
                    mSearchResultsContainer.setAlpha(1f - 0.45f * progress);
                    float slideOffsetPx = 20f * getResources().getDisplayMetrics().density;
                    mSearchResultsContainer.setTranslationY(progress * slideOffsetPx);
                }
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.setAlpha(1f - 0.35f * progress);
                }
                if (mDashboardView != null) {
                    mDashboardView.setVisibility(VISIBLE);
                    mDashboardView.setAlpha(0.6f * progress);
                    float dashScale = 0.96f + (0.04f * progress);
                    mDashboardView.setScaleX(dashScale);
                    mDashboardView.setScaleY(dashScale);
                }
            }

            @Override
            public void onBackCancelled() {
                Log.d("AppLib", "onBackCancelled inSearch=" + mInSearchMode
                        + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                        + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                        + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
                mBackGestureActive = false;
                // If exit already started (late cancel from unregisterBackCallback), it owns
                // every view. Touching the dashboard here is what leaves it at alpha 0.
                if (!mInSearchMode) return;

                if (mSearchResultsView != null) {
                    mSearchResultsView.setLayerType(LAYER_TYPE_NONE, null);
                }
                if (mSearchResultsContainer != null) {
                    mSearchResultsContainer.animate().alpha(1f).translationY(0f)
                            .setDuration(160).start();
                }
                if (mSearchBackdrop != null) {
                    mSearchBackdrop.animate().alpha(1f).setDuration(160).start();
                }
                if (mDashboardView != null) {
                    mDashboardView.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f)
                            .setDuration(160)
                            .withEndAction(() -> {
                                // withEndAction is NOT run if the animator is cancelled.
                                if (mInSearchMode) applySearchSteadyState();
                            }).start();
                }
            }

            @Override
            public void onBackInvoked() {
                Log.d("AppLib", "onBackInvoked inSearch=" + mInSearchMode
                        + " dash(vis=" + (mDashboardView != null ? mDashboardView.getVisibility() : -1)
                        + " a=" + (mDashboardView != null ? mDashboardView.getAlpha() : -1f) + ")"
                        + " anim=" + (mTransitionAnimator != null && mTransitionAnimator.isRunning()));
                mBackGestureActive = false;
                exitSearchMode(true);
            }
        };
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            android.util.Log.d("BACK_DEBUG", "AppLibraryPage.dispatchKeyEvent(KEYCODE_BACK) called, action=" + event.getAction() + ", mInSearchMode=" + mInSearchMode);
            if (mInSearchMode) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    exitSearchMode(true);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void checkRootImeVisibility() {
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            handleImeVisibility(rootInsets.isVisible(WindowInsets.Type.ime()));
        }
    }

    private void handleImeVisibility(boolean imeVisible) {
        if (mInSearchMode && mImeWasVisible && !imeVisible) {
            if (mInternalKeyboardHide) {
                mInternalKeyboardHide = false;
            } else {
                Log.d("BACK_DEBUG", "AppLibraryPage IME hidden externally while in search mode -> exiting search mode");
                mImeWasVisible = false;
                post(() -> {
                    if (mInSearchMode) {
                        exitSearchMode(true);
                    }
                });
                return;
            }
        }
        mImeWasVisible = imeVisible;
    }

    public void showKeyboard() {
        mInternalKeyboardHide = false;
        mSearchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideKeyboard() {
        if (mImeWasVisible) {
            mInternalKeyboardHide = true;
        }
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
    }

    private void performSearch(String query) {
        if (mIsClearingSearch || !mInSearchMode) return;
        mSearchAdapter.filter(query);
        // Show alphabet index scroller only when viewing full list (query is empty)
        mIndexScroller.setVisibility((query == null || query.trim().isEmpty()) ? VISIBLE : GONE);
    }

    public void saveScrollState() {
        if (mDashboardView != null) {
            mSavedScrollY = mDashboardView.computeVerticalScrollOffset();
        }
    }

    public void restoreScrollState() {
        if (mDashboardView != null) {
            int currentOffset = mDashboardView.computeVerticalScrollOffset();
            int deltaY = mSavedScrollY - currentOffset;
            if (deltaY != 0) {
                mDashboardView.scrollBy(0, deltaY);
            }
        }
    }

    public void invalidateGlassSurfaces() {
        if (mSearchGlass != null && mSearchGlass.getVisibility() == VISIBLE) {
            mSearchGlass.invalidate();
        }
        if (mSearchBackdropGlass != null && mSearchBackdropGlass.getVisibility() == VISIBLE) {
            mSearchBackdropGlass.invalidate();
        }
        if (mDashboardView != null) {
            for (int i = 0; i < mDashboardView.getChildCount(); i++) {
                View child = mDashboardView.getChildAt(i);
                if (child instanceof CategoryCardView) {
                    AppLibraryCategoryGlassView gv = ((CategoryCardView) child).getGlassView();
                    if (gv != null) {
                        gv.invalidate();
                    }
                } else if (child instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) child;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        View subChild = vg.getChildAt(j);
                        if (subChild instanceof CategoryCardView) {
                            AppLibraryCategoryGlassView gv = ((CategoryCardView) subChild).getGlassView();
                            if (gv != null) {
                                gv.invalidate();
                            }
                        }
                    }
                }
            }
        }
    }

    public void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        if (mDashboardView != null) {
            for (int i = 0; i < mDashboardView.getChildCount(); i++) {
                View child = mDashboardView.getChildAt(i);
                updateCardNotificationDots(child, updatedDots, packageUserKey);
            }
        }
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null) {
            AppLibraryCategoryExpandedView expandedView = AppLibraryCategoryExpandedView.getOpen(launcher);
            if (expandedView != null) {
                RecyclerView grid = expandedView.findViewById(R.id.expanded_category_grid);
                if (grid != null) {
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View child = grid.getChildAt(i);
                        if (child instanceof BubbleTextView) {
                            BubbleTextView btv = (BubbleTextView) child;
                            if (btv.getTag() instanceof ItemInfo) {
                                ItemInfo info = (ItemInfo) btv.getTag();
                                if (!packageUserKey.updateFromItemInfo(info) || updatedDots.test(packageUserKey)) {
                                    btv.applyDotState(info, true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateCardNotificationDots(View view, Predicate<PackageUserKey> updatedDots, PackageUserKey packageUserKey) {
        if (view instanceof CategoryCardView) {
            CategoryCardView card = (CategoryCardView) view;
            for (int j = 0; j < card.getChildCount(); j++) {
                View sub = card.getChildAt(j);
                if (sub instanceof BubbleTextView) {
                    BubbleTextView btv = (BubbleTextView) sub;
                    if (btv.getTag() instanceof ItemInfo) {
                        ItemInfo info = (ItemInfo) btv.getTag();
                        if (!packageUserKey.updateFromItemInfo(info) || updatedDots.test(packageUserKey)) {
                            btv.applyDotState(info, true);
                        }
                    }
                }
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int j = 0; j < vg.getChildCount(); j++) {
                updateCardNotificationDots(vg.getChildAt(j), updatedDots, packageUserKey);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!mInSearchMode && (mTransitionAnimator == null || !mTransitionAnimator.isRunning())) {
            updateSearchCenteredPosition();
        }
    }

    public void updateSearchCenteredPosition() {
        if (mInSearchMode) return;
        if (mSearchContent != null) {
            mSearchContent.setTranslationX(calculateSearchCenteredShift());
        }
    }

    private float calculateSearchCenteredShift() {
        if (mSearchContainer == null || mSearchInput == null) return 0f;
        int containerWidth = mSearchContainer.getWidth();
        if (containerWidth <= 0 && mSearchRow != null) {
            containerWidth = mSearchRow.getWidth();
        }
        if (containerWidth <= 0) return 0f;

        float density = getResources().getDisplayMetrics().density;
        float iconWidth = 17f * density;
        float iconPadding = 7f * density;
        float leftPadding = 14f * density;
        CharSequence hint = mSearchInput.getHint();
        float textWidth = hint != null ? mSearchInput.getPaint().measureText(hint.toString()) : 0f;
        float clusterWidth = iconWidth + iconPadding + textWidth;

        return Math.max(0f, (containerWidth - clusterWidth) / 2f - leftPadding);
    }

    @Override
    public void setInsets(Rect insets) {
        if (mContentContainer != null && insets != null) {
            int topPad = insets.top > 0
                    ? insets.top + (int) (8 * getResources().getDisplayMetrics().density)
                    : (int) (36 * getResources().getDisplayMetrics().density);
            mContentContainer.setPadding(
                    mContentContainer.getPaddingLeft(),
                    topPad,
                    mContentContainer.getPaddingRight(),
                    mContentContainer.getPaddingBottom()
            );
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAppLibraryModel != null) {
            mAppLibraryModel.addListener(mAppLibraryUpdateListener);
        }
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher != null && launcher.getAppsView() != null
                && launcher.getAppsView().getAppsStore() != null && !mAppsStoreListenerRegistered) {
            mAppsStoreListenerRegistered = true;
            launcher.getAppsView().getAppsStore().addUpdateListener(mAppsStoreUpdateListener);
        }
        getViewTreeObserver().addOnGlobalLayoutListener(mImeLayoutListener);
        WindowInsets rootInsets = getRootWindowInsets();
        if (rootInsets != null) {
            Insets statusBarInsets = WindowInsetsCompat.toWindowInsetsCompat(rootInsets)
                    .getInsets(WindowInsetsCompat.Type.statusBars());
            if (statusBarInsets.top > 0) {
                setInsets(new Rect(0, statusBarInsets.top, 0, 0));
            }
        }
        if (mInSearchMode) {
            registerBackCallback();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAppLibraryModel != null) {
            mAppLibraryModel.removeListener(mAppLibraryUpdateListener);
        }
        if (mAppsStoreListenerRegistered) {
            Launcher launcher = Launcher.getLauncher(getContext());
            if (launcher != null && launcher.getAppsView() != null
                    && launcher.getAppsView().getAppsStore() != null) {
                launcher.getAppsView().getAppsStore().removeUpdateListener(mAppsStoreUpdateListener);
            }
            mAppsStoreListenerRegistered = false;
        }
        getViewTreeObserver().removeOnGlobalLayoutListener(mImeLayoutListener);
        unregisterBackCallback();
    }
}
```

---

# src/com/android/launcher3/applibrary/view/AppLibraryBodyContainer.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/AppLibraryBodyContainer.java`  
**Lines**: 50 | **Bytes**: 1578

```java
package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Container wrapping the App Library dashboard and search results below the search row.
 * Strictly enforces hardware RenderNode clipping and canvas clipping to guarantee that
 * any overscroll, rubber-banding, or flinging inside the dashboard can never draw above
 * the search row or overlap search bar liquid glass shaders.
 */
public class AppLibraryBodyContainer extends FrameLayout {

    public AppLibraryBodyContainer(@NonNull Context context) {
        this(context, null);
    }

    public AppLibraryBodyContainer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryBodyContainer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClipToPadding(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            setClipBounds(new Rect(0, 0, w, h));
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
```

---

# src/com/android/launcher3/applibrary/view/CategoryCardView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/CategoryCardView.java`  
**Lines**: 310 | **Bytes**: 12587

```java
package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.applibrary.model.CategoryData;

import java.util.Collections;
import java.util.List;

public class CategoryCardView extends ViewGroup {

    private AppLibraryCategoryGlassView mGlassView;
    private int mIconSize;
    private int mSmallIconSize;
    private int mPadding;
    private int mGap;
    private CategoryData mCategoryData;

    public CategoryCardView(Context context) {
        this(context, null);
    }

    public CategoryCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CategoryCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mGlassView = new AppLibraryCategoryGlassView(getContext());
        addView(mGlassView, 0); // Add as first child (background)
        
        Launcher launcher = Launcher.getLauncher(getContext());
        DeviceProfile dp = launcher.getDeviceProfile();
        mIconSize = dp.iconSizePx;
        mSmallIconSize = mIconSize / 2;
        mPadding = getResources().getDimensionPixelSize(R.dimen.app_library_card_padding);
        mGap = getResources().getDimensionPixelSize(R.dimen.app_library_card_padding) / 2;

        // Clip children to card's squircle/rounded outline
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
                float radius = mGlassView != null ? mGlassView.getTargetRadius(view.getWidth(), view.getHeight()) : view.getWidth() * 0.14f;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        setClipChildren(false);
        setClipToPadding(false);

        // Tapping any part of the card that ISN'T one of the app icons
        // (background/gaps/padding) opens the full list. This is what makes
        // categories with <=3 apps openable at all — they never get a
        // MiniClusterView tile, so previously nothing on them was clickable.
        setOnClickListener(v -> {
            if (mCategoryData != null) {
                AppLibraryCategoryExpandedView.show(launcher, mCategoryData, this);
            }
        });
    }

    public void bind(CategoryData categoryData) {
        mCategoryData = categoryData;
        List<AppInfo> apps = categoryData.apps;
        Launcher launcher = Launcher.getLauncher(getContext());
        
        int largeIconsNeeded = apps.size() <= 4 ? apps.size() : 3;
        boolean needsMiniCluster = apps.size() > 4;
        
        // 1. Ensure slots 0 to largeIconsNeeded - 1 are BubbleTextViews
        for (int i = 0; i < largeIconsNeeded; i++) {
            int childIndex = i + 1; // child 0 is mGlassView
            View child = childIndex < getChildCount() ? getChildAt(childIndex) : null;
            BubbleTextView icon;
            if (child instanceof BubbleTextView) {
                icon = (BubbleTextView) child;
            } else {
                if (child != null) {
                    removeViewAt(childIndex);
                }
                icon = (BubbleTextView) launcher.getLayoutInflater().inflate(
                        R.layout.app_library_card_icon, this, false);
                icon.setOnClickListener(launcher.getItemOnClickListener());
                icon.setOnLongClickListener(v -> {
                    com.android.launcher3.popup.PopupContainerWithArrow.showForIcon((BubbleTextView) v);
                    return true;
                });
                addView(icon, childIndex);
            }
            icon.applyFromApplicationInfo(apps.get(i));
            icon.setText(""); // Ensure no text for App Library icons in the card
            icon.setCompoundDrawablePadding(0);
        }
        
        // 2. Handle slot 3 if needed
        int slot3Index = 4; // 1 + 3
        if (needsMiniCluster) {
            View child = slot3Index < getChildCount() ? getChildAt(slot3Index) : null;
            MiniClusterView miniCluster;
            if (child instanceof MiniClusterView) {
                miniCluster = (MiniClusterView) child;
                miniCluster.reset();
            } else {
                if (child != null) {
                    removeViewAt(slot3Index);
                }
                miniCluster = new MiniClusterView(getContext());
                addView(miniCluster, slot3Index);
            }
            miniCluster.setCategoryData(categoryData);
            int smallCount = Math.min(4, apps.size() - 3);
            for (int i = 0; i < smallCount; i++) {
                miniCluster.addIcon(apps.get(3 + i));
            }
        }
        
        // 3. Remove any remaining trailing views
        int totalExpectedChildren = 1 + largeIconsNeeded + (needsMiniCluster ? 1 : 0);
        while (getChildCount() > totalExpectedChildren) {
            removeViewAt(getChildCount() - 1);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Card is typically square
        int height = width;
        
        int childSize = (width - 2 * mPadding - mGap) / 2;
        int childMeasureSpec = MeasureSpec.makeMeasureSpec(childSize, MeasureSpec.EXACTLY);
        
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mGlassView) {
                child.measure(widthMeasureSpec, heightMeasureSpec);
            } else {
                child.measure(childMeasureSpec, childMeasureSpec);
            }
        }
        
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        int childSize = (width - 2 * mPadding - mGap) / 2;
        
        int iconIndex = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mGlassView) {
                child.layout(0, 0, width, height);
            } else {
                int row = iconIndex / 2;
                int col = iconIndex % 2;
                
                int left = mPadding + col * (childSize + mGap);
                int top = mPadding + row * (childSize + mGap);
                
                child.layout(left, top, left + childSize, top + childSize);
                iconIndex++;
            }
        }
    }

    public AppLibraryCategoryGlassView getGlassView() {
        return mGlassView;
    }

    public void setPreviewSlotHidden(int appIndex, boolean hidden) {
        boolean hasMiniCluster = mCategoryData != null && mCategoryData.apps != null && mCategoryData.apps.size() > 4;
        if (hasMiniCluster) {
            if (appIndex < 3) {
                int childIndex = appIndex + 1; // child 0 is mGlassView
                if (childIndex < getChildCount()) {
                    View child = getChildAt(childIndex);
                    if (child != null) {
                        child.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
                    }
                }
            } else if (getChildCount() > 4 && getChildAt(4) instanceof MiniClusterView) {
                ((MiniClusterView) getChildAt(4)).setSlotHidden(appIndex - 3, hidden);
            }
        } else {
            int childIndex = appIndex + 1;
            if (childIndex < getChildCount()) {
                View child = getChildAt(childIndex);
                if (child != null) {
                    child.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
                }
            }
        }
    }

    public void clearHiddenPreviewSlots() {
        for (int i = 1; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof MiniClusterView) {
                ((MiniClusterView) child).clearHiddenSlots();
            } else if (child != null) {
                child.setVisibility(View.VISIBLE);
            }
        }
    }

    public void getIconStartLocations(DragLayer dragLayer, List<PointF> outCenters, List<Float> outScales) {
        if (mCategoryData == null || mCategoryData.apps == null) return;
        int[] cardLoc = new int[2];
        dragLayer.getLocationInDragLayer(this, cardLoc);

        int totalApps = mCategoryData.apps.size();
        if (totalApps <= 4) {
            for (int i = 0; i < totalApps; i++) {
                View child = getChildAt(i + 1); // child 0 is mGlassView
                if (child != null) {
                    float cx = cardLoc[0] + child.getLeft() + child.getWidth() / 2f;
                    float cy = cardLoc[1] + child.getTop() + child.getHeight() / 2f;
                    outCenters.add(new PointF(cx, cy));
                    outScales.add(1.0f);
                }
            }
        } else {
            // First 3 are large icons
            for (int i = 0; i < 3; i++) {
                View child = getChildAt(i + 1);
                if (child != null) {
                    float cx = cardLoc[0] + child.getLeft() + child.getWidth() / 2f;
                    float cy = cardLoc[1] + child.getTop() + child.getHeight() / 2f;
                    outCenters.add(new PointF(cx, cy));
                    outScales.add(1.0f);
                }
            }
            // Slot 4 is MiniClusterView (child 4)
            View miniCluster = getChildAt(4);
            if (miniCluster instanceof MiniClusterView) {
                MiniClusterView mc = (MiniClusterView) miniCluster;
                float miniLeft = cardLoc[0] + mc.getLeft();
                float miniTop = cardLoc[1] + mc.getTop();
                int width = mc.getWidth();
                int clusterGap = mc.getGap();
                int miniIconSize = (width - clusterGap) / 2;
                float miniScale = (mIconSize > 0 && miniIconSize > 0)
                        ? (float) miniIconSize / (float) mIconSize
                        : 0.45f;
                if (miniScale <= 0f) miniScale = 0.45f;

                int clusterApps = totalApps - 3;
                for (int i = 0; i < clusterApps; i++) {
                    if (i < 4) {
                        int col = i % 2;
                        int row = i / 2;
                        float cx = miniLeft + col * (miniIconSize + clusterGap) + miniIconSize / 2f;
                        float cy = miniTop + row * (miniIconSize + clusterGap) + miniIconSize / 2f;
                        outCenters.add(new PointF(cx, cy));
                        outScales.add(miniScale);
                    } else {
                        float cx = miniLeft + mc.getWidth() / 2f;
                        float cy = miniTop + mc.getHeight() / 2f;
                        outCenters.add(new PointF(cx, cy));
                        outScales.add(miniScale);
                    }
                }
            }
        }
    }

    public List<Bitmap> getMiniClusterBitmaps() {
        if (getChildCount() > 4 && getChildAt(4) instanceof MiniClusterView) {
            return ((MiniClusterView) getChildAt(4)).getPreviewBitmaps();
        }
        return Collections.emptyList();
    }

    public PointF getMiniClusterCenter(DragLayer dragLayer) {
        if (getChildCount() > 4 && getChildAt(4) instanceof MiniClusterView) {
            View mc = getChildAt(4);
            int[] cardLoc = new int[2];
            dragLayer.getLocationInDragLayer(this, cardLoc);
            float cx = cardLoc[0] + mc.getLeft() + mc.getWidth() / 2f;
            float cy = cardLoc[1] + mc.getTop() + mc.getHeight() / 2f;
            return new PointF(cx, cy);
        }
        return null;
    }
}
```

---

# src/com/android/launcher3/applibrary/view/MiniClusterView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/MiniClusterView.java`  
**Lines**: 144 | **Bytes**: 4488

```java
package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.applibrary.model.CategoryData;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MiniClusterView extends View {

    private final List<ItemInfoWithIcon> mIcons = new ArrayList<>();
    private final List<FastBitmapDrawable> mDrawables = new ArrayList<>();
    private final boolean[] mHiddenSlots = new boolean[4];
    private int mGap;
    private CategoryData mCategoryData;

    public MiniClusterView(Context context) {
        this(context, null);
    }

    public MiniClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MiniClusterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Launcher launcher = Launcher.getLauncher(context);
        mGap = launcher.getDeviceProfile().iconDrawablePaddingPx / 2;
        
        setOnClickListener(v -> {
            Log.d("MiniClusterView", "Clicked on mini cluster!");
            if (mCategoryData != null) {
                Log.d("MiniClusterView", "CategoryData is not null, showing expanded view");
                View parent = (View) getParent();
                CategoryCardView card = (parent instanceof CategoryCardView) ? (CategoryCardView) parent : null;
                AppLibraryCategoryExpandedView.show(launcher, mCategoryData, card);
            } else {
                Log.d("MiniClusterView", "CategoryData is null!");
            }
        });
        
        setClickable(true);
        setFocusable(true);
    }


    public void reset() {
        mIcons.clear();
        mDrawables.clear();
        clearHiddenSlots();
        mCategoryData = null;
    }

    public void setCategoryData(CategoryData data) {
        mCategoryData = data;
    }

    public int getGap() {
        return mGap;
    }

    public void setSlotHidden(int slotIndex, boolean hidden) {
        if (slotIndex >= 0 && slotIndex < mHiddenSlots.length) {
            if (mHiddenSlots[slotIndex] != hidden) {
                mHiddenSlots[slotIndex] = hidden;
                invalidate();
            }
        }
    }

    public void clearHiddenSlots() {
        boolean changed = false;
        for (int i = 0; i < mHiddenSlots.length; i++) {
            if (mHiddenSlots[i]) {
                mHiddenSlots[i] = false;
                changed = true;
            }
        }
        if (changed) {
            invalidate();
        }
    }

    public void addIcon(ItemInfoWithIcon info) {
        if (mIcons.size() < 4) {
            mIcons.add(info);
            FastBitmapDrawable d = info.newIcon(getContext());
            d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            mDrawables.add(d);
            invalidate();
        }
    }

    public List<Bitmap> getPreviewBitmaps() {
        List<Bitmap> bitmaps = new ArrayList<>();
        for (ItemInfoWithIcon info : mIcons) {
            if (info != null && info.bitmap != null && info.bitmap.icon != null) {
                bitmaps.add(info.bitmap.icon);
            }
        }
        return bitmaps;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawables.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();
        int iconSize = (width - mGap) / 2;

        for (int i = 0; i < mDrawables.size(); i++) {
            if (i < mHiddenSlots.length && mHiddenSlots[i]) continue;
            FastBitmapDrawable d = mDrawables.get(i);
            int row = i / 2;
            int col = i % 2;

            int left = col * (iconSize + mGap);
            int top = row * (iconSize + mGap);

            canvas.save();
            canvas.translate(left, top);
            // Scale drawable to fit
            float scale = (float) iconSize / Math.max(d.getIntrinsicWidth(), d.getIntrinsicHeight());
            canvas.scale(scale, scale);
            d.draw(canvas);
            canvas.restore();
        }
    }
}
```

---

# src/com/android/launcher3/applibrary/view/AppLibraryCategoryExpandedView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/AppLibraryCategoryExpandedView.java`  
**Lines**: 341 | **Bytes**: 12945

```java
package com.android.launcher3.applibrary.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.applibrary.model.CategoryData;
import android.graphics.PointF;
import com.android.launcher3.dragndrop.DragLayer;

import java.util.ArrayList;
import java.util.List;

public class AppLibraryCategoryExpandedView extends AbstractFloatingView implements Insettable {
    private RecyclerView mGrid;
    private TextView mTitle;
    private CategoryCardView mSourceCard;
    private View mBackgroundPage;
    private AppLibraryFolderTransition mTransition;
    private final List<PointF> mStartCenters = new ArrayList<>();
    private final List<Float> mStartScales = new ArrayList<>();

    // Whether we have fully settled after the first open gesture.
    // The ACTION_UP that ends the mini-cluster tap also hits this overlay
    // (since it appears during the down→up sequence). We must swallow that
    // first UP without closing, so we wait until the next DOWN→UP cycle.
    private boolean mIgnoreFirstUp = true;

    private CategoryData mCategoryData;
    private final com.android.launcher3.applibrary.model.AppLibraryModel.AppLibraryUpdateListener mModelListener =
            this::onAppLibraryModelUpdated;

    private void onAppLibraryModelUpdated() {
        if (!mIsOpen || mCategoryData == null) return;
        if (mTransition != null && mTransition.isAnimating()) {
            post(() -> {
                if (mIsOpen && mCategoryData != null) {
                    onAppLibraryModelUpdated();
                }
            });
            return;
        }
        List<com.android.launcher3.model.data.AppInfo> updated =
                com.android.launcher3.applibrary.model.AppLibraryModel.getInstance(getContext())
                        .getAppsForCategory(mCategoryData.category);
        if (updated == null || updated.isEmpty()) {
            close(true);
        } else if (mCategoryData.apps != null && mCategoryData.apps.equals(updated)) {
            return;
        } else {
            mCategoryData = new CategoryData(mCategoryData.category, updated);
            mGrid.setAdapter(new ExpandedCategoryAdapter(Launcher.getLauncher(getContext()), updated));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        com.android.launcher3.applibrary.model.AppLibraryModel.getInstance(getContext())
                .addListener(mModelListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        com.android.launcher3.applibrary.model.AppLibraryModel.getInstance(getContext())
                .removeListener(mModelListener);
    }

    public AppLibraryCategoryExpandedView(Context context) {
        this(context, null);
    }

    public AppLibraryCategoryExpandedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryCategoryExpandedView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mIsOpen = false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setClipChildren(false);
        setClipToPadding(false);
        mGrid = findViewById(R.id.expanded_category_grid);
        mTitle = findViewById(R.id.expanded_category_title);

        if (mGrid != null) {
            mGrid.setClipChildren(false);
            mGrid.setClipToPadding(false);
            mGrid.setLayoutManager(new GridLayoutManager(getContext(), 4));
            android.view.ViewParent p = mGrid.getParent();
            while (p instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) p;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
                if (vg == this) break;
                p = vg.getParent();
            }
        }

        // Close on a genuine tap (not a scroll/fling) that lands on empty
        // grid space or outside icon bounds.
        GestureDetector emptySpaceTap = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        View child = mGrid.findChildViewUnder(e.getX(), e.getY());
                        if (child == null) {
                            close(true);
                        } else if (child instanceof com.android.launcher3.BubbleTextView) {
                            float childX = e.getX() - child.getLeft();
                            float childY = e.getY() - child.getTop();
                            if (((com.android.launcher3.BubbleTextView) child).shouldIgnoreTouchDown(childX, childY)) {
                                close(true);
                            }
                        }
                        return false; // never consume — let icons still get clicks
                    }
                });
        mGrid.setOnTouchListener((v, e) -> {
            emptySpaceTap.onTouchEvent(e);
            return false;
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't intercept — let child views receive events normally.
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // This is called only when a touch is NOT handled by any child view
        // (i.e. touch landed on empty background area).
        int action = ev.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            // Mark that we've seen a real DOWN on the background
            // (as opposed to the stale UP from the mini-cluster tap)
            mIgnoreFirstUp = false;
        } else if (action == MotionEvent.ACTION_UP) {
            if (!mIgnoreFirstUp) {
                // Genuine tap on empty space → close
                close(true);
            }
            // Reset for next gesture
            mIgnoreFirstUp = false;
        }

        // Always consume background touches so nothing behind gets them.
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public void setInsets(Rect insets) {
        // Do nothing to avoid getting margins applied by DragLayer
    }

    private void restoreBackgroundViews() {
        if (mTransition != null) {
            mTransition.cancelAndRestoreBackground();
        }
        if (mBackgroundPage != null) {
            mBackgroundPage.setScaleX(1f);
            mBackgroundPage.setScaleY(1f);
            mBackgroundPage.setAlpha(1f);
        }
        if (mSourceCard != null) {
            mSourceCard.clearHiddenPreviewSlots();
            mSourceCard.setAlpha(1f);
        }
    }

    @Override
    public void onBackProgressed(@androidx.annotation.NonNull android.window.BackEvent backEvent) {
        // No-op: AppLibraryFolderTransition handles custom geometry morphing
        // and expects overlay scale to remain 1f.
    }

    @Override
    public void onBackCancelled() {
        super.onBackCancelled();
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
    }

    @Override
    protected void handleClose(boolean animate) {
        if (!mIsOpen) return;
        mIsOpen = false;
        setScaleX(1f);
        setScaleY(1f);

        if (animate && mTransition != null) {
            mTransition.close(mStartCenters, mStartScales, this::removeViewFromDragLayer);
        } else if (animate) {
            restoreBackgroundViews();
            ObjectAnimator anim = ObjectAnimator.ofFloat(this, View.ALPHA, 0f);
            anim.setDuration(200);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeViewFromDragLayer();
                }
            });
            anim.start();
        } else {
            restoreBackgroundViews();
            removeViewFromDragLayer();
        }
    }

    private void removeViewFromDragLayer() {
        restoreBackgroundViews();
        DragLayer dragLayer = Launcher.getLauncher(getContext()).getDragLayer();
        dragLayer.removeView(this);
    }
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        ViewGroup.LayoutParams params = getLayoutParams();
        int topMargin = -1;
        if (params instanceof MarginLayoutParams) {
            topMargin = ((MarginLayoutParams) params).topMargin;
        }
        Log.d("ExpandedCategory", "onLayout bounds: left=" + l + " top=" + t + " right=" + r + " bottom=" + b + " topMargin=" + topMargin + " paddingTop=" + getPaddingTop() + " class=" + params.getClass().getName());
        View glass = findViewById(R.id.app_library_overlay_glass);
        if (glass != null) {
            Log.d("ExpandedCategory", "glass bounds: left=" + glass.getLeft() + " top=" + glass.getTop() + " right=" + glass.getRight() + " bottom=" + glass.getBottom());
        }
    }
    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_APP_LIBRARY_EXPANDED) != 0;
    }

    public static AppLibraryCategoryExpandedView getOpen(Launcher launcher) {
        return getOpenView(launcher, TYPE_APP_LIBRARY_EXPANDED);
    }

    public void bind(CategoryData data) {
        mCategoryData = data;
        mTitle.setText(data.category.getTitle());
        mGrid.setAdapter(new ExpandedCategoryAdapter(Launcher.getLauncher(getContext()), data.apps));
    }

    public static AppLibraryCategoryExpandedView show(Launcher launcher, CategoryData data) {
        return show(launcher, data, null);
    }

    public static AppLibraryCategoryExpandedView show(Launcher launcher, CategoryData data, CategoryCardView sourceCard) {
        Log.d("ExpandedCategory", "show() called for category: " + data.category.getTitle());
        closeOpenViews(launcher, false, TYPE_APP_LIBRARY_EXPANDED);
        AppLibraryCategoryExpandedView view = (AppLibraryCategoryExpandedView) launcher.getLayoutInflater()
                .inflate(R.layout.app_library_expanded_category, launcher.getDragLayer(), false);

        view.bind(data);
        view.mSourceCard = sourceCard;
        view.mBackgroundPage = launcher.findViewById(R.id.app_library_content_container);

        DragLayer dragLayer = launcher.getDragLayer();

        if (sourceCard != null) {
            sourceCard.getIconStartLocations(dragLayer, view.mStartCenters, view.mStartScales);
        }

        InsettableFrameLayout.LayoutParams lp = new InsettableFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.ignoreInsets = true;
        view.setLayoutParams(lp);

        view.mIsOpen = true;
        // Ignore the first ACTION_UP because it's the tail end of the
        // mini-cluster tap that opened this overlay.
        view.mIgnoreFirstUp = true;

        dragLayer.addView(view);
        launcher.onDragLayerHierarchyChanged();

        View glass = view.findViewById(R.id.app_library_overlay_glass);
        if (glass != null) {
            glass.setAlpha(0f);
        }
        view.mTitle.setAlpha(0f);
        view.setAlpha(1f);

        view.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!view.mIsOpen) {
                    return true;
                }
                view.mTransition = new AppLibraryFolderTransition(
                        view.mBackgroundPage,
                        view.mSourceCard,
                        view,
                        glass,
                        view.mTitle,
                        view.mGrid
                );
                view.mTransition.open(view.mStartCenters, view.mStartScales, () -> {
                    // settled
                });
                return true;
            }
        });

        return view;
    }
}
```

---

# src/com/android/launcher3/applibrary/view/AppLibraryIndexScrollerView.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/AppLibraryIndexScrollerView.java`  
**Lines**: 137 | **Bytes**: 4749

```java
package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class AppLibraryIndexScrollerView extends View {

    public interface OnSectionSelectedListener {
        void onSectionSelected(String section);
    }

    private static final String[] SECTIONS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
            "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
            "V", "W", "X", "Y", "Z", "#"
    };

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnSectionSelectedListener mListener;
    private int mLastSelectedIndex = -1;

    public AppLibraryIndexScrollerView(Context context) {
        this(context, null);
    }

    public AppLibraryIndexScrollerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryIndexScrollerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(false);
        float density = getResources().getDisplayMetrics().density;
        mPaint.setColor(0xCCFFFFFF); // Crisp semi-transparent white matching iOS
        mPaint.setTextSize(10f * density);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    }

    public void setOnSectionSelectedListener(OnSectionSelectedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defaultWidth = (int) (18f * density);
        int defaultHeight = (int) (18f * density * SECTIONS.length); // 18dp per letter
        int width = resolveSize(defaultWidth, widthMeasureSpec);
        int height = resolveSize(defaultHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        int width = getWidth();
        if (height <= 0 || width <= 0) return;

        float itemHeight = (float) height / SECTIONS.length;
        float fontCenterOffset = (mPaint.descent() + mPaint.ascent()) / 2f;

        for (int i = 0; i < SECTIONS.length; i++) {
            float x = width / 2f;
            float y = i * itemHeight + itemHeight / 2f - fontCenterOffset;
            canvas.drawText(SECTIONS[i], x, y, mPaint);
        }
    }

    private float mDownX;
    private float mDownY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                selectSectionAt(event.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                if (Math.abs(dx) > Math.abs(dy) * 2.0f && Math.abs(dx) > 40f) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return false;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                selectSectionAt(event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastSelectedIndex = -1;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void selectSectionAt(float y) {
        int height = getHeight();
        if (height <= 0) return;
        int index = (int) (y / ((float) height / SECTIONS.length));
        index = Math.max(0, Math.min(index, SECTIONS.length - 1));

        if (index != mLastSelectedIndex) {
            mLastSelectedIndex = index;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (mListener != null) {
                mListener.onSectionSelected(SECTIONS[index]);
            }
        }
    }
}
```

---

# src/com/android/launcher3/applibrary/view/ChangeCategoryPickerView.kt

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/ChangeCategoryPickerView.kt`  
**Lines**: 469 | **Bytes**: 18610

```kotlin
package com.android.launcher3.applibrary.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.applibrary.model.AppLibraryCategory
import com.android.launcher3.applibrary.model.AppLibraryModel
import com.android.launcher3.applibrary.model.CategoryProvider
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.InsettableFrameLayout
import foundation.e.bliss.glass.PopupGlassView
import foundation.e.bliss.glass.LiquidGlassToast
import foundation.e.bliss.graphics.ContinuousCornerGeometry
import foundation.e.bliss.graphics.GlassShapeSpec

/**
 * Authentic iOS-style Liquid Glass Category Picker dialog.
 * Allows users to manually assign an application to any App Library category.
 */
class ChangeCategoryPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractFloatingView(context, attrs, defStyleAttr), Insettable {

    private val launcher: Launcher = Launcher.getLauncher(context)
    private lateinit var itemInfo: ItemInfo
    private lateinit var scrimView: View
    private lateinit var cardContainer: FrameLayout
    private lateinit var glassBg: PopupGlassView
    private lateinit var frostBase: View
    private lateinit var contentColumn: LinearLayout

    init {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private fun isDarkTheme(): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun buildUI() {
        removeAllViews()
        val density = resources.displayMetrics.density
        val cardWidth = (280 * density).toInt()
        val cornerRadiusPx = 20f * density
        val isDark = isDarkTheme()
        val dividerColor = Color.parseColor("#2E000000")
        val hairlineThickness = maxOf(1, (0.55f * density).toInt())
        val geometry = ContinuousCornerGeometry()

        val rootFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false
        }

        // Full-screen dim scrim
        scrimView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#59000000"))
            alpha = 0f
            isClickable = true
            isFocusable = false
            setOnClickListener { close(true) }
        }
        rootFrame.addView(scrimView)

        // Content Column
        contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Header (Title & Subtitle)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
        }

        val appTitle = itemInfo.title?.toString() ?: "App"

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = context.getString(R.string.change_category_title)
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        headerLayout.addView(titleView)

        val messageView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = context.getString(R.string.change_category_message, appTitle)
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.parseColor("#7A000000"))
        }
        headerLayout.addView(messageView)
        contentColumn.addView(headerLayout)

        // Divider below header
        val headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
            setBackgroundColor(dividerColor)
        }
        contentColumn.addView(headerDivider)

        // Category list inside ScrollView
        val categoryProvider = CategoryProvider.getInstance(context)
        val currentCategory = categoryProvider.getCategory(itemInfo)

        val categoriesToDisplay = AppLibraryCategory.values().filter { it != AppLibraryCategory.SUGGESTIONS }

        val categoryListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val rowHeight = (44 * density).toInt()
        val rippleColor = Color.argb(25, 0, 0, 0)

        for (i in categoriesToDisplay.indices) {
            val cat = categoriesToDisplay[i]
            val isCurrent = (cat == currentCategory)

            if (i > 0) {
                val rowDivider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
                    setBackgroundColor(dividerColor)
                }
                categoryListContainer.addView(rowDivider)
            }

            val rowView = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
                isClickable = true
                isFocusable = true
                background = RippleDrawable(
                    ColorStateList.valueOf(rippleColor),
                    ColorDrawable(Color.TRANSPARENT),
                    ColorDrawable(Color.WHITE)
                )
                setPadding((18 * density).toInt(), 0, (18 * density).toInt(), 0)

                val label = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    }
                    text = cat.title
                    textSize = 16f
                    setTextColor(Color.BLACK)
                    if (isCurrent) {
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    } else {
                        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    }
                }
                addView(label)

                if (isCurrent) {
                    val checkmark = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            (20 * density).toInt(),
                            (20 * density).toInt()
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL or Gravity.END
                        }
                        setImageResource(R.drawable.ic_check_blue)
                    }
                    addView(checkmark)
                }

                setOnClickListener {
                    val pkg = (itemInfo.targetComponent?.packageName ?: itemInfo.targetPackage ?: "").lowercase()
                    if (pkg.isNotEmpty()) {
                        categoryProvider.setCategoryOverride(pkg, itemInfo.user, cat)
                        AppLibraryModel.getInstance(context).refreshCategories()

                        val expanded = AppLibraryCategoryExpandedView.getOpen(launcher)
                        expanded?.close(true)

                        val toastMessage = "Moved to ${cat.title}"
                        LiquidGlassToast.show(
                            launcher,
                            toastMessage,
                            R.drawable.ic_ios_check_circle
                        )
                    }
                    close(true)
                }
            }
            categoryListContainer.addView(rowView)
        }

        // Limit scroll view height so card doesn't exceed screen
        val screenHeight = resources.displayMetrics.heightPixels
        val maxScrollHeight = (screenHeight * 0.42f).toInt()

        val scrollView = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val mode = MeasureSpec.getMode(heightMeasureSpec)
                val size = MeasureSpec.getSize(heightMeasureSpec)
                val clampedHeight = if (mode == MeasureSpec.UNSPECIFIED) {
                    maxScrollHeight
                } else {
                    minOf(size, maxScrollHeight)
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(clampedHeight, MeasureSpec.AT_MOST))
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(categoryListContainer)
        }
        contentColumn.addView(scrollView)

        // Divider above Cancel
        val footerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hairlineThickness)
            setBackgroundColor(dividerColor)
        }
        contentColumn.addView(footerDivider)

        // Cancel Button
        val cancelBtn = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            text = "Cancel"
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#007AFF"))
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(rippleColor),
                ColorDrawable(Color.TRANSPARENT),
                ColorDrawable(Color.WHITE)
            )
            setOnClickListener { close(true) }
        }
        contentColumn.addView(cancelBtn)

        // Glass background layers
        glassBg = PopupGlassView(context).apply {
            setPopupRadius(cornerRadiusPx)
        }

        frostBase = View(context).apply {
            setBackgroundColor(if (isDark) Color.argb(40, 0, 0, 0) else Color.TRANSPARENT)
        }

        val maxCardHeight = (screenHeight * 0.75f).toInt()
        cardContainer = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val widthSpec = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
                contentColumn.measure(widthSpec, MeasureSpec.makeMeasureSpec(maxCardHeight, MeasureSpec.AT_MOST))
                val targetHeight = contentColumn.measuredHeight.coerceAtMost(maxCardHeight)
                val heightSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)

                glassBg.measure(widthSpec, heightSpec)
                frostBase.measure(widthSpec, heightSpec)

                setMeasuredDimension(cardWidth, targetHeight)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val w = right - left
                val h = bottom - top
                glassBg.layout(0, 0, w, h)
                frostBase.layout(0, 0, w, h)
                contentColumn.layout(0, 0, w, h)
                pivotX = w / 2f
                pivotY = h / 2f
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    if (view.width <= 0 || view.height <= 0) return
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val path = geometry.getPath(
                            width = view.width.toFloat(),
                            height = view.height.toFloat(),
                            radius = cornerRadiusPx,
                            smoothing = GlassShapeSpec.IOS_SMOOTHING
                        )
                        outline.setConvexPath(path)
                    } else {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
            }
            clipToOutline = true
        }

        cardContainer.addView(glassBg)
        cardContainer.addView(frostBase)
        cardContainer.addView(contentColumn)

        rootFrame.addView(cardContainer)
        addView(rootFrame)
    }

    private fun showInternal() {
        val dragLayer = launcher.dragLayer ?: return
        buildUI()

        val lp = InsettableFrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            ignoreInsets = true
        }
        layoutParams = lp
        elevation = 300f
        dragLayer.addView(this)
        mIsOpen = true
        launcher.updateDisallowBack()

        post {
            glassBg.bindToWallpaper()
        }

        cardContainer.scaleX = 0.88f
        cardContainer.scaleY = 0.88f
        cardContainer.alpha = 0f

        val spring = PathInterpolator(0.16f, 1f, 0.3f, 1f)
        val scaleXAnim = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 0.88f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 0.88f, 1f)
        val alphaAnim = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 0f, 1f)
        val scrimAnim = ObjectAnimator.ofFloat(scrimView, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleXAnim, scaleYAnim, alphaAnim, scrimAnim)
            duration = 260
            interpolator = spring
            start()
        }
    }

    override fun canHandleBack(): Boolean = true

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    private var isClosing = false

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen || isClosing) return
        isClosing = true
        mIsOpen = false
        launcher.updateDisallowBack()

        if (animate) {
            val exitScaleX = ObjectAnimator.ofFloat(cardContainer, View.SCALE_X, 1f, 0.92f)
            val exitScaleY = ObjectAnimator.ofFloat(cardContainer, View.SCALE_Y, 1f, 0.92f)
            val exitCardAlpha = ObjectAnimator.ofFloat(cardContainer, View.ALPHA, 1f, 0f)
            val exitScrimAlpha = ObjectAnimator.ofFloat(scrimView, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                playTogether(exitScaleX, exitScaleY, exitCardAlpha, exitScrimAlpha)
                duration = 150
                interpolator = AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        launcher.dragLayer?.removeView(this@ChangeCategoryPickerView)
                        launcher.updateDisallowBack()
                    }
                })
                start()
            }
        } else {
            launcher.dragLayer?.removeView(this)
            launcher.updateDisallowBack()
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and TYPE_OPTIONS_POPUP_DIALOG) != 0
    }

    override fun setInsets(insets: Rect?) {}

    companion object {
        @JvmStatic
        fun show(launcher: Launcher, itemInfo: ItemInfo): ChangeCategoryPickerView {
            closeAllOpenViews(launcher)
            val view = ChangeCategoryPickerView(launcher).apply {
                this.itemInfo = itemInfo
            }
            view.showInternal()
            return view
        }
    }
}
```

---

# src/com/android/launcher3/applibrary/view/AppLibraryFolderTransition.kt

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/AppLibraryFolderTransition.kt`  
**Lines**: 520 | **Bytes**: 19678

```kotlin
package com.android.launcher3.applibrary.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.icons.CrossFadeBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawable
import kotlin.math.pow

/**
 * Handles the authentic iOS App Library category folder expansion and collapse transitions.
 * Implements the Mini-Grid-Origin Staggered Radial Expansion with 4-Corner Cross-Fade.
 */
class AppLibraryFolderTransition(
    private val page: View?,
    private val folderTile: View?,
    private val overlay: ViewGroup,
    private val overlayGlass: View?,
    private val title: View,
    private val grid: RecyclerView
) {
    private val tempRect = Rect()
    private var anim: ValueAnimator? = null
    private var isClosing = false

    // Track original drawables on the corner hero views for cleanup
    private val originalDrawables = mutableMapOf<BubbleTextView, FastBitmapDrawable>()

    fun isAnimating(): Boolean = anim?.isRunning == true

    private fun clamp(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)
    private fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p
    private fun invLerp(a: Float, b: Float, v: Float): Float {
        if (a >= b) return 1f
        return ((v - a) / (b - a)).coerceIn(0f, 1f)
    }
    private fun spring(t: Float, k: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(k)

    /**
     * Item layout and animation configuration for a grid child.
     */
    private data class ItemConfig(
        val index: Int,
        val tier: Int, // 0 = Large Card Hero (Slots 0..2), 1 = Mini-Cluster Hero (Slots 3..6), 2 = Outer Perimeter Overflow, 3 = Inner Core Overflow
        val startX: Float,
        val startY: Float,
        val startScale: Float,
        val finalPos: PointF
    )

    /**
     * Computes the untransformed layout center of [child]'s icon graphic in [overlay] coordinates,
     * and sets [child]'s pivot to its icon graphic center so scaling lands directly on target slots.
     */
    private fun computeChildIconCenterAndSetPivot(child: View): PointF {
        child.translationX = 0f
        child.translationY = 0f
        child.scaleX = 1f
        child.scaleY = 1f

        var leftInOverlay = 0f
        var topInOverlay = 0f
        var cur: View? = child
        while (cur != null && cur !== overlay) {
            leftInOverlay += cur.left
            topInOverlay += cur.top
            cur = cur.parent as? View
        }

        val icX: Float
        val icY: Float
        if (child is BubbleTextView) {
            child.getIconBounds(tempRect)
            icX = tempRect.exactCenterX()
            icY = tempRect.exactCenterY()
        } else {
            icX = child.width / 2f
            icY = child.height / 2f
        }
        child.pivotX = icX
        child.pivotY = icY

        return PointF(leftInOverlay + icX, topInOverlay + icY)
    }

    private fun buildConfigs(
        n: Int,
        hasMiniCluster: Boolean,
        startCenters: List<PointF>,
        startScales: List<Float>,
        ovLeft: Float,
        ovTop: Float,
        miniGridCenterInOverlay: PointF,
        miniScale: Float,
        finals: List<PointF>,
        gridCenterX: Float,
        gridCenterY: Float,
        totalRows: Int
    ): ArrayList<ItemConfig> {
        val configs = ArrayList<ItemConfig>(n)
        for (i in 0 until n) {
            val finalPos = finals[i]
            val row = i / 4
            val col = i % 4

            if (hasMiniCluster) {
                if (i < 3) {
                    // Tier 0: Large Card Preview Hero (Slots 0..2)
                    // Starts from its exact resting slot on the card at scale 1.0f
                    val startCenter = if (i < startCenters.size) {
                        PointF(startCenters[i].x - ovLeft, startCenters[i].y - ovTop)
                    } else {
                        finalPos
                    }
                    val startS = if (i < startScales.size) startScales[i] else 1.0f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 0,
                            startX = startCenter.x,
                            startY = startCenter.y,
                            startScale = startS,
                            finalPos = finalPos
                        )
                    )
                } else if (i < 7) {
                    // Tier 1: Mini-Cluster Preview Hero (Slots 3..6)
                    // Starts from its visible sub-slot in the mini-cluster at scale miniScale (~0.45f)
                    val startCenter = if (i < startCenters.size) {
                        PointF(startCenters[i].x - ovLeft, startCenters[i].y - ovTop)
                    } else {
                        miniGridCenterInOverlay
                    }
                    val startS = if (i < startScales.size) startScales[i] else miniScale
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 1,
                            startX = startCenter.x,
                            startY = startCenter.y,
                            startScale = startS,
                            finalPos = finalPos
                        )
                    )
                } else if (col == 0 || col == 3 || row == 0 || row == totalRows - 1) {
                    // Tier 2: Outer Perimeter Overflow Apps (N >= 8)
                    val startX = miniGridCenterInOverlay.x + (finalPos.x - gridCenterX) * 0.16f
                    val startY = miniGridCenterInOverlay.y + (finalPos.y - gridCenterY) * 0.16f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 2,
                            startX = startX,
                            startY = startY,
                            startScale = 0.14f,
                            finalPos = finalPos
                        )
                    )
                } else {
                    // Tier 3: Inner Core Overflow Apps (Trailing Cushion)
                    val startX = miniGridCenterInOverlay.x + (finalPos.x - gridCenterX) * 0.16f
                    val startY = miniGridCenterInOverlay.y + (finalPos.y - gridCenterY) * 0.16f
                    configs.add(
                        ItemConfig(
                            index = i,
                            tier = 3,
                            startX = startX,
                            startY = startY,
                            startScale = 0.14f,
                            finalPos = finalPos
                        )
                    )
                }
            } else {
                // Small folder without mini-cluster (N <= 4)
                val startCenter = if (i < startCenters.size) {
                    PointF(startCenters[i].x - ovLeft, startCenters[i].y - ovTop)
                } else {
                    finalPos
                }
                val startS = if (i < startScales.size) startScales[i] else 1.0f
                configs.add(
                    ItemConfig(
                        index = i,
                        tier = 0,
                        startX = startCenter.x,
                        startY = startCenter.y,
                        startScale = startS,
                        finalPos = finalPos
                    )
                )
            }
        }
        return configs
    }

    fun open(
        startCenters: List<PointF>,
        startScales: List<Float>,
        onEnd: Runnable
    ) {
        isClosing = false
        val n = grid.childCount
        if (n == 0) {
            onEnd.run()
            return
        }

        val card = folderTile as? CategoryCardView
        val hasMiniCluster = n >= 5 && startCenters.size > 4

        // Setup dimming overlay
        overlayGlass?.visibility = View.VISIBLE
        overlayGlass?.setBackgroundColor(0x22000000.toInt())
        overlayGlass?.alpha = 0f

        val finals = (0 until n).map { computeChildIconCenterAndSetPivot(grid.getChildAt(it)) }
        val gridCenterX = if (finals.isNotEmpty()) finals.map { it.x }.average().toFloat() else overlay.width / 2f
        val gridCenterY = if (finals.isNotEmpty()) finals.map { it.y }.average().toFloat() else overlay.height / 2f
        val totalRows = (n + 3) / 4

        // Hide card's preview slots during flight so they do not duplicate
        val previewSlotsToHide = if (hasMiniCluster) minOf(n, 7) else n
        for (s in 0 until previewSlotsToHide) {
            card?.setPreviewSlotHidden(s, true)
        }

        // Overlay offsets relative to DragLayer
        val ovLeft = overlay.left.toFloat()
        val ovTop = overlay.top.toFloat()

        val miniGridCenterInOverlay = if (hasMiniCluster && startCenters.size >= 7) {
            PointF(
                (startCenters[3].x + startCenters[6].x) / 2f - ovLeft,
                (startCenters[3].y + startCenters[6].y) / 2f - ovTop
            )
        } else if (hasMiniCluster && startCenters.size > 3) {
            PointF(startCenters[3].x - ovLeft, startCenters[3].y - ovTop)
        } else if (startCenters.isNotEmpty()) {
            PointF(startCenters[0].x - ovLeft, startCenters[0].y - ovTop)
        } else {
            PointF(gridCenterX, gridCenterY)
        }

        val miniScale = if (hasMiniCluster && startScales.size > 3) startScales[3] else 0.45f

        val configs = buildConfigs(
            n, hasMiniCluster, startCenters, startScales, ovLeft, ovTop,
            miniGridCenterInOverlay, miniScale, finals, gridCenterX, gridCenterY, totalRows
        )

        overlay.scaleX = 1f
        overlay.scaleY = 1f

        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                applyFrame(p, configs, hasMiniCluster)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        applyFrame(1f, configs, hasMiniCluster)
                        restoreOriginalDrawables()
                        onEnd.run()
                    }
                }
            })
            start()
        }
    }

    fun close(
        startCenters: List<PointF>,
        startScales: List<Float>,
        onEnd: Runnable
    ) {
        if (isClosing) return
        isClosing = true

        val n = grid.childCount
        if (n == 0) {
            restoreBackground()
            onEnd.run()
            return
        }

        val card = folderTile as? CategoryCardView
        val hasMiniCluster = n >= 5 && startCenters.size > 4

        // Ensure card preview slots stay hidden during reverse flight
        val previewSlotsToHide = if (hasMiniCluster) minOf(n, 7) else n
        for (s in 0 until previewSlotsToHide) {
            card?.setPreviewSlotHidden(s, true)
        }

        val finals = (0 until n).map { computeChildIconCenterAndSetPivot(grid.getChildAt(it)) }
        val gridCenterX = if (finals.isNotEmpty()) finals.map { it.x }.average().toFloat() else overlay.width / 2f
        val gridCenterY = if (finals.isNotEmpty()) finals.map { it.y }.average().toFloat() else overlay.height / 2f
        val totalRows = (n + 3) / 4

        val ovLeft = overlay.left.toFloat()
        val ovTop = overlay.top.toFloat()

        val miniGridCenterInOverlay = if (hasMiniCluster && startCenters.size >= 7) {
            PointF(
                (startCenters[3].x + startCenters[6].x) / 2f - ovLeft,
                (startCenters[3].y + startCenters[6].y) / 2f - ovTop
            )
        } else if (hasMiniCluster && startCenters.size > 3) {
            PointF(startCenters[3].x - ovLeft, startCenters[3].y - ovTop)
        } else if (startCenters.isNotEmpty()) {
            PointF(startCenters[0].x - ovLeft, startCenters[0].y - ovTop)
        } else {
            PointF(gridCenterX, gridCenterY)
        }

        val miniScale = if (hasMiniCluster && startScales.size > 3) startScales[3] else 0.45f

        val configs = buildConfigs(
            n, hasMiniCluster, startCenters, startScales, ovLeft, ovTop,
            miniGridCenterInOverlay, miniScale, finals, gridCenterX, gridCenterY, totalRows
        )

        overlay.scaleX = 1f
        overlay.scaleY = 1f

        anim?.cancel()
        anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 290
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                applyFrame(p, configs, hasMiniCluster)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        restoreOriginalDrawables()
                        restoreBackground()
                        onEnd.run()
                    }
                }
            })
            start()
        }
    }

    private fun applyFrame(p: Float, configs: List<ItemConfig>, hasMiniCluster: Boolean) {
        // 1. Collapsed card stays stationary at scale 1.0 and fades smoothly
        val cardAlpha = 1.0f - invLerp(0.12f, 0.52f, p)
        folderTile?.let { ft ->
            ft.scaleX = 1f
            ft.scaleY = 1f
            ft.alpha = cardAlpha
        }
        page?.let { pg ->
            pg.scaleX = 1f
            pg.scaleY = 1f
            pg.alpha = cardAlpha
        }

        // 2. Dim Scrim Overlay
        overlayGlass?.let { og ->
            og.alpha = invLerp(0f, 0.6f, p) * 0.35f
        }

        // 3. Category Title
        val titleP = invLerp(0.42f, 0.88f, p)
        val titleAlpha = spring(titleP, 2.5f)
        val titleScale = lerp(0.88f, 1.0f, spring(titleP, 2.5f))
        title.alpha = titleAlpha
        title.scaleX = titleScale
        title.scaleY = titleScale

        // 4. Per-app choreographies
        for (cfg in configs) {
            val child = grid.getChildAt(cfg.index) ?: continue

            val curX: Float
            val curY: Float
            val curScale: Float
            val curAlpha: Float

            if (hasMiniCluster) {
                when (cfg.tier) {
                    0 -> {
                        // Tier 0: Large Card Preview Hero (Slots 0..2)
                        // Smoothly moves between card position and expanded grid position at scale 1.0
                        val pPos = spring(p, 3.2f)
                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = 1.0f
                        curAlpha = 1.0f

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.08f, 0.45f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                    1 -> {
                        // Tier 1: Mini-Cluster Preview Hero (Slots 3..6)
                        // Expands from miniScale to 1.0
                        val pPos = spring(invLerp(0.00f, 0.88f, p), 3.4f)
                        val pScale = spring(invLerp(0.00f, 0.82f, p), 3.8f)

                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = lerp(cfg.startScale, 1.0f, pScale)
                        curAlpha = 1.0f

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.12f, 0.55f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                    2 -> {
                        // Tier 2: Outer Perimeter Overflow Apps (k_pos = 2.7, k_scale = 2.5)
                        val pPos = spring(invLerp(0.06f, 0.98f, p), 2.7f)
                        val pScale = spring(invLerp(0.06f, 0.98f, p), 2.5f)

                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = lerp(0.14f, 1.0f, pScale)
                        curAlpha = invLerp(0.04f, 0.34f, p)

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.22f, 0.68f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                    else -> {
                        // Tier 3: Inner Core Overflow Apps / Trailing Cushion (k_pos = 2.0, k_scale = 1.75)
                        val pPos = spring(invLerp(0.16f, 0.98f, p), 2.0f)
                        val pScale = spring(invLerp(0.16f, 0.98f, p), 1.75f)

                        curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                        curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                        curScale = lerp(0.14f, 1.0f, pScale)
                        curAlpha = invLerp(0.04f, 0.34f, p)

                        if (child is BubbleTextView) {
                            val labelP = invLerp(0.22f, 0.68f, p)
                            child.setTextAlpha(labelP)
                        }
                    }
                }
            } else {
                // Folder without mini cluster (N <= 4)
                val pPos = spring(p, 3.0f)
                curX = lerp(cfg.startX, cfg.finalPos.x, pPos)
                curY = lerp(cfg.startY, cfg.finalPos.y, pPos)
                curScale = lerp(cfg.startScale, 1.0f, pPos)
                curAlpha = 1.0f

                if (child is BubbleTextView) {
                    val labelP = invLerp(0.15f, 0.55f, p)
                    child.setTextAlpha(labelP)
                }
            }

            child.translationX = curX - cfg.finalPos.x
            child.translationY = curY - cfg.finalPos.y
            child.scaleX = curScale
            child.scaleY = curScale
            child.alpha = curAlpha
        }
    }

    private fun restoreOriginalDrawables() {
        for ((view, orig) in originalDrawables) {
            view.setIcon(orig)
        }
        originalDrawables.clear()
    }

    fun cancelAndRestoreBackground() {
        anim?.cancel()
        restoreOriginalDrawables()
        restoreBackground()
    }

    fun restoreBackground() {
        (folderTile as? CategoryCardView)?.clearHiddenPreviewSlots()
        page?.let { p ->
            p.scaleX = 1f
            p.scaleY = 1f
            p.alpha = 1f
        }
        folderTile?.let { ft ->
            ft.scaleX = 1f
            ft.scaleY = 1f
            ft.alpha = 1f
        }
    }
}
```

---

# src/com/android/launcher3/applibrary/view/ExpandedCategoryAdapter.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/ExpandedCategoryAdapter.java`  
**Lines**: 88 | **Bytes**: 3065

```java
package com.android.launcher3.applibrary.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;

import java.util.List;

public class ExpandedCategoryAdapter extends RecyclerView.Adapter<ExpandedCategoryAdapter.ViewHolder> {

    private final Launcher mLauncher;
    private final List<AppInfo> mApps;

    public ExpandedCategoryAdapter(Launcher launcher, List<AppInfo> apps) {
        mLauncher = launcher;
        mApps = apps;
    }

    private int getCellSize(ViewGroup parent) {
        if (parent != null && parent.getWidth() > 0) {
            return parent.getWidth() / 4;
        }
        DeviceProfile dp = mLauncher.getDeviceProfile();
        if (dp != null && dp.getCellSize().x > 0) {
            return dp.getCellSize().x;
        }
        return ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        BubbleTextView icon = (BubbleTextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_icon, parent, false);
        int cellSize = getCellSize(parent);
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        if (lp != null) {
            lp.height = cellSize;
        } else {
            lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellSize);
        }
        icon.setLayoutParams(lp);
        icon.setCenterVertically(true);
        icon.setDisplay(BubbleTextView.DISPLAY_APP_LIBRARY_EXPANDED);
        icon.setTextColor(android.graphics.Color.WHITE);
        // Bind the standard item click listener so clicking an app launches it
        icon.setOnClickListener(mLauncher.getItemOnClickListener());
        return new ViewHolder(icon);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = mApps.get(position);
        holder.icon.applyFromApplicationInfo(app);
        holder.icon.setCenterVertically(true);
        int cellSize = getCellSize((ViewGroup) holder.icon.getParent());
        if (cellSize > 0 && holder.icon.getLayoutParams() != null && holder.icon.getLayoutParams().height != cellSize) {
            holder.icon.getLayoutParams().height = cellSize;
        }
        holder.icon.setOnLongClickListener(v -> {
            com.android.launcher3.popup.PopupContainerWithArrow.showForIcon(holder.icon);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mApps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        BubbleTextView icon;
        ViewHolder(BubbleTextView itemView) {
            super(itemView);
            icon = itemView;
        }
    }
}
```

---

# src/com/android/launcher3/applibrary/view/AppLibrarySearchAdapter.java

**Absolute Path**: `D:\Code\OS_Launcher\src/com/android/launcher3/applibrary/view/AppLibrarySearchAdapter.java`  
**Lines**: 189 | **Bytes**: 6712

```java
package com.android.launcher3.applibrary.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.AppInfo;

import com.android.launcher3.util.ComponentKey;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppLibrarySearchAdapter extends RecyclerView.Adapter<AppLibrarySearchAdapter.ViewHolder> {

    private final Launcher mLauncher;
    private final List<AppInfo> mAllApps = new ArrayList<>();
    private final List<AppInfo> mFilteredApps = new ArrayList<>();
    private final Map<AppInfo, String> mLowerTitleCache = new HashMap<>();
    private final Collator mCollator = Collator.getInstance(Locale.getDefault());

    public AppLibrarySearchAdapter(Launcher launcher) {
        mLauncher = launcher;
        mCollator.setStrength(Collator.PRIMARY);
        setHasStableIds(true);
    }

    private String mCurrentQuery = "";

    public void setApps(List<AppInfo> apps) {
        mAllApps.clear();
        mLowerTitleCache.clear();
        if (apps != null) {
            mAllApps.addAll(apps);
            Collections.sort(mAllApps, (a, b) -> {
                String titleA = a.title != null ? a.title.toString() : "";
                String titleB = b.title != null ? b.title.toString() : "";
                return mCollator.compare(titleA, titleB);
            });
            for (AppInfo app : mAllApps) {
                String title = app.title != null ? app.title.toString().toLowerCase(Locale.getDefault()) : "";
                mLowerTitleCache.put(app, title);
            }
        }
        filter(mCurrentQuery);
    }

    public void filter(String query) {
        mCurrentQuery = query != null ? query : "";
        mFilteredApps.clear();
        if (mCurrentQuery.trim().isEmpty()) {
            mFilteredApps.addAll(mAllApps);
        } else {
            String lower = mCurrentQuery.trim().toLowerCase(Locale.getDefault());
            for (AppInfo app : mAllApps) {
                String title = mLowerTitleCache.get(app);
                if (title != null && title.contains(lower)) {
                    mFilteredApps.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    private final Map<ComponentKey, Long> mStableIdMap = new HashMap<>();
    private long mNextStableId = 1;

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < mFilteredApps.size()) {
            AppInfo app = mFilteredApps.get(position);
            ComponentKey key = app.getComponentKey();
            if (key != null) {
                Long id = mStableIdMap.get(key);
                if (id == null) {
                    id = mNextStableId++;
                    mStableIdMap.put(key, id);
                }
                return id;
            }
            if (app.id != com.android.launcher3.model.data.ItemInfo.NO_ID) {
                return app.id;
            }
        }
        return RecyclerView.NO_ID;
    }

    public int getPositionForSection(char letter) {
        if (mFilteredApps.isEmpty()) return -1;
        char target = Character.toUpperCase(letter);
        if (target == '#') {
            for (int i = 0; i < mFilteredApps.size(); i++) {
                AppInfo app = mFilteredApps.get(i);
                if (app.title != null && app.title.length() > 0) {
                    char first = app.title.charAt(0);
                    if (!Character.isLetter(first)) return i;
                }
            }
            return mFilteredApps.size() - 1;
        }

        for (int i = 0; i < mFilteredApps.size(); i++) {
            AppInfo app = mFilteredApps.get(i);
            if (app.title != null && app.title.length() > 0) {
                char first = Character.toUpperCase(app.title.charAt(0));
                if (Character.isLetter(first) && first >= target) {
                    return i;
                }
            }
        }
        return mFilteredApps.size() - 1;
    }

    public boolean isEmpty() {
        return mFilteredApps.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_library_search_item, parent, false);
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = mFilteredApps.get(position);
        holder.title.setText(app.title);
        FastBitmapDrawable icon = app.newIcon(mLauncher);
        holder.icon.setImageDrawable(icon);
        holder.itemView.setTag(app);
        holder.itemView.setOnClickListener(v -> {
            View.OnClickListener clickListener = mLauncher.getItemOnClickListener();
            if (clickListener != null) {
                clickListener.onClick(v);
            } else {
                mLauncher.startActivitySafely(v, app.getIntent(), app);
            }
            v.postDelayed(() -> {
                AppLibraryPage alp = mLauncher.getAppLibraryPage();
                if (alp != null) {
                    alp.exitSearchMode();
                }
                SpotlightSearchOverlay spotlight = SpotlightSearchOverlay.getOpen(mLauncher);
                if (spotlight != null) {
                    spotlight.close(false);
                }
            }, 300);
        });
        View.OnLongClickListener longClickListener = v -> {
            holder.icon.setTag(app);
            holder.itemView.setTag(app);
            com.android.launcher3.popup.PopupContainerWithArrow.showForSearchItem(holder.icon, app);
            return true;
        };
        holder.itemView.setOnLongClickListener(longClickListener);
        holder.icon.setOnLongClickListener(longClickListener);
    }

    @Override
    public int getItemCount() {
        return mFilteredApps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.app_search_item_icon);
            title = itemView.findViewById(R.id.app_search_item_title);
        }
    }
}
```

---

