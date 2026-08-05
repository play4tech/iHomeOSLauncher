package com.android.launcher3.applibrary.model

import android.content.Context
import com.android.launcher3.model.data.AppInfo

class AppLibraryModel(private val context: Context) {

    private val categoryProvider = CategoryProvider(context)
    val suggestionsProvider = SuggestionsProvider(context)
    val recentProvider = RecentProvider(context)

    // Maps a Category to a list of AppInfos
    private val categorizedApps = mutableMapOf<AppLibraryCategory, MutableList<AppInfo>>()
    
    // Ordered categories for display
    private var orderedCategories = emptyList<AppLibraryCategory>()

    private var allApps: List<AppInfo> = emptyList()

    private val listeners = mutableListOf<AppLibraryUpdateListener>()

    interface AppLibraryUpdateListener {
        fun onAppLibraryUpdated()
    }

    fun addListener(listener: AppLibraryUpdateListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: AppLibraryUpdateListener) {
        listeners.remove(listener)
    }

    fun updateApps(apps: List<AppInfo>) {
        this.allApps = apps
        categorizedApps.clear()

        // Distribute to categories
        for (app in apps) {
            val category = categoryProvider.getCategory(app)
            if (!categorizedApps.containsKey(category)) {
                categorizedApps[category] = mutableListOf()
            }
            categorizedApps[category]!!.add(app)
        }

        // Sort categories by predefined order, skipping empty ones
        val ordered = mutableListOf<AppLibraryCategory>()
        // Suggestions and Recently Added are special and always first if they have content
        ordered.add(AppLibraryCategory.SUGGESTIONS)
        ordered.add(AppLibraryCategory.RECENTLY_ADDED)

        AppLibraryCategory.values().forEach { cat ->
            if (cat != AppLibraryCategory.SUGGESTIONS && cat != AppLibraryCategory.RECENTLY_ADDED) {
                if (categorizedApps.containsKey(cat) && categorizedApps[cat]!!.isNotEmpty()) {
                    ordered.add(cat)
                    // Sort apps alphabetically within category
                    categorizedApps[cat]!!.sortBy { it.title.toString() }
                }
            }
        }
        orderedCategories = ordered

        notifyListeners()
    }

    fun getOrderedCategories(): List<AppLibraryCategory> {
        return orderedCategories
    }

    fun getAppsForCategory(category: AppLibraryCategory): List<AppInfo> {
        return when (category) {
            AppLibraryCategory.SUGGESTIONS -> suggestionsProvider.getSuggestions(allApps)
            AppLibraryCategory.RECENTLY_ADDED -> recentProvider.getRecentlyAdded(allApps)
            else -> categorizedApps[category] ?: emptyList()
        }
    }

    fun recordAppLaunch(app: AppInfo) {
        suggestionsProvider.recordLaunch(app)
        notifyListeners()
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            listener.onAppLibraryUpdated()
        }
    }
}
