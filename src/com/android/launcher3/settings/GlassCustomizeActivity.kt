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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.android.launcher3.R
import foundation.e.bliss.glass.GlassConfig
import foundation.e.bliss.glass.PreviewDockGlassView
import foundation.e.bliss.wallpaper.WallpaperRepository
import java.util.Locale

/**
 * Interactive Liquid Glass effect customization screen with a real-time live dock preview.
 * Sliders adjust optics, blur, dispersion, and specular highlights in real time.
 */
class GlassCustomizeActivity : FragmentActivity() {

    private lateinit var previewDockGlass: PreviewDockGlassView
    private lateinit var previewWallpaper: ImageView
    private var currentSettings: GlassConfig.GlassSettings = GlassConfig.GlassSettings()

    private val sliderBindings = mutableListOf<SliderBinding>()
    private val switchBindings = mutableListOf<SwitchBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Bliss_Onboarding)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glass_customize)

        setupSystemBars()
        currentSettings = GlassConfig.loadSettings(this)

        initHeader()
        initPreview()
        initSliders()
        initSwitches()
        initResetButton()
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
            finish()
        }

        findViewById<View>(R.id.btn_save)?.setOnClickListener {
            saveAndFinish()
        }
    }

    private fun initPreview() {
        previewWallpaper = findViewById(R.id.preview_wallpaper)!!
        previewDockGlass = findViewById(R.id.preview_dock_glass)!!

        loadWallpaperBackdrop()
        setupPreviewIcons()

        // Configure live dock glass
        previewWallpaper.post {
            previewDockGlass.backdropSource = previewWallpaper
            previewDockGlass.enableDynamicBackground = true
            previewDockGlass.useShaderPipeline = true
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
        findViewById<ImageView>(R.id.icon_phone)?.setImageDrawable(
            resolveIcon(Intent(Intent.ACTION_DIAL), R.drawable.ic_preview_phone)
        )

        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:")
        }
        findViewById<ImageView>(R.id.icon_messages)?.setImageDrawable(
            resolveIcon(smsIntent, R.drawable.ic_preview_messages)
        )

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
        findViewById<ImageView>(R.id.icon_browser)?.setImageDrawable(
            resolveIcon(webIntent, R.drawable.ic_preview_browser)
        )

        val camIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        findViewById<ImageView>(R.id.icon_camera)?.setImageDrawable(
            resolveIcon(camIntent, R.drawable.ic_preview_camera)
        )
    }

    private fun resolveIcon(intent: Intent, fallbackRes: Int): Drawable {
        return try {
            val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: packageManager.resolveActivity(intent, 0)
            info?.loadIcon(packageManager) ?: ContextCompat.getDrawable(this, fallbackRes)!!
        } catch (e: Exception) {
            ContextCompat.getDrawable(this, fallbackRes) ?: ColorDrawable(Color.GRAY)
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
            max = 300f,
            steps = 300,
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
            max = 120f,
            steps = 118,
            getter = { it.bevelWidth },
            setter = { s, v -> s.bevelWidth = v },
            formatter = { String.format(Locale.US, "%.1f px", it) }
        )

        // 3. Refraction Falloff
        bindSlider(
            containerId = R.id.slider_refraction_falloff,
            title = "Refraction Falloff",
            subtitle = "Inverse-power curve steepness (default ${GlassConfig.DEFAULT_REFRACTION_FALLOFF})",
            min = 0.5f,
            max = 5.0f,
            steps = 450,
            getter = { it.refractionFalloff },
            setter = { s, v -> s.refractionFalloff = v },
            formatter = { String.format(Locale.US, "%.2f", it) }
        )

        // 4. Displacement Scale
        bindSlider(
            containerId = R.id.slider_displacement_scale,
            title = "Displacement Scale",
            subtitle = "Overall displacement factor (default ${GlassConfig.DEFAULT_DISPLACEMENT_SCALE.toInt()})",
            min = 0f,
            max = 200f,
            steps = 200,
            getter = { it.displacementScale },
            setter = { s, v -> s.displacementScale = v },
            formatter = { String.format(Locale.US, "%.1f", it) }
        )

        // 5. Edge Softness
        bindSlider(
            containerId = R.id.slider_edge_softness,
            title = "Edge Softness",
            subtitle = "Outer border softness (default ${GlassConfig.DEFAULT_EDGE_SOFTNESS.toInt()} px)",
            min = 0f,
            max = 30f,
            steps = 30,
            getter = { it.edgeSoftness },
            setter = { s, v -> s.edgeSoftness = v },
            formatter = { String.format(Locale.US, "%.1f px", it) }
        )

        // 6. Blur Amount
        bindSlider(
            containerId = R.id.slider_blur_amount,
            title = "Blur Amount",
            subtitle = "Background frosted blur (default 0.063)",
            min = 0.0f,
            max = 0.25f,
            steps = 500,
            getter = { it.blurAmount },
            setter = { s, v -> s.blurAmount = v },
            formatter = { String.format(Locale.US, "%.3f", it) }
        )

        // 7. Saturation Boost
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

        // 8. Chromatic Dispersion
        bindSlider(
            containerId = R.id.slider_dispersion_strength,
            title = "Chromatic Dispersion",
            subtitle = "Spectral rainbow fringe at rim (default ${GlassConfig.DEFAULT_DISPERSION_STRENGTH})",
            min = 0.0f,
            max = 0.40f,
            steps = 400,
            getter = { it.dispersionStrength },
            setter = { s, v -> s.dispersionStrength = v },
            formatter = { String.format(Locale.US, "%.2f", it) }
        )

        // 9. Aberration Intensity
        bindSlider(
            containerId = R.id.slider_aberration_intensity,
            title = "Aberration Intensity",
            subtitle = "RGB split across surface (default ${GlassConfig.DEFAULT_ABERRATION_INTENSITY})",
            min = 0.0f,
            max = 8.0f,
            steps = 80,
            getter = { it.aberrationIntensity },
            setter = { s, v -> s.aberrationIntensity = v },
            formatter = { String.format(Locale.US, "%.1f", it) }
        )

        // 10. Edge Highlight Width
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

        // 11. Edge Highlight Opacity
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

        // 12. Elasticity
        bindSlider(
            containerId = R.id.slider_elasticity,
            title = "Physics Elasticity",
            subtitle = "Glass elasticity response factor (default ${GlassConfig.DEFAULT_ELASTICITY})",
            min = 0.0f,
            max = 0.50f,
            steps = 50,
            getter = { it.elasticity },
            setter = { s, v -> s.elasticity = v },
            formatter = { String.format(Locale.US, "%.2f", it) }
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
        formatter: (Float) -> String
    ) {
        val container = findViewById<View>(containerId) ?: return
        val binding = SliderBinding(container, title, subtitle, min, max, steps, getter, setter, formatter)
        binding.init(onChanged = { applyToPreview() }, currentSettings)
        sliderBindings.add(binding)
    }

    private fun initSwitches() {
        switchBindings.clear()

        // 1. Specular Edge Highlight
        bindSwitch(
            containerId = R.id.switch_edge_highlight,
            title = "Specular Edge Highlight",
            subtitle = "Signature iOS specular highlight along the rim",
            getter = { it.enableEdgeHighlight },
            setter = { s, v -> s.enableEdgeHighlight = v }
        )

        // 2. Sensor Tilt Highlight
        bindSwitch(
            containerId = R.id.switch_sensor_highlight,
            title = "Dynamic Sensor Highlight",
            subtitle = "Tracks device tilt and angle via gravity sensor",
            getter = { it.enableSensorHighlight },
            setter = { s, v -> s.enableSensorHighlight = v }
        )

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
            Toast.makeText(this, "Reset to default liquid glass parameters", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyToPreview() {
        GlassConfig.applyConfig(previewDockGlass, currentSettings)
        previewDockGlass.invalidate()
    }

    private fun saveAndFinish() {
        GlassConfig.saveSettings(this, currentSettings)
        Toast.makeText(this, "Liquid Glass settings saved", Toast.LENGTH_SHORT).show()
        finish()
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
        val formatter: (Float) -> String
    ) {
        val seekbar: SeekBar = container.findViewById(R.id.seekbar)!!
        val txtTitle: TextView = container.findViewById(R.id.txt_title)!!
        val txtSubtitle: TextView = container.findViewById(R.id.txt_subtitle)!!
        val txtValue: TextView = container.findViewById(R.id.txt_value)!!

        fun init(onChanged: () -> Unit, settings: GlassConfig.GlassSettings) {
            txtTitle.text = title
            txtSubtitle.text = subtitle
            seekbar.max = steps
            updateFromSettings(settings)

            seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val value = min + (progress.toFloat() / steps.toFloat()) * (max - min)
                        setter(settings, value)
                        txtValue.text = formatter(value)
                        onChanged()
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        fun updateFromSettings(settings: GlassConfig.GlassSettings) {
            val value = getter(settings)
            txtValue.text = formatter(value)
            val progress = (((value - min) / (max - min)) * steps).toInt().coerceIn(0, steps)
            seekbar.progress = progress
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
