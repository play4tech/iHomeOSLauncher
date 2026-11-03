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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.ads.AdManager
import com.android.launcher3.ads.ConsentManager
import com.android.launcher3.billing.BillingManager
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.icons.LauncherIcons
import com.google.android.gms.ads.AdView
import foundation.e.bliss.glass.LiquidGlassToast
import com.android.launcher3.model.data.ItemInfoWithIcon
import foundation.e.bliss.glass.FolderGlassView
import foundation.e.bliss.glass.GlassConfig
import foundation.e.bliss.glass.PreviewDockGlassView
import foundation.e.bliss.glass.PreviewWallpaperView
import foundation.e.bliss.glass.SearchPillGlassView
import foundation.e.bliss.wallpaper.WallpaperRepository
import java.util.Locale

/**
 * Interactive Liquid Glass effect customization screen with a real-time live preview.
 * Sliders adjust optics, blur, dispersion, and specular highlights in real time.
 */
class GlassCustomizeActivity : FragmentActivity() {

    private lateinit var previewDockGlass: PreviewDockGlassView
    private var previewFolderGlass: FolderGlassView? = null
    private var previewSearchGlass: SearchPillGlassView? = null
    private lateinit var previewWallpaper: PreviewWallpaperView
    private var currentSettings: GlassConfig.GlassSettings = GlassConfig.GlassSettings()

    private val sliderBindings = mutableListOf<SliderBinding>()
    private val switchBindings = mutableListOf<SwitchBinding>()

    private var bannerAdView: AdView? = null

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
        setContentView(R.layout.activity_glass_customize)

        setupSystemBars()
        currentSettings = GlassConfig.loadSettings(this)

