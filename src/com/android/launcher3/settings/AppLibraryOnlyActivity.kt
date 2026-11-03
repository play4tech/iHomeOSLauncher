/*
 * Copyright (C) 2024 MURENA SAS
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

package com.android.launcher3.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RemovedAppStore
import foundation.e.bliss.glass.LiquidGlassToast
import foundation.e.bliss.glass.SquircleImageView
import java.util.Locale

/**
 * Manages applications that are set to "App Library Only" (hidden from the Home Screen).
 * Allows users to toggle whether an application appears on the Home Screen grid/dock
 * or is kept exclusively in the App Library.
 */
class AppLibraryOnlyActivity : FragmentActivity() {

    data class AppItem(
        val componentName: ComponentName,
        val user: UserHandle,
        val label: String,
        val icon: Drawable,
        var isAppLibraryOnly: Boolean,
        val initialIsAppLibraryOnly: Boolean
    )

    private val allApps = mutableListOf<AppItem>()
    private val displayedApps = mutableListOf<AppItem>()
    private lateinit var adapter: AppLibraryOnlyAdapter

    private lateinit var editSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var txtSectionTitle: TextView
    private lateinit var txtEmptyState: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var recyclerView: RecyclerView

    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Bliss_Onboarding)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_library_only)

        setupSystemBars()
        initViews()
        loadApps()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }

    private fun initViews() {
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            applyChangesAndFinish()
        }

        findViewById<View>(R.id.btn_done)?.setOnClickListener {
            applyChangesAndFinish()
        }

        editSearch = findViewById(R.id.edit_search)!!
        btnClearSearch = findViewById(R.id.btn_clear_search)!!
        txtSectionTitle = findViewById(R.id.txt_section_title)!!
        txtEmptyState = findViewById(R.id.txt_empty_state)!!
        progressLoading = findViewById(R.id.progress_loading)!!
        recyclerView = findViewById(R.id.recycler_apps)!!
        recyclerView.itemAnimator = null

        adapter = AppLibraryOnlyAdapter(displayedApps) {
            updateSectionHeader()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard()
                }
            }
        })

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterApps(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            editSearch.setText("")
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editSearch.windowToken, 0)
    }

    private fun loadApps() {
        progressLoading.visibility = View.VISIBLE
        txtEmptyState.visibility = View.GONE

        Executors.THREAD_POOL_EXECUTOR.execute {
            val launcherApps = getSystemService(LauncherApps::class.java) ?: return@execute
            val userManager = getSystemService(UserManager::class.java) ?: return@execute
            val removedApps = RemovedAppStore.getRemovedApps(applicationContext)
            val myPackage = packageName
            val densityDpi = resources.displayMetrics.densityDpi
            val appState = LauncherAppState.getInstanceNoCreate()
            val iconCache = appState?.iconCache

            val loadedList = mutableListOf<AppItem>()

            for (user in userManager.userProfiles) {
                val activities: List<LauncherActivityInfo> = try {
                    launcherApps.getActivityList(null, user)
                } catch (e: Exception) {
                    emptyList()
                }

                for (info in activities) {
                    val pkg = info.applicationInfo.packageName
                    if (pkg == myPackage) continue

                    val cn = info.componentName
                    val label = try {
                        info.label?.toString() ?: pkg
                    } catch (e: Exception) {
                        pkg
                    }
                    val icon = try {
                        if (iconCache != null) {
                            val appInfo = com.android.launcher3.model.data.AppInfo(applicationContext, info, user)
                            iconCache.getTitleAndIcon(appInfo, info, false)
                            appInfo.newIcon(applicationContext)
                        } else {
                            info.getBadgedIcon(densityDpi)
                        }
                    } catch (e: Exception) {
                        try {
                            info.getBadgedIcon(densityDpi)
                        } catch (e2: Exception) {
                            packageManager.defaultActivityIcon
                        }
                    }
                    val isRemoved = removedApps.contains(cn.flattenToString())

                    loadedList.add(
                        AppItem(
                            componentName = cn,
                            user = user,
                            label = label,
                            icon = icon,
                            isAppLibraryOnly = isRemoved,
                            initialIsAppLibraryOnly = isRemoved
                        )
                    )
                }
            }

            loadedList.sortBy { it.label.lowercase(Locale.getDefault()) }

            runOnUiThread {
                progressLoading.visibility = View.GONE
                allApps.clear()
                allApps.addAll(loadedList)
                filterApps(editSearch.text.toString().trim())
            }
        }
    }

    private fun filterApps(query: String) {
        displayedApps.clear()
        if (query.isEmpty()) {
            displayedApps.addAll(allApps)
        } else {
            val lower = query.lowercase(Locale.getDefault())
            for (app in allApps) {
                if (app.label.lowercase(Locale.getDefault()).contains(lower)) {
                    displayedApps.add(app)
                }
            }
        }
        adapter.notifyDataSetChanged()
        updateSectionHeader()

        txtEmptyState.visibility = if (displayedApps.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (displayedApps.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateSectionHeader() {
        val total = displayedApps.size
        val hiddenCount = allApps.count { it.isAppLibraryOnly }
        if (editSearch.text.isNullOrEmpty()) {
            txtSectionTitle.text = if (hiddenCount > 0) {
                "ALL APPS ($total) • $hiddenCount IN APP LIBRARY ONLY"
            } else {
                "ALL APPS ($total)"
            }
        } else {
            txtSectionTitle.text = "MATCHING APPS ($total)"
        }
    }

    override fun onBackPressed() {
        applyChangesAndFinish()
    }

    private fun applyChangesAndFinish() {
        if (isSaving) return
        isSaving = true

        val modified = allApps.filter { it.isAppLibraryOnly != it.initialIsAppLibraryOnly }
        if (modified.isEmpty()) {
            finish()
            return
        }

        // Apply changes to RemovedAppStore immediately
        val context = applicationContext
        for (item in modified) {
            val flatCn = item.componentName.flattenToString()
            if (item.isAppLibraryOnly) {
                RemovedAppStore.markRemoved(context, flatCn)
            } else {
                RemovedAppStore.unmarkRemoved(context, flatCn)
            }
        }

        LiquidGlassToast.show(this, "Home Screen updated", R.drawable.ic_ios_check_circle)

        // Perform DB sync & model reload on MODEL_EXECUTOR
        Executors.MODEL_EXECUTOR.execute {
            try {
                val appState = LauncherAppState.getInstanceNoCreate()
                if (appState != null) {
                    val dbController = appState.model.modelDbController
                    val tableName = LauncherSettings.Favorites.getFavoritesTableName()

                    val newlyHidden = modified.filter { it.isAppLibraryOnly }
                    for (item in newlyHidden) {
                        val flatCn = item.componentName.flattenToString()
                        dbController.delete(tableName, "intent LIKE ?", arrayOf("%$flatCn%"))
                        if (tableName != LauncherSettings.Favorites.TABLE_NAME) {
                            dbController.delete(
                                LauncherSettings.Favorites.TABLE_NAME,
                                "intent LIKE ?",
                                arrayOf("%$flatCn%")
                            )
                        }
                    }

                    dbController.deleteEmptyFolders()

                    Executors.MAIN_EXECUTOR.execute {
                        appState.model.forceReload()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppLibraryOnlyActivity", "Error committing workspace changes", e)
            }
        }

        finish()
    }

    class AppLibraryOnlyAdapter(
        private val items: List<AppItem>,
        private val onStateToggled: () -> Unit
    ) : RecyclerView.Adapter<AppLibraryOnlyAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val rowItem: View = view.findViewById(R.id.row_app_item)!!
            val imgIcon: SquircleImageView = view.findViewById(R.id.img_app_icon)!!
            val txtName: TextView = view.findViewById(R.id.txt_app_name)!!
            val txtStatus: TextView = view.findViewById(R.id.txt_app_status)!!
            val switchState: Switch = view.findViewById(R.id.switch_app_state)!!
            val dividerLine: View = view.findViewById(R.id.divider_line)!!
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_library_only, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            holder.imgIcon.setImageDrawable(item.icon)
            holder.txtName.text = item.label

            bindToggleState(holder, item)

            holder.rowItem.setOnClickListener {
                item.isAppLibraryOnly = !item.isAppLibraryOnly
                holder.switchState.isChecked = item.isAppLibraryOnly
                bindStatusText(holder, item)
                onStateToggled()
            }

            holder.dividerLine.visibility = if (position == items.size - 1) View.GONE else View.VISIBLE
        }

        private fun bindToggleState(holder: ViewHolder, item: AppItem) {
            bindStatusText(holder, item)
            holder.switchState.setOnCheckedChangeListener(null)
            holder.switchState.isChecked = item.isAppLibraryOnly
        }

        private fun bindStatusText(holder: ViewHolder, item: AppItem) {
            if (item.isAppLibraryOnly) {
                holder.txtStatus.text = "App Library Only"
                holder.txtStatus.setTextColor(holder.itemView.context.getColor(R.color.ios_icon_indigo))
            } else {
                holder.txtStatus.text = "Shown on Home Screen"
                holder.txtStatus.setTextColor(holder.itemView.context.getColor(R.color.onboarding_text_secondary))
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
