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

            com.android.launcher3.util.Executors.MAIN_EXECUTOR.execute main@ {
                if (seq != pendingSequence) return@main
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