        initHeader()
        initPreview()
        initSliders()
        initSwitches()
        initResetButton()
        initAds()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }

    private fun initHeader() {
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            if (isSaving) {
                saveFinishHandler.removeCallbacks(saveFinishRunnable)
                finish()
            } else {
                saveAndFinish(fromSaveButton = false)
            }
        }

        findViewById<View>(R.id.btn_save)?.setOnClickListener {
            saveAndFinish(fromSaveButton = true)
        }
    }

    override fun onBackPressed() {
        if (isSaving) {
            saveFinishHandler.removeCallbacks(saveFinishRunnable)
            finish()
            return
        }
        saveAndFinish(fromSaveButton = false)
    }

    override fun onResume() {
        super.onResume()
        bannerAdView?.resume()
    }

    override fun onPause() {
        super.onPause()
        bannerAdView?.pause()
        GlassConfig.saveSettings(this, currentSettings)
    }

    override fun onDestroy() {
        saveFinishHandler.removeCallbacks(saveFinishRunnable)
        bannerAdView?.destroy()
        bannerAdView = null
        super.onDestroy()
    }

    private fun initAds() {
        if (BillingManager.isProUser(this)) return

        ConsentManager.gatherConsent(this) { canRequestAds ->
            if (canRequestAds && !isFinishing && !isDestroyed) {
                AdManager.initialize(this) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            val container = findViewById<ViewGroup>(R.id.ad_banner_container)
                            if (container != null) {
                                bannerAdView = AdManager.loadBanner(this, container)
                            }
                            AdManager.preloadInterstitial(this)
                        }
                    }
                }
            }
        }
    }

    private fun initPreview() {
        previewWallpaper = findViewById(R.id.preview_wallpaper)!!
        previewDockGlass = findViewById(R.id.preview_dock_glass)!!
        previewFolderGlass = findViewById(R.id.preview_folder_glass)
        previewSearchGlass = findViewById(R.id.preview_search_glass)

        loadWallpaperBackdrop()
        setupPreviewIcons()

        // Configure live dock glass and preview elements
        previewWallpaper.post {
            previewDockGlass.backdropSource = previewWallpaper
            previewDockGlass.enableDynamicBackground = false
            previewDockGlass.useShaderPipeline = true

            previewFolderGlass?.let {
                it.backdropSource = previewWallpaper
                it.enableDynamicBackground = false
                it.useShaderPipeline = true
            }

            previewSearchGlass?.let {
                it.backdropSource = previewWallpaper
                it.enableDynamicBackground = false
                it.useShaderPipeline = true
            }

            applyToPreview()
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

        // Fallback elegant gradient
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1C3144"), Color.parseColor("#007E8A"), Color.parseColor("#3BBA9C"))
        )
        previewWallpaper.setImageDrawable(gradient)
    }

    private fun setupPreviewIcons() {
        val dockIconViews = listOf(
            findViewById<ImageView>(R.id.icon_phone),
            findViewById<ImageView>(R.id.icon_messages),
            findViewById<ImageView>(R.id.icon_browser),
            findViewById<ImageView>(R.id.icon_camera)
        )

        val desktopIconViews = listOf(
            findViewById<ImageView>(R.id.icon_desktop_1),
            findViewById<ImageView>(R.id.icon_desktop_2),
            findViewById<ImageView>(R.id.icon_desktop_3)
        )

        val miniIconViews = listOf(
            findViewById<ImageView>(R.id.folder_mini_1),
            findViewById<ImageView>(R.id.folder_mini_2),
            findViewById<ImageView>(R.id.folder_mini_3),
            findViewById<ImageView>(R.id.folder_mini_4)
        )

        // 1. Try to load the user's actual homescreen items from LauncherModel
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
                    if (d != null) {
                        realDockDrawables.add(d)
                    }
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
                    if (d != null) {
                        realDesktopDrawables.add(d)
                    }
                }

                // Folder items if any folder exists
                if (bgDataModel.folders.size() > 0) {
                    val folder = bgDataModel.folders.valueAt(0)
                    if (folder != null) {
                        for (fItem in folder.contents) {
                            if (realFolderMiniDrawables.size >= 4) break
                            val d = fItem.newIcon(this)
                            if (d != null) {
                                realFolderMiniDrawables.add(d)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load items from model", e)
        }

        // 2. Default fallback intents & drawables for dock slots
        val defaultDockApps = listOf(
            Pair(Intent(Intent.ACTION_DIAL), R.drawable.ic_preview_phone),
            Pair(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:") }, R.drawable.ic_preview_messages),
            Pair(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")), R.drawable.ic_preview_browser),
            Pair(Intent(MediaStore.ACTION_IMAGE_CAPTURE), R.drawable.ic_preview_camera)
        )

        for (i in 0 until 4) {
            val iconView = dockIconViews[i] ?: continue
            if (i < realDockDrawables.size) {
                iconView.setImageDrawable(realDockDrawables[i])
            } else {
                val fallbackIndex = (i - realDockDrawables.size).coerceAtMost(defaultDockApps.lastIndex)
                val (intent, fallbackRes) = defaultDockApps[fallbackIndex]
                iconView.setImageDrawable(resolveIcon(intent, fallbackRes))
            }
        }

        // 3. Default fallback intents & drawables for desktop slots
        val defaultDesktopApps = listOf(
            Pair(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }, R.drawable.ic_preview_photos),
            Pair(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")), R.drawable.ic_preview_maps),
            Pair(Intent(Settings.ACTION_SETTINGS), R.drawable.ic_preview_settings)
        )

        for (i in 0 until 3) {
            val iconView = desktopIconViews[i] ?: continue
            if (i < realDesktopDrawables.size) {
                iconView.setImageDrawable(realDesktopDrawables[i])
            } else {
                val fallbackIndex = (i - realDesktopDrawables.size).coerceAtMost(defaultDesktopApps.lastIndex)
                val (intent, fallbackRes) = defaultDesktopApps[fallbackIndex]
                iconView.setImageDrawable(resolveIcon(intent, fallbackRes))
            }
        }

        // 4. Bind mini icons if real folder contents were found
        for (i in 0 until miniIconViews.size) {
            val miniView = miniIconViews[i] ?: continue
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
            Log.w(TAG, "Failed shaping icon with LauncherIcons", e)
            rawDrawable
        }
    }

    private fun initSliders() {
        sliderBindings.clear()

        // 1. Refraction Height
        bindSlider(
            containerId = R.id.slider_refraction_height,
            title = "Refraction Height",
            subtitle = "Edge displacement at rim (${GlassConfig.DEFAULT_REFRACTION_HEIGHT.toInt()} px default)",
            min = 0f,
            max = 150f,
            steps = 150,
            getter = { it.refractionHeight },
            setter = { s, v -> s.refractionHeight = v },
            formatter = { String.format(Locale.US, "%.1f px", it) }
        )

        // 2. Bevel Width
        bindSlider(
            containerId = R.id.slider_bevel_width,
            title = "Bevel Width",
            subtitle = "Glass thickness band (${GlassConfig.DEFAULT_BEVEL_WIDTH.toInt()} px default)",
            min = 2f,
            max = 80f,
            steps = 78,
            getter = { it.bevelWidth },
            setter = { s, v -> s.bevelWidth = v },
            formatter = { String.format(Locale.US, "%.1f px", it) }
        )

        // 3. Blur Amount
        bindSlider(
            containerId = R.id.slider_blur_amount,
            title = "Blur Amount",
            subtitle = "Background frosted blur (${String.format(Locale.US, "%.2f", GlassConfig.DEFAULT_BLUR_AMOUNT)} default)",
            min = 0.0f,
            max = 1.0f,
            steps = 100,
            getter = { it.blurAmount },
            setter = { s, v -> s.blurAmount = v },
            formatter = { String.format(Locale.US, "%.2f", it) },
            onStopTracking = {
                foundation.e.bliss.wallpaper.WallpaperBlurRenderer.INSTANCE.get(this).regenerateBlur()
            }
        )

        // 4. White Tint Opacity
        bindSlider(
            containerId = R.id.slider_white_tint_opacity,
            title = "White Tint Opacity",
            subtitle = "Translucent white body tint (${(Color.alpha(GlassConfig.DEFAULT_GLASS_TINT) / 255f * 100).toInt()}% default)",
            min = 0f,
            max = 100f,
            steps = 100,
            getter = { (Color.alpha(it.glassTint) / 255f) * 100f },
            setter = { s, v ->
                val alpha = (v / 100f * 255f).toInt().coerceIn(0, 255)
                s.glassTint = Color.argb(alpha, 255, 255, 255)
            },
            formatter = { String.format(Locale.US, "%.0f%%", it) }
        )

        // 5. Saturation Boost
        bindSlider(
            containerId = R.id.slider_saturation,
            title = "Saturation Boost",
            subtitle = "Color vibrance boost (default ${GlassConfig.DEFAULT_SATURATION.toInt()}%)",
            min = 100f,
            max = 250f,
            steps = 150,
            getter = { it.saturation },
            setter = { s, v -> s.saturation = v },
            formatter = { String.format(Locale.US, "%.0f%%", it) }
        )

        // 5. Chromatic Dispersion
        bindSlider(
            containerId = R.id.slider_dispersion_strength,
            title = "Chromatic Dispersion",
            subtitle = "Spectral rainbow fringe at rim (${String.format(Locale.US, "%.2f", GlassConfig.DEFAULT_DISPERSION_STRENGTH)} default)",
            min = 0.0f,
            max = 0.40f,
            steps = 400,
            getter = { it.dispersionStrength },
            setter = { s, v -> s.dispersionStrength = v },
            formatter = { String.format(Locale.US, "%.2f", it) }
        )

        // 6. Specular Highlight Width
        bindSlider(
            containerId = R.id.slider_edge_highlight_width,
            title = "Specular Highlight Width",
            subtitle = "Rim border highlight width (default ${GlassConfig.DEFAULT_EDGE_HIGHLIGHT_BORDER_WIDTH} px)",
            min = 0.0f,
            max = 5.0f,
            steps = 50,
            getter = { it.edgeHighlightBorderWidth },
            setter = { s, v -> s.edgeHighlightBorderWidth = v },
            formatter = { String.format(Locale.US, "%.1f px", it) }
        )

        // 7. Specular Highlight Opacity
        bindSlider(
            containerId = R.id.slider_edge_highlight_opacity,
            title = "Highlight Opacity",
            subtitle = "Specular highlight brightness (default ${GlassConfig.DEFAULT_EDGE_HIGHLIGHT_OPACITY.toInt()}%)",
            min = 0f,
            max = 100f,
            steps = 100,
            getter = { it.edgeHighlightOpacity },
            setter = { s, v -> s.edgeHighlightOpacity = v },
            formatter = { String.format(Locale.US, "%.0f%%", it) }
        )

        // 8. Black Border Width
        bindSlider(
            containerId = R.id.slider_border_width,
            title = "Border Width",
            subtitle = "Outline thickness (default ${GlassConfig.DEFAULT_BORDER_WIDTH} dp)",
            min = 0.05f,
            max = 1.50f,
            steps = 29,
            getter = { it.borderWidth },
            setter = { s, v -> s.borderWidth = v },
            formatter = { String.format(Locale.US, "%.2f dp", it) }
        )

        // 9. Black Border Opacity
        bindSlider(
            containerId = R.id.slider_border_opacity,
            title = "Border Opacity",
            subtitle = "Peak border opacity (default ${GlassConfig.DEFAULT_BORDER_OPACITY.toInt()}%)",
            min = 10f,
            max = 100f,
            steps = 90,
            getter = { it.borderOpacity },
            setter = { s, v -> s.borderOpacity = v },
            formatter = { String.format(Locale.US, "%.0f%%", it) }
        )
    }

    private fun bindSlider(
        containerId: Int,
        title: String,
        subtitle: String,
        min: Float,
        max: Float,
        steps: Int,
        getter: (GlassConfig.GlassSettings) -> Float,
        setter: (GlassConfig.GlassSettings, Float) -> Unit,
        formatter: (Float) -> String,
        onStopTracking: (() -> Unit)? = null
    ) {
        val container = findViewById<View>(containerId) ?: return
        val binding = SliderBinding(container, title, subtitle, min, max, steps, getter, setter, formatter, onStopTracking)
        binding.init(onChanged = { applyToPreview() }, currentSettings)
        sliderBindings.add(binding)
    }

    private fun initSwitches() {
        switchBindings.clear()

        // Black Border Toggle
        bindSwitch(
            containerId = R.id.switch_border_enabled,
            title = "Black Outline Border",
            subtitle = "Thin black border around glass edges",
            getter = { it.borderEnabled },
            setter = { s, v -> s.borderEnabled = v }
        )

        // 1. Specular Edge Highlight
        bindSwitch(
            containerId = R.id.switch_edge_highlight,
            title = "Specular Edge Highlight",
            subtitle = "Signature specular highlight along the rim",
            getter = { it.enableEdgeHighlight },
            setter = { s, v -> s.enableEdgeHighlight = v }
        )

        // 2. Sensor Tilt Highlight (Removed - launcher locked to portrait)
        findViewById<View>(R.id.switch_sensor_highlight)?.visibility = View.GONE

        // 3. Adaptive Backdrop Tint
        bindSwitch(
            containerId = R.id.switch_adaptive_tint,
            title = "Adaptive Backdrop Tint",
            subtitle = "Tint dynamically follows wallpaper luminance",
            getter = { it.enableAdaptiveTint },
            setter = { s, v -> s.enableAdaptiveTint = v }
        )

        // 4. Outward Refraction
        bindSwitch(
            containerId = R.id.switch_refraction_outward,
            title = "Outward Refraction",
            subtitle = "Inverts displacement outwards instead of inwards",
            getter = { it.refractionOutward },
            setter = { s, v -> s.refractionOutward = v }
        )

        // 5. Refraction No-Fold
        bindSwitch(
            containerId = R.id.switch_refraction_no_fold,
            title = "No-Fold Lens Profile",
            subtitle = "Disables rim fold-back compression ring",
            getter = { it.refractionNoFold },
            setter = { s, v -> s.refractionNoFold = v }
        )
    }

    private fun bindSwitch(
        containerId: Int,
        title: String,
        subtitle: String,
        getter: (GlassConfig.GlassSettings) -> Boolean,
        setter: (GlassConfig.GlassSettings, Boolean) -> Unit
    ) {
        val container = findViewById<View>(containerId) ?: return
        val binding = SwitchBinding(container, title, subtitle, getter, setter)
        binding.init(onChanged = { applyToPreview() }, currentSettings)
        switchBindings.add(binding)
    }

    private fun initResetButton() {
        findViewById<View>(R.id.btn_reset_defaults)?.setOnClickListener {
            currentSettings = GlassConfig.GlassSettings()
            sliderBindings.forEach { it.updateFromSettings(currentSettings) }
            switchBindings.forEach { it.updateFromSettings(currentSettings) }
            applyToPreview()
            LiquidGlassToast.show(this, "Reset to default glass parameters", R.drawable.ic_ios_check_circle)
        }
    }

    private fun applyToPreview() {
        previewWallpaper.blurAmount = currentSettings.blurAmount
        previewWallpaper.invalidate()

        GlassConfig.applyConfig(previewDockGlass, currentSettings)
        previewDockGlass.invalidate()

        previewFolderGlass?.let {
            GlassConfig.applyConfig(it, currentSettings)
            it.invalidate()
        }

        previewSearchGlass?.let {
            GlassConfig.applyConfig(it, currentSettings)
            it.invalidate()
        }
    }

    private fun saveAndFinish(fromSaveButton: Boolean = false) {
        if (isSaving) return

        GlassConfig.saveSettings(this, currentSettings)

        val proceedFinish = {
            AdManager.showInterstitialOnExitIfEligible(this) {
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            }
        }

        if (fromSaveButton) {
            isSaving = true
            findViewById<View>(R.id.btn_save)?.isEnabled = false
            LiquidGlassToast.show(this, "Glass settings saved", R.drawable.ic_ios_check_circle)
            saveFinishHandler.postDelayed({
                proceedFinish()
            }, 850L)
        } else {
            proceedFinish()
        }
    }

    // --- Helper Binding Classes ---

    private class SliderBinding(
        val container: View,
        val title: String,
        val subtitle: String,
        val min: Float,
        val max: Float,
        val steps: Int,
        val getter: (GlassConfig.GlassSettings) -> Float,
        val setter: (GlassConfig.GlassSettings, Float) -> Unit,
        val formatter: (Float) -> String,
        val onStopTracking: (() -> Unit)? = null
    ) {
        val slider: IosSlider = container.findViewById(R.id.seekbar)!!
        val txtTitle: TextView = container.findViewById(R.id.txt_title)!!
        val txtSubtitle: TextView = container.findViewById(R.id.txt_subtitle)!!
        val txtValue: TextView = container.findViewById(R.id.txt_value)!!

        fun init(onChanged: () -> Unit, settings: GlassConfig.GlassSettings) {
            txtTitle.text = title
            txtSubtitle.text = subtitle
            slider.min = min
            slider.max = max
            slider.steps = steps
            updateFromSettings(settings)

            slider.onSliderChangeListener = object : IosSlider.OnSliderChangeListener {
                override fun onProgressChanged(s: IosSlider, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        setter(settings, progress)
                        txtValue.text = formatter(progress)
                        onChanged()
                    }
                }

                override fun onStopTrackingTouch(s: IosSlider) {
                    onStopTracking?.invoke()
                }
            }
        }

        fun updateFromSettings(settings: GlassConfig.GlassSettings) {
            val value = getter(settings)
            txtValue.text = formatter(value)
            slider.progress = value
        }
    }

    private class SwitchBinding(
        val container: View,
        val title: String,
        val subtitle: String,
        val getter: (GlassConfig.GlassSettings) -> Boolean,
        val setter: (GlassConfig.GlassSettings, Boolean) -> Unit
    ) {
        val switchToggle: Switch = container.findViewById(R.id.switch_toggle)!!
        val txtTitle: TextView = container.findViewById(R.id.txt_title)!!
        val txtSubtitle: TextView = container.findViewById(R.id.txt_subtitle)!!

        fun init(onChanged: () -> Unit, settings: GlassConfig.GlassSettings) {
            txtTitle.text = title
            txtSubtitle.text = subtitle
            updateFromSettings(settings)

            switchToggle.setOnCheckedChangeListener { _, isChecked ->
                setter(settings, isChecked)
                onChanged()
            }
        }

        fun updateFromSettings(settings: GlassConfig.GlassSettings) {
            switchToggle.setOnCheckedChangeListener(null)
            switchToggle.isChecked = getter(settings)
        }
    }

    companion object {
        private const val TAG = "GlassCustomizeActivity"
    }
}
