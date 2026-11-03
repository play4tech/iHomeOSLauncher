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
package com.android.launcher3.settings

import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import foundation.e.bliss.glass.FolderGlassView
import foundation.e.bliss.glass.LiquidGlassToast
import foundation.e.bliss.glass.PreviewDockGlassView
import foundation.e.bliss.glass.PreviewWallpaperView
import foundation.e.bliss.glass.SquircleImageView
import foundation.e.bliss.wallpaper.WallpaperRepository
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive Icon Size customization screen with real-time live preview of desktop apps,
 * folder, and dock icons.
 */
class IconSizeCustomizeActivity : FragmentActivity() {

    companion object {
        private const val TAG = "IconSizeCustomizeActivity"

        // Base DP dimensions at 1.0 (100%) scale
        private const val BASE_DESKTOP_ICON_DP = 52f
        private const val BASE_FOLDER_ICON_DP = 52f
        private const val BASE_MINI_ICON_DP = 16f
        private const val BASE_DOCK_ICON_DP = 44f
        private const val BASE_DOCK_HEIGHT_DP = 60f
        private const val BASE_LABEL_TEXT_SP = 11f
    }

    private lateinit var previewWallpaper: PreviewWallpaperView
    private lateinit var previewDockGlass: PreviewDockGlassView
    private lateinit var previewFolderGlass: FolderGlassView
    private lateinit var previewFolderContainer: FrameLayout

    private lateinit var desktopIconViews: List<SquircleImageView>
    private lateinit var desktopLabelViews: List<TextView>
    private lateinit var folderMiniIconViews: List<SquircleImageView>
    private lateinit var dockIconViews: List<SquircleImageView>
    private lateinit var labelFolder: TextView

    private lateinit var sliderScale: IosSlider
    private lateinit var txtScaleValue: TextView
    private lateinit var checkCompact: ImageView
    private lateinit var checkDefault: ImageView
    private lateinit var checkLarge: ImageView

    private var initialScale: Float = 1.0f
    private var currentScale: Float = 1.0f
    private var isSaving = false

