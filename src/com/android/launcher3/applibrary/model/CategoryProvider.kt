package com.android.launcher3.applibrary.model

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.launcher3.model.data.AppInfo

class CategoryProvider(private val context: Context) {

    fun getCategory(appInfo: AppInfo): AppLibraryCategory {
        val packageName = appInfo.componentName?.packageName ?: ""
        val appName = appInfo.title.toString().lowercase()

        // 1. Developer Rules (e.g. Adobe, Microsoft)
        getCategoryByDeveloper(packageName)?.let { return it }

        // 2. Known Package DB (Local Mapping)
        getCategoryByPackage(packageName)?.let { return it }

        // 3. Keyword Rules based on App Name
        getCategoryByKeyword(appName)?.let { return it }

        // 4. ApplicationInfo.category (API 26+)
        val pm = context.packageManager
        try {
            val appInfoFlags = pm.getApplicationInfo(packageName, 0)
            getCategoryByAppInfoCategory(appInfoFlags.category)?.let { return it }
        } catch (e: Exception) {
            // Ignore if package not found
        }

        // 5. Fallback
        return AppLibraryCategory.OTHER
    }

    private fun getCategoryByDeveloper(packageName: String): AppLibraryCategory? {
        if (packageName.startsWith("com.adobe.")) return AppLibraryCategory.CREATIVITY
        if (packageName.startsWith("com.microsoft.")) return AppLibraryCategory.PRODUCTIVITY_FINANCE
        if (packageName.startsWith("com.jetbrains.")) return AppLibraryCategory.PRODUCTIVITY_FINANCE
        if (packageName.startsWith("com.google.android.apps.docs")) return AppLibraryCategory.PRODUCTIVITY_FINANCE
        if (packageName.startsWith("com.google.android.apps.fitness")) return AppLibraryCategory.HEALTH_FITNESS
        return null
    }

    private fun getCategoryByPackage(packageName: String): AppLibraryCategory? {
        return when (packageName) {
            "com.whatsapp", "com.facebook.katana", "com.instagram.android", "com.twitter.android", "com.snapchat.android", "org.telegram.messenger", "com.zhiliaoapp.musically" -> AppLibraryCategory.SOCIAL
            "com.spotify.music", "com.netflix.mediaclient", "com.google.android.youtube", "tv.twitch.android.app" -> AppLibraryCategory.ENTERTAINMENT
            "com.android.chrome", "org.mozilla.firefox", "com.reddit.frontpage" -> AppLibraryCategory.INFORMATION_READING
            "com.android.calculator2", "com.google.android.calculator", "com.android.deskclock", "com.google.android.deskclock", "com.android.settings" -> AppLibraryCategory.UTILITIES
            "com.google.android.apps.maps", "com.waze", "com.moovit", "com.ubercab", "com.lyft.android" -> AppLibraryCategory.TRAVEL
            "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.ubercab.eats", "com.zhiliaoapp.musically" -> AppLibraryCategory.SHOPPING_FOOD
            "com.nianticlabs.pokemongo", "com.supercell.clashofclans", "com.king.candycrushsaga" -> AppLibraryCategory.GAMES
            "com.google.android.apps.photos", "com.google.android.camera", "com.android.camera2" -> AppLibraryCategory.CREATIVITY
            else -> null
        }
    }

    private fun getCategoryByKeyword(appName: String): AppLibraryCategory? {
        if (appName.contains("chat") || appName.contains("messenger") || appName.contains("social") || appName.contains("meet")) return AppLibraryCategory.SOCIAL
        if (appName.contains("game") || appName.contains("puzzle") || appName.contains("rpg") || appName.contains("casino")) return AppLibraryCategory.GAMES
        if (appName.contains("bank") || appName.contains("finance") || appName.contains("pay") || appName.contains("wallet") || appName.contains("money")) return AppLibraryCategory.PRODUCTIVITY_FINANCE
        if (appName.contains("music") || appName.contains("video") || appName.contains("tv") || appName.contains("movie") || appName.contains("player")) return AppLibraryCategory.ENTERTAINMENT
        if (appName.contains("news") || appName.contains("reader") || appName.contains("book") || appName.contains("comic")) return AppLibraryCategory.INFORMATION_READING
        if (appName.contains("shop") || appName.contains("store") || appName.contains("food") || appName.contains("eat") || appName.contains("delivery")) return AppLibraryCategory.SHOPPING_FOOD
        if (appName.contains("travel") || appName.contains("map") || appName.contains("transit") || appName.contains("ride") || appName.contains("taxi") || appName.contains("flight")) return AppLibraryCategory.TRAVEL
        if (appName.contains("health") || appName.contains("fitness") || appName.contains("workout") || appName.contains("diet") || appName.contains("run")) return AppLibraryCategory.HEALTH_FITNESS
        if (appName.contains("school") || appName.contains("learn") || appName.contains("study") || appName.contains("education") || appName.contains("course")) return AppLibraryCategory.EDUCATION
        if (appName.contains("photo") || appName.contains("camera") || appName.contains("draw") || appName.contains("edit") || appName.contains("design") || appName.contains("art")) return AppLibraryCategory.CREATIVITY
        if (appName.contains("tool") || appName.contains("util") || appName.contains("calc") || appName.contains("clock") || appName.contains("weather") || appName.contains("file")) return AppLibraryCategory.UTILITIES
        if (appName.contains("note") || appName.contains("task") || appName.contains("todo") || appName.contains("mail") || appName.contains("doc") || appName.contains("office")) return AppLibraryCategory.PRODUCTIVITY_FINANCE
        return null
    }

    private fun getCategoryByAppInfoCategory(category: Int): AppLibraryCategory? {
        return when (category) {
            ApplicationInfo.CATEGORY_AUDIO -> AppLibraryCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_VIDEO -> AppLibraryCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_GAME -> AppLibraryCategory.GAMES
            ApplicationInfo.CATEGORY_IMAGE -> AppLibraryCategory.CREATIVITY
            ApplicationInfo.CATEGORY_MAPS -> AppLibraryCategory.TRAVEL
            ApplicationInfo.CATEGORY_NEWS -> AppLibraryCategory.INFORMATION_READING
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppLibraryCategory.PRODUCTIVITY_FINANCE
            ApplicationInfo.CATEGORY_SOCIAL -> AppLibraryCategory.SOCIAL
            else -> null
        }
    }
}