    private val saveFinishHandler = Handler(Looper.getMainLooper())
    private val saveFinishRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Bliss_Onboarding)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_icon_size_customize)

        setupSystemBars()

        initialScale = try {
            LauncherPrefs.get(this).get(LauncherPrefs.ICON_SIZE_SCALE)
        } catch (e: Exception) {
            1.0f
        }.coerceIn(0.80f, 1.15f)
        currentScale = initialScale

        initHeader()
        initPreview()
        initControls()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }

    private fun initHeader() {
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            if (isSaving) {
                saveFinishHandler.removeCallbacks(saveFinishRunnable)
            }
            finish()
        }

        findViewById<View>(R.id.btn_save)?.setOnClickListener {
            saveAndFinish()
        }
    }

    override fun onBackPressed() {
        if (isSaving) {
            saveFinishHandler.removeCallbacks(saveFinishRunnable)
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        saveFinishHandler.removeCallbacks(saveFinishRunnable)
        super.onDestroy()
    }

    private fun saveAndFinish() {
        if (isSaving) return
        isSaving = true
        findViewById<View>(R.id.btn_save)?.isEnabled = false

        // 1. Commit scale to LauncherPrefs
        LauncherPrefs.get(this).put(LauncherPrefs.ICON_SIZE_SCALE, currentScale)

        // 2. Notify InvariantDeviceProfile to reconfigure grid & icons
        InvariantDeviceProfile.INSTANCE.get(this).onConfigChanged(applicationContext)

        // 3. Show authentic confirmation toast
        LiquidGlassToast.show(this, "Icon size saved", R.drawable.ic_ios_check_circle)

        // 4. Smoothly finish
        saveFinishHandler.postDelayed(saveFinishRunnable, 850L)
    }

    private fun initPreview() {
        previewWallpaper = findViewById(R.id.preview_wallpaper)!!
        previewDockGlass = findViewById(R.id.preview_dock_glass)!!
        previewFolderGlass = findViewById(R.id.preview_folder_glass)!!
        previewFolderContainer = findViewById(R.id.preview_folder_container)!!

        desktopIconViews = listOf(
            findViewById(R.id.icon_desktop_1)!!,
            findViewById(R.id.icon_desktop_2)!!,
            findViewById(R.id.icon_desktop_3)!!
        )

        desktopLabelViews = listOf(
            findViewById(R.id.label_desktop_1)!!,
            findViewById(R.id.label_desktop_2)!!,
            findViewById(R.id.label_desktop_3)!!
        )

        folderMiniIconViews = listOf(
            findViewById(R.id.folder_mini_1)!!,
            findViewById(R.id.folder_mini_2)!!,
            findViewById(R.id.folder_mini_3)!!,
            findViewById(R.id.folder_mini_4)!!
        )

        dockIconViews = listOf(
            findViewById(R.id.icon_dock_1)!!,
            findViewById(R.id.icon_dock_2)!!,
            findViewById(R.id.icon_dock_3)!!,
            findViewById(R.id.icon_dock_4)!!
        )

        labelFolder = findViewById(R.id.label_folder)!!

        loadWallpaperBackdrop()
        setupPreviewIcons()

        previewWallpaper.post {
            previewDockGlass.backdropSource = previewWallpaper
            previewDockGlass.enableDynamicBackground = false
            previewDockGlass.useShaderPipeline = true

            previewFolderGlass.backdropSource = previewWallpaper
            previewFolderGlass.enableDynamicBackground = false
            previewFolderGlass.useShaderPipeline = true

            updatePreviewScale(currentScale)
        }
    }

    private fun loadWallpaperBackdrop() {
        try {
            val repo = WallpaperRepository.INSTANCE.get(this)
            val wpBitmap = repo.currentWallpaper
            if (wpBitmap != null) {
                previewWallpaper.setImageBitmap(wpBitmap)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "WallpaperRepository bitmap unavailable", e)
        }

        try {
            val wpDrawable = WallpaperManager.getInstance(this).drawable
            if (wpDrawable != null) {
                previewWallpaper.setImageDrawable(wpDrawable)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "WallpaperManager drawable unavailable", e)
        }

        // Fallback gradient
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1C3144"), Color.parseColor("#007E8A"), Color.parseColor("#3BBA9C"))
        )
        previewWallpaper.setImageDrawable(gradient)
    }

    private fun setupPreviewIcons() {
        val realDockDrawables = mutableListOf<Drawable>()
        val realDesktopDrawables = mutableListOf<Drawable>()
        val realFolderMiniDrawables = mutableListOf<Drawable>()

        try {
            val appState = LauncherAppState.getInstance(this)
            val bgDataModel = appState.model.mBgDataModel
            synchronized(bgDataModel) {
                // Dock items
                val hotseatItems = bgDataModel.workspaceItems
                    .filter { it.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT }
                    .sortedBy { it.screenId }
                for (item in hotseatItems) {
                    if (realDockDrawables.size >= 4) break
                    val d = when (item) {
                        is ItemInfoWithIcon -> item.newIcon(this)
                        is FolderInfo -> item.contents.firstOrNull()?.newIcon(this)
                        else -> null
                    }
                    if (d != null) realDockDrawables.add(d)
                }

                // Desktop items
                val desktopItems = bgDataModel.workspaceItems
                    .filter { it.container == LauncherSettings.Favorites.CONTAINER_DESKTOP }
                    .sortedWith(compareBy({ it.screenId }, { it.cellY }, { it.cellX }))
                for (item in desktopItems) {
                    if (realDesktopDrawables.size >= 3) break
                    val d = when (item) {
                        is ItemInfoWithIcon -> item.newIcon(this)
                        is FolderInfo -> item.contents.firstOrNull()?.newIcon(this)
                        else -> null
                    }
                    if (d != null) realDesktopDrawables.add(d)
                }

                // Folder items
                if (bgDataModel.folders.size() > 0) {
                    val folder = bgDataModel.folders.valueAt(0)
                    if (folder != null) {
                        for (fItem in folder.contents) {
                            if (realFolderMiniDrawables.size >= 4) break
                            val d = fItem.newIcon(this)
                            if (d != null) realFolderMiniDrawables.add(d)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load items from model", e)
        }

        // Fallbacks
        val defaultDockApps = listOf(
            Pair(Intent(Intent.ACTION_DIAL), R.drawable.ic_preview_phone),
            Pair(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:") }, R.drawable.ic_preview_messages),
            Pair(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")), R.drawable.ic_preview_browser),
            Pair(Intent(MediaStore.ACTION_IMAGE_CAPTURE), R.drawable.ic_preview_camera)
        )
        for (i in 0 until 4) {
            val iconView = dockIconViews.getOrNull(i) ?: continue
            if (i < realDockDrawables.size) {
                iconView.setImageDrawable(realDockDrawables[i])
            } else {
                val fallbackIndex = (i - realDockDrawables.size).coerceAtMost(defaultDockApps.lastIndex)
                val (intent, fallbackRes) = defaultDockApps[fallbackIndex]
                iconView.setImageDrawable(resolveIcon(intent, fallbackRes))
            }
        }

        val defaultDesktopApps = listOf(
            Pair(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }, R.drawable.ic_preview_photos),
            Pair(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")), R.drawable.ic_preview_maps),
            Pair(Intent(Settings.ACTION_SETTINGS), R.drawable.ic_preview_settings)
        )
        for (i in 0 until 3) {
            val iconView = desktopIconViews.getOrNull(i) ?: continue
            if (i < realDesktopDrawables.size) {
                iconView.setImageDrawable(realDesktopDrawables[i])
            } else {
                val fallbackIndex = (i - realDesktopDrawables.size).coerceAtMost(defaultDesktopApps.lastIndex)
                val (intent, fallbackRes) = defaultDesktopApps[fallbackIndex]
                iconView.setImageDrawable(resolveIcon(intent, fallbackRes))
            }
        }

        for (i in folderMiniIconViews.indices) {
            val miniView = folderMiniIconViews[i]
            if (i < realFolderMiniDrawables.size) {
                miniView.setImageDrawable(realFolderMiniDrawables[i])
            }
        }
    }

    private fun resolveIcon(intent: Intent, fallbackRes: Int): Drawable {
        val rawDrawable = try {
            val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: packageManager.resolveActivity(intent, 0)
            info?.loadIcon(packageManager) ?: ContextCompat.getDrawable(this, fallbackRes)!!
        } catch (e: Exception) {
            ContextCompat.getDrawable(this, fallbackRes) ?: ColorDrawable(Color.GRAY)
        }

        return try {
            LauncherIcons.obtain(this).use { li ->
                val aid = if (rawDrawable is AdaptiveIconDrawable) {
                    rawDrawable
                } else {
                    li.wrapToAdaptiveIcon(rawDrawable)
                }
                li.createBadgedIconBitmap(aid).newIcon(this)
            }
        } catch (e: Exception) {
            rawDrawable
        }
    }

    private fun updatePreviewScale(scale: Float) {
        val density = resources.displayMetrics.density

        // Desktop apps and folder card scale in 100% lockstep
        val desktopAppPx = (BASE_DESKTOP_ICON_DP * density * scale).roundToInt()
        for (icon in desktopIconViews) {
            val lp = icon.layoutParams
            lp.width = desktopAppPx
            lp.height = desktopAppPx
            icon.layoutParams = lp
        }

        val folderLp = previewFolderContainer.layoutParams
        folderLp.width = desktopAppPx
        folderLp.height = desktopAppPx
        previewFolderContainer.layoutParams = folderLp

        // Folder mini icons scale
        val miniIconPx = (BASE_MINI_ICON_DP * density * scale).roundToInt()
        for (mini in folderMiniIconViews) {
            val lp = mini.layoutParams
            lp.width = miniSizePx(miniIconPx)
            lp.height = miniSizePx(miniIconPx)
            mini.layoutParams = lp
        }

        // Dock icons scale
        val dockIconPx = (BASE_DOCK_ICON_DP * density * scale).roundToInt()
        for (dockIcon in dockIconViews) {
            val lp = dockIcon.layoutParams
            lp.width = dockIconPx
            lp.height = dockIconPx
            dockIcon.layoutParams = lp
        }

        // Dock glass height scale
        val dockHeightPx = (BASE_DOCK_HEIGHT_DP * density * scale).roundToInt()
        val dockGlassLp = previewDockGlass.layoutParams
        dockGlassLp.height = dockHeightPx
        previewDockGlass.layoutParams = dockGlassLp

        // Text labels scale
        val labelSp = BASE_LABEL_TEXT_SP * scale
        for (label in desktopLabelViews) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)
        }
        labelFolder.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)

        // Readout text
        val percent = (scale * 100f).roundToInt()
        txtScaleValue.text = "$percent%"

        // Update preset checkmarks
        checkCompact.visibility = if (abs(scale - 0.85f) < 0.005f) View.VISIBLE else View.GONE
        checkDefault.visibility = if (abs(scale - 1.00f) < 0.005f) View.VISIBLE else View.GONE
        checkLarge.visibility = if (abs(scale - 1.15f) < 0.005f) View.VISIBLE else View.GONE
    }

    private fun miniSizePx(baseMiniPx: Int): Int {
        return baseMiniPx.coerceAtLeast(10)
    }

    private fun initControls() {
        sliderScale = findViewById(R.id.slider_scale)!!
        txtScaleValue = findViewById(R.id.txt_scale_value)!!
        checkCompact = findViewById(R.id.check_compact)!!
        checkDefault = findViewById(R.id.check_default)!!
        checkLarge = findViewById(R.id.check_large)!!

        sliderScale.min = 80f
        sliderScale.max = 115f
        sliderScale.steps = 35 // 1% per step

        val initialPercent = (currentScale * 100f).roundToInt()
        sliderScale.progress = initialPercent.toFloat()
        txtScaleValue.text = "$initialPercent%"

        sliderScale.onSliderChangeListener = object : IosSlider.OnSliderChangeListener {
            override fun onProgressChanged(slider: IosSlider, progress: Float, fromUser: Boolean) {
                val percent = progress.roundToInt()
                currentScale = percent / 100f
                updatePreviewScale(currentScale)
            }
        }

        // Tapping scale percentage resets to 100%
        txtScaleValue.setOnClickListener {
            sliderScale.progress = 100f
            currentScale = 1.0f
            updatePreviewScale(1.0f)
        }

        // Presets
        findViewById<View>(R.id.preset_compact)?.setOnClickListener {
            sliderScale.progress = 85f
            currentScale = 0.85f
            updatePreviewScale(0.85f)
        }

        findViewById<View>(R.id.preset_default)?.setOnClickListener {
            sliderScale.progress = 100f
            currentScale = 1.0f
            updatePreviewScale(1.0f)
        }

        findViewById<View>(R.id.preset_large)?.setOnClickListener {
            sliderScale.progress = 115f
            currentScale = 1.15f
            updatePreviewScale(1.15f)
        }

        // Reset to default
        findViewById<View>(R.id.btn_reset_defaults)?.setOnClickListener {
            sliderScale.progress = 100f
            currentScale = 1.0f
            updatePreviewScale(1.0f)
        }
    }
}
