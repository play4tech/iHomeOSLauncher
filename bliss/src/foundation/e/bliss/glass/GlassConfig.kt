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
package foundation.e.bliss.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.android.launcher3.LauncherPrefs
import com.example.liquidglass.GlassAccessibilityMode
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * Master configuration object defining single source of truth for all Liquid Glass surfaces across
 * the launcher (Dock, Closed Folder, Opened Folder, App Library, Search Bar).
 *
 * Configured with the official default liquid glass values from QWEA0/Liquid-Glass-Android (iOS 26 Liquid Glass),
 * with support for live parameter customization and persistence.
 */
object GlassConfig {
    // --- Default Parameter Constants (User fine-tuned liquid glass defaults) ---
    const val DEFAULT_REFRACTION_HEIGHT: Float = 143f
    const val DEFAULT_BEVEL_WIDTH: Float = 65f
    const val DEFAULT_REFRACTION_FALLOFF: Float = 2.94f
    const val DEFAULT_DISPERSION_STRENGTH: Float = 0.0f
    const val DEFAULT_BLUR_AMOUNT: Float = 0.25f
    const val DEFAULT_SATURATION: Float = 120f
    const val DEFAULT_ABERRATION_INTENSITY: Float = 2f
    const val DEFAULT_DISPLACEMENT_SCALE: Float = 43f
    const val DEFAULT_ELASTICITY: Float = 0.15f
    const val DEFAULT_EDGE_HIGHLIGHT_BORDER_WIDTH: Float = 0.35f
    const val DEFAULT_EDGE_HIGHLIGHT_OPACITY: Float = 55f
    const val DEFAULT_EDGE_SOFTNESS: Float = 2f
    const val DEFAULT_ENABLE_EDGE_HIGHLIGHT: Boolean = true
    const val DEFAULT_ENABLE_SENSOR_HIGHLIGHT: Boolean = false
    const val DEFAULT_ENABLE_ADAPTIVE_TINT: Boolean = false
    const val DEFAULT_REFRACTION_NO_FOLD: Boolean = false
    const val DEFAULT_REFRACTION_OUTWARD: Boolean = false
    const val POSITION_SIDES: Int = 0
    const val POSITION_TOP: Int = 1
    const val POSITION_BOTTOM: Int = 2
    const val POSITION_ALL: Int = 3
    const val POSITION_TOP_BOTTOM: Int = 4

    const val DEFAULT_BORDER_ENABLED: Boolean = true
    const val DEFAULT_BORDER_WIDTH: Float = 0.25f        // in dp (ultra-thin hairline)
    const val DEFAULT_BORDER_OPACITY: Float = 55f        // 55% peak opacity (subtle)
    const val DEFAULT_BORDER_FADE: Float = 100f          // 100% fade at ends
    const val DEFAULT_BORDER_POSITION: Int = POSITION_SIDES
    const val DEFAULT_BORDER_FADE_SPREAD: Float = 35f    // 35% fade transition distance

    const val DEFAULT_WHITE_BORDER_ENABLED: Boolean = false
    const val DEFAULT_WHITE_BORDER_WIDTH: Float = 0.25f
    const val DEFAULT_WHITE_BORDER_OPACITY: Float = 80f
    const val DEFAULT_WHITE_BORDER_FADE: Float = 100f
    const val DEFAULT_WHITE_BORDER_POSITION: Int = POSITION_SIDES
    const val DEFAULT_WHITE_BORDER_FADE_SPREAD: Float = 35f

    // Subtle milky white translucent glass body tint (25% alpha), matching Apple iOS 26 and liquid_glass_easy
    const val DEFAULT_GLASS_TINT: Int = 0x40FFFFFF.toInt()
    const val CURRENT_CONFIG_VERSION: Int = 14

    // Backward-compatibility aliases
    const val DEFAULT_DISPERSION: Float = DEFAULT_DISPERSION_STRENGTH

    // --- SharedPreferences Keys ---
    const val PREF_KEY_CONFIG_VERSION = "pref_glass_config_version"
    const val PREF_KEY_GLASS_TINT = "pref_glass_tint"
    const val PREF_KEY_REFRACTION_HEIGHT = "pref_glass_refraction_height"
    const val PREF_KEY_BEVEL_WIDTH = "pref_glass_bevel_width"
    const val PREF_KEY_REFRACTION_FALLOFF = "pref_glass_refraction_falloff"
    const val PREF_KEY_DISPERSION_STRENGTH = "pref_glass_dispersion_strength"
    const val PREF_KEY_BLUR_AMOUNT = "pref_glass_blur_amount"
    const val PREF_KEY_SATURATION = "pref_glass_saturation"
    const val PREF_KEY_ABERRATION_INTENSITY = "pref_glass_aberration_intensity"
    const val PREF_KEY_DISPLACEMENT_SCALE = "pref_glass_displacement_scale"
    const val PREF_KEY_ELASTICITY = "pref_glass_elasticity"
    const val PREF_KEY_EDGE_HIGHLIGHT_BORDER_WIDTH = "pref_glass_edge_highlight_border_width"
    const val PREF_KEY_EDGE_HIGHLIGHT_OPACITY = "pref_glass_edge_highlight_opacity"
    const val PREF_KEY_EDGE_SOFTNESS = "pref_glass_edge_softness"
    const val PREF_KEY_ENABLE_EDGE_HIGHLIGHT = "pref_glass_enable_edge_highlight"
    const val PREF_KEY_ENABLE_SENSOR_HIGHLIGHT = "pref_glass_enable_sensor_highlight"
    const val PREF_KEY_ENABLE_ADAPTIVE_TINT = "pref_glass_enable_adaptive_tint"
    const val PREF_KEY_REFRACTION_NO_FOLD = "pref_glass_refraction_no_fold"
    const val PREF_KEY_REFRACTION_OUTWARD = "pref_glass_refraction_outward"

    const val PREF_KEY_BORDER_ENABLED = "pref_glass_border_enabled"
    const val PREF_KEY_BORDER_WIDTH = "pref_glass_border_width"
    const val PREF_KEY_BORDER_OPACITY = "pref_glass_border_opacity"
    const val PREF_KEY_BORDER_FADE = "pref_glass_border_fade"
    const val PREF_KEY_BORDER_POSITION = "pref_glass_border_position"
    const val PREF_KEY_BORDER_FADE_SPREAD = "pref_glass_border_fade_spread"

    const val PREF_KEY_WHITE_BORDER_ENABLED = "pref_glass_white_border_enabled"
    const val PREF_KEY_WHITE_BORDER_WIDTH = "pref_glass_white_border_width"
    const val PREF_KEY_WHITE_BORDER_OPACITY = "pref_glass_white_border_opacity"
    const val PREF_KEY_WHITE_BORDER_FADE = "pref_glass_white_border_fade"
    const val PREF_KEY_WHITE_BORDER_POSITION = "pref_glass_white_border_position"
    const val PREF_KEY_WHITE_BORDER_FADE_SPREAD = "pref_glass_white_border_fade_spread"

    /** Data holder for all Liquid Glass optical parameters. */
    data class GlassSettings(
        var refractionHeight: Float = DEFAULT_REFRACTION_HEIGHT,
        var bevelWidth: Float = DEFAULT_BEVEL_WIDTH,
        var refractionFalloff: Float = DEFAULT_REFRACTION_FALLOFF,
        var dispersionStrength: Float = DEFAULT_DISPERSION_STRENGTH,
        var blurAmount: Float = DEFAULT_BLUR_AMOUNT,
        var saturation: Float = DEFAULT_SATURATION,
        var aberrationIntensity: Float = DEFAULT_ABERRATION_INTENSITY,
        var displacementScale: Float = DEFAULT_DISPLACEMENT_SCALE,
        var elasticity: Float = DEFAULT_ELASTICITY,
        var edgeHighlightBorderWidth: Float = DEFAULT_EDGE_HIGHLIGHT_BORDER_WIDTH,
        var edgeHighlightOpacity: Float = DEFAULT_EDGE_HIGHLIGHT_OPACITY,
        var edgeSoftness: Float = DEFAULT_EDGE_SOFTNESS,
        var enableEdgeHighlight: Boolean = DEFAULT_ENABLE_EDGE_HIGHLIGHT,
        var enableSensorHighlight: Boolean = DEFAULT_ENABLE_SENSOR_HIGHLIGHT,
        var enableAdaptiveTint: Boolean = DEFAULT_ENABLE_ADAPTIVE_TINT,
        var refractionNoFold: Boolean = DEFAULT_REFRACTION_NO_FOLD,
        var refractionOutward: Boolean = DEFAULT_REFRACTION_OUTWARD,
        var glassTint: Int = DEFAULT_GLASS_TINT,
        var borderEnabled: Boolean = DEFAULT_BORDER_ENABLED,
        var borderWidth: Float = DEFAULT_BORDER_WIDTH,
        var borderOpacity: Float = DEFAULT_BORDER_OPACITY,
        var borderFade: Float = DEFAULT_BORDER_FADE,
        var borderPosition: Int = DEFAULT_BORDER_POSITION,
        var borderFadeSpread: Float = DEFAULT_BORDER_FADE_SPREAD,

        var whiteBorderEnabled: Boolean = DEFAULT_WHITE_BORDER_ENABLED,
        var whiteBorderWidth: Float = DEFAULT_WHITE_BORDER_WIDTH,
        var whiteBorderOpacity: Float = DEFAULT_WHITE_BORDER_OPACITY,
        var whiteBorderFade: Float = DEFAULT_WHITE_BORDER_FADE,
        var whiteBorderPosition: Int = DEFAULT_WHITE_BORDER_POSITION,
        var whiteBorderFadeSpread: Float = DEFAULT_WHITE_BORDER_FADE_SPREAD,
    )

    @Volatile
    private var cachedSettings: GlassSettings? = null

    private val registeredViews = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<LiquidGlassView, Boolean>()
    )

    private var prefsListenerRegistered = false
    private var prefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    private fun ensurePrefsListener(context: Context) {
        if (!prefsListenerRegistered) {
            try {
                val prefs = LauncherPrefs.getPrefs(context.applicationContext)
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key != null && key.startsWith("pref_glass_")) {
                        cachedSettings = null
                        val newSettings = loadSettings(context)
                        notifyAllViews(context, newSettings)
                    }
                }
                prefsListener = listener
                prefs.registerOnSharedPreferenceChangeListener(listener)
                prefsListenerRegistered = true
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @JvmStatic
    fun registerView(view: LiquidGlassView) {
        synchronized(registeredViews) {
            registeredViews.add(view)
        }
        val settings = loadSettings(view.context)
        applyConfig(view, settings)
    }

    @JvmStatic
    fun unregisterView(view: LiquidGlassView) {
        synchronized(registeredViews) {
            registeredViews.remove(view)
        }
    }

    private var sensorsPermanentlyDisabled = false
    private var dummyListenerView: android.view.View? = null

    @JvmStatic
    fun disableSensors(context: Context) {
        try {
            val cls = Class.forName("com.example.liquidglass.LightSourceController")
            val lightDirXField = cls.getDeclaredField("lightDirX").apply { isAccessible = true }
            val lightDirYField = cls.getDeclaredField("lightDirY").apply { isAccessible = true }
            val lastNotifiedXField = cls.getDeclaredField("lastNotifiedX").apply { isAccessible = true }
            val lastNotifiedYField = cls.getDeclaredField("lastNotifiedY").apply { isAccessible = true }
            val registeredField = cls.getDeclaredField("registered").apply { isAccessible = true }
            val listenersField = cls.getDeclaredField("listeners").apply { isAccessible = true }

            // Lock specular light direction permanently to top/bottom: X = 0.0 (no sides), Y = 1.0 (top and bottom specular)
            lightDirXField.setFloat(null, 0.0f)
            lightDirYField.setFloat(null, 1.0f)
            lastNotifiedXField.setFloat(null, 0.0f)
            lastNotifiedYField.setFloat(null, 1.0f)

            // Mark registered = true so LightSourceController.register() never registers a listener
            registeredField.setBoolean(null, true)

            if (!sensorsPermanentlyDisabled) {
                val instanceField = cls.getDeclaredField("INSTANCE").apply { isAccessible = true }
                val inst = instanceField.get(null) as? android.hardware.SensorEventListener
                val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
                if (inst != null && sm != null) {
                    sm.unregisterListener(inst)
                }
                sensorsPermanentlyDisabled = true
                android.util.Log.d("GlassConfig", "disableSensors: Locked specular lightDir to (0.0, 1.0)")
            }

            // Keep a dummy view in listeners permanently so listeners.isEmpty() is NEVER true,
            // preventing unregister() from resetting registered to false.
            @Suppress("UNCHECKED_CAST")
            val listeners = listenersField.get(null) as? java.util.LinkedHashSet<android.view.View>
            if (listeners != null) {
                synchronized(listeners) {
                    if (dummyListenerView == null) {
                        dummyListenerView = android.view.View(context.applicationContext)
                    }
                    val dummy = dummyListenerView
                    if (dummy != null && !listeners.contains(dummy)) {
                        listeners.add(dummy)
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("GlassConfig", "disableSensors failed", e)
        }
    }

    @JvmStatic
    fun notifyAllViews(context: Context, settings: GlassSettings) {
        val viewsCopy = synchronized(registeredViews) {
            registeredViews.toList()
        }
        for (v in viewsCopy) {
            try {
                applyConfig(v, settings)
                v.invalidate()
            } catch (e: Exception) {
                // ignore recycled or detached views
            }
        }
    }

    @JvmStatic
    fun loadSettings(context: Context): GlassSettings {
        disableSensors(context)
        ensurePrefsListener(context)
        cachedSettings?.let { return it.copy() }
        val prefs = LauncherPrefs.getPrefs(context)
        val configVersion = prefs.getInt(PREF_KEY_CONFIG_VERSION, 0)
        val isMigrated = configVersion >= CURRENT_CONFIG_VERSION

        if (!isMigrated) {
            prefs.edit()
                .putInt(PREF_KEY_CONFIG_VERSION, CURRENT_CONFIG_VERSION)
                .putInt(PREF_KEY_GLASS_TINT, DEFAULT_GLASS_TINT)
                .putFloat(PREF_KEY_REFRACTION_HEIGHT, DEFAULT_REFRACTION_HEIGHT)
                .putFloat(PREF_KEY_BEVEL_WIDTH, DEFAULT_BEVEL_WIDTH)
                .putFloat(PREF_KEY_REFRACTION_FALLOFF, DEFAULT_REFRACTION_FALLOFF)
                .putFloat(PREF_KEY_DISPERSION_STRENGTH, DEFAULT_DISPERSION_STRENGTH)
                .putFloat(PREF_KEY_BLUR_AMOUNT, DEFAULT_BLUR_AMOUNT)
                .putFloat(PREF_KEY_SATURATION, DEFAULT_SATURATION)
                .putFloat(PREF_KEY_ABERRATION_INTENSITY, DEFAULT_ABERRATION_INTENSITY)
                .putFloat(PREF_KEY_DISPLACEMENT_SCALE, DEFAULT_DISPLACEMENT_SCALE)
                .putFloat(PREF_KEY_ELASTICITY, DEFAULT_ELASTICITY)
                .putFloat(PREF_KEY_EDGE_HIGHLIGHT_BORDER_WIDTH, DEFAULT_EDGE_HIGHLIGHT_BORDER_WIDTH)
                .putFloat(PREF_KEY_EDGE_HIGHLIGHT_OPACITY, DEFAULT_EDGE_HIGHLIGHT_OPACITY)
                .putFloat(PREF_KEY_EDGE_SOFTNESS, DEFAULT_EDGE_SOFTNESS)
                .putBoolean(PREF_KEY_ENABLE_EDGE_HIGHLIGHT, DEFAULT_ENABLE_EDGE_HIGHLIGHT)
                .putBoolean(PREF_KEY_ENABLE_SENSOR_HIGHLIGHT, DEFAULT_ENABLE_SENSOR_HIGHLIGHT)
                .putBoolean(PREF_KEY_ENABLE_ADAPTIVE_TINT, DEFAULT_ENABLE_ADAPTIVE_TINT)
                .putBoolean(PREF_KEY_REFRACTION_NO_FOLD, DEFAULT_REFRACTION_NO_FOLD)
                .putBoolean(PREF_KEY_REFRACTION_OUTWARD, DEFAULT_REFRACTION_OUTWARD)
                .putBoolean(PREF_KEY_BORDER_ENABLED, DEFAULT_BORDER_ENABLED)
                .putFloat(PREF_KEY_BORDER_WIDTH, DEFAULT_BORDER_WIDTH)
                .putFloat(PREF_KEY_BORDER_OPACITY, DEFAULT_BORDER_OPACITY)
                .putFloat(PREF_KEY_BORDER_FADE, DEFAULT_BORDER_FADE)
                .putInt(PREF_KEY_BORDER_POSITION, DEFAULT_BORDER_POSITION)
                .putFloat(PREF_KEY_BORDER_FADE_SPREAD, DEFAULT_BORDER_FADE_SPREAD)
                .putBoolean(PREF_KEY_WHITE_BORDER_ENABLED, DEFAULT_WHITE_BORDER_ENABLED)
                .putFloat(PREF_KEY_WHITE_BORDER_WIDTH, DEFAULT_WHITE_BORDER_WIDTH)
                .putFloat(PREF_KEY_WHITE_BORDER_OPACITY, DEFAULT_WHITE_BORDER_OPACITY)
                .putFloat(PREF_KEY_WHITE_BORDER_FADE, DEFAULT_WHITE_BORDER_FADE)
                .putInt(PREF_KEY_WHITE_BORDER_POSITION, DEFAULT_WHITE_BORDER_POSITION)
                .putFloat(PREF_KEY_WHITE_BORDER_FADE_SPREAD, DEFAULT_WHITE_BORDER_FADE_SPREAD)
                .apply()
        }

        val settings = GlassSettings(
            refractionHeight = prefs.getFloat(PREF_KEY_REFRACTION_HEIGHT, DEFAULT_REFRACTION_HEIGHT),
            bevelWidth = prefs.getFloat(PREF_KEY_BEVEL_WIDTH, DEFAULT_BEVEL_WIDTH),
            refractionFalloff = prefs.getFloat(PREF_KEY_REFRACTION_FALLOFF, DEFAULT_REFRACTION_FALLOFF),
            dispersionStrength = prefs.getFloat(PREF_KEY_DISPERSION_STRENGTH, DEFAULT_DISPERSION_STRENGTH),
            blurAmount = prefs.getFloat(PREF_KEY_BLUR_AMOUNT, DEFAULT_BLUR_AMOUNT),
            saturation = prefs.getFloat(PREF_KEY_SATURATION, DEFAULT_SATURATION),
            aberrationIntensity = prefs.getFloat(PREF_KEY_ABERRATION_INTENSITY, DEFAULT_ABERRATION_INTENSITY),
            displacementScale = prefs.getFloat(PREF_KEY_DISPLACEMENT_SCALE, DEFAULT_DISPLACEMENT_SCALE),
            elasticity = prefs.getFloat(PREF_KEY_ELASTICITY, DEFAULT_ELASTICITY),
            edgeHighlightBorderWidth = prefs.getFloat(PREF_KEY_EDGE_HIGHLIGHT_BORDER_WIDTH, DEFAULT_EDGE_HIGHLIGHT_BORDER_WIDTH),
            edgeHighlightOpacity = prefs.getFloat(PREF_KEY_EDGE_HIGHLIGHT_OPACITY, DEFAULT_EDGE_HIGHLIGHT_OPACITY),
            edgeSoftness = prefs.getFloat(PREF_KEY_EDGE_SOFTNESS, DEFAULT_EDGE_SOFTNESS),
            enableEdgeHighlight = prefs.getBoolean(PREF_KEY_ENABLE_EDGE_HIGHLIGHT, DEFAULT_ENABLE_EDGE_HIGHLIGHT),
            enableSensorHighlight = prefs.getBoolean(PREF_KEY_ENABLE_SENSOR_HIGHLIGHT, DEFAULT_ENABLE_SENSOR_HIGHLIGHT),
            enableAdaptiveTint = prefs.getBoolean(PREF_KEY_ENABLE_ADAPTIVE_TINT, DEFAULT_ENABLE_ADAPTIVE_TINT),
            refractionNoFold = prefs.getBoolean(PREF_KEY_REFRACTION_NO_FOLD, DEFAULT_REFRACTION_NO_FOLD),
            refractionOutward = prefs.getBoolean(PREF_KEY_REFRACTION_OUTWARD, DEFAULT_REFRACTION_OUTWARD),
            glassTint = prefs.getInt(PREF_KEY_GLASS_TINT, DEFAULT_GLASS_TINT),
            borderEnabled = prefs.getBoolean(PREF_KEY_BORDER_ENABLED, DEFAULT_BORDER_ENABLED),
            borderWidth = prefs.getFloat(PREF_KEY_BORDER_WIDTH, DEFAULT_BORDER_WIDTH),
            borderOpacity = prefs.getFloat(PREF_KEY_BORDER_OPACITY, DEFAULT_BORDER_OPACITY),
            borderFade = prefs.getFloat(PREF_KEY_BORDER_FADE, DEFAULT_BORDER_FADE),
            borderPosition = prefs.getInt(PREF_KEY_BORDER_POSITION, DEFAULT_BORDER_POSITION),
            borderFadeSpread = prefs.getFloat(PREF_KEY_BORDER_FADE_SPREAD, DEFAULT_BORDER_FADE_SPREAD),

            whiteBorderEnabled = prefs.getBoolean(PREF_KEY_WHITE_BORDER_ENABLED, DEFAULT_WHITE_BORDER_ENABLED),
            whiteBorderWidth = prefs.getFloat(PREF_KEY_WHITE_BORDER_WIDTH, DEFAULT_WHITE_BORDER_WIDTH),
            whiteBorderOpacity = prefs.getFloat(PREF_KEY_WHITE_BORDER_OPACITY, DEFAULT_WHITE_BORDER_OPACITY),
            whiteBorderFade = prefs.getFloat(PREF_KEY_WHITE_BORDER_FADE, DEFAULT_WHITE_BORDER_FADE),
            whiteBorderPosition = prefs.getInt(PREF_KEY_WHITE_BORDER_POSITION, DEFAULT_WHITE_BORDER_POSITION),
            whiteBorderFadeSpread = prefs.getFloat(PREF_KEY_WHITE_BORDER_FADE_SPREAD, DEFAULT_WHITE_BORDER_FADE_SPREAD)
        )
        cachedSettings = settings
        return settings.copy()
    }

    @JvmStatic
    fun saveSettings(context: Context, settings: GlassSettings) {
        cachedSettings = settings.copy()
        val prefs = LauncherPrefs.getPrefs(context)
        prefs.edit()
            .putInt(PREF_KEY_CONFIG_VERSION, CURRENT_CONFIG_VERSION)
            .putInt(PREF_KEY_GLASS_TINT, settings.glassTint)
            .putFloat(PREF_KEY_REFRACTION_HEIGHT, settings.refractionHeight)
            .putFloat(PREF_KEY_BEVEL_WIDTH, settings.bevelWidth)
            .putFloat(PREF_KEY_REFRACTION_FALLOFF, settings.refractionFalloff)
            .putFloat(PREF_KEY_DISPERSION_STRENGTH, settings.dispersionStrength)
            .putFloat(PREF_KEY_BLUR_AMOUNT, settings.blurAmount)
            .putFloat(PREF_KEY_SATURATION, settings.saturation)
            .putFloat(PREF_KEY_ABERRATION_INTENSITY, settings.aberrationIntensity)
            .putFloat(PREF_KEY_DISPLACEMENT_SCALE, settings.displacementScale)
            .putFloat(PREF_KEY_ELASTICITY, settings.elasticity)
            .putFloat(PREF_KEY_EDGE_HIGHLIGHT_BORDER_WIDTH, settings.edgeHighlightBorderWidth)
            .putFloat(PREF_KEY_EDGE_HIGHLIGHT_OPACITY, settings.edgeHighlightOpacity)
            .putFloat(PREF_KEY_EDGE_SOFTNESS, settings.edgeSoftness)
            .putBoolean(PREF_KEY_ENABLE_EDGE_HIGHLIGHT, settings.enableEdgeHighlight)
            .putBoolean(PREF_KEY_ENABLE_SENSOR_HIGHLIGHT, settings.enableSensorHighlight)
            .putBoolean(PREF_KEY_ENABLE_ADAPTIVE_TINT, settings.enableAdaptiveTint)
            .putBoolean(PREF_KEY_REFRACTION_NO_FOLD, settings.refractionNoFold)
            .putBoolean(PREF_KEY_REFRACTION_OUTWARD, settings.refractionOutward)
            .putBoolean(PREF_KEY_BORDER_ENABLED, settings.borderEnabled)
            .putFloat(PREF_KEY_BORDER_WIDTH, settings.borderWidth)
            .putFloat(PREF_KEY_BORDER_OPACITY, settings.borderOpacity)
            .putFloat(PREF_KEY_BORDER_FADE, settings.borderFade)
            .putInt(PREF_KEY_BORDER_POSITION, settings.borderPosition)
            .putFloat(PREF_KEY_BORDER_FADE_SPREAD, settings.borderFadeSpread)
            .putBoolean(PREF_KEY_WHITE_BORDER_ENABLED, settings.whiteBorderEnabled)
            .putFloat(PREF_KEY_WHITE_BORDER_WIDTH, settings.whiteBorderWidth)
            .putFloat(PREF_KEY_WHITE_BORDER_OPACITY, settings.whiteBorderOpacity)
            .putFloat(PREF_KEY_WHITE_BORDER_FADE, settings.whiteBorderFade)
            .putInt(PREF_KEY_WHITE_BORDER_POSITION, settings.whiteBorderPosition)
            .putFloat(PREF_KEY_WHITE_BORDER_FADE_SPREAD, settings.whiteBorderFadeSpread)
            .apply()
        notifyAllViews(context, settings)
        try {
            foundation.e.bliss.wallpaper.WallpaperBlurRenderer.INSTANCE.get(context).regenerateBlur()
        } catch (_: Throwable) {}
    }

    @JvmStatic
    fun resetToDefaults(context: Context): GlassSettings {
        val defaults = GlassSettings()
        saveSettings(context, defaults)
        return defaults
    }

    /**
     * Applies custom [settings] to the given [glassView].
     */
    @JvmStatic
    fun applyConfig(glassView: LiquidGlassView, settings: GlassSettings) {
        // --- Liquid Glass 2.0 lens pipeline (API 33+, silently no-ops below) ---
        val isAppLibSearch = glassView.javaClass.simpleName == "AppLibrarySearchGlassView"
        glassView.material = GlassMaterial.REGULAR      // REGULAR (readability) | CLEAR (over media)
        if (!isAppLibSearch) {
            glassView.refractionHeight = settings.refractionHeight
            glassView.bevelWidth = settings.bevelWidth
        }
        glassView.refractionFalloff = settings.refractionFalloff
        glassView.dispersionStrength = settings.dispersionStrength
        glassView.enableEdgeHighlight = settings.enableEdgeHighlight
        glassView.edgeHighlightBorderWidth = settings.edgeHighlightBorderWidth
        glassView.edgeHighlightOpacity = settings.edgeHighlightOpacity
        glassView.enableSensorHighlight = true
        glassView.enableAdaptiveTint = settings.enableAdaptiveTint
        try {
            val powerSaveField = LiquidGlassView::class.java.getDeclaredField("a11yPowerSave").apply { isAccessible = true }
            powerSaveField.setBoolean(glassView, false)
        } catch (_: Throwable) {}

        // --- Optical blur, saturation, aberration & physics defaults ---
        if (glassView is PopupGlassView) {
            glassView.blurAmount = PopupGlassView.POPUP_BLUR_AMOUNT
        } else {
            glassView.blurAmount = settings.blurAmount
        }
        glassView.saturation = settings.saturation
        glassView.aberrationIntensity = settings.aberrationIntensity
        glassView.displacementScale = settings.displacementScale
        glassView.elasticity = settings.elasticity
        glassView.glassTint = settings.glassTint
        glassView.useShaderPipeline = true
        if (!isAppLibSearch) {
            glassView.adaptiveLensScale = true
        }
        glassView.refractionNoFold = settings.refractionNoFold
        glassView.refractionOutward = settings.refractionOutward
        glassView.edgeSoftness = settings.edgeSoftness

        glassView.enablePressEffect = false
        glassView.enableDynamicBackground = false
        glassView.collectFrameStats = false
        glassView.accessibilityMode = GlassAccessibilityMode.AUTO

        if (glassView is BorderedGlassView) {
            glassView.borderEnabled = settings.borderEnabled
            glassView.borderWidthPx = settings.borderWidth * glassView.resources.displayMetrics.density
            glassView.borderOpacity = settings.borderOpacity
            glassView.borderFade = settings.borderFade
            glassView.borderPosition = settings.borderPosition
            glassView.borderFadeSpread = settings.borderFadeSpread

            glassView.whiteBorderEnabled = settings.whiteBorderEnabled
            glassView.whiteBorderWidthPx = settings.whiteBorderWidth * glassView.resources.displayMetrics.density
            glassView.whiteBorderOpacity = settings.whiteBorderOpacity
            glassView.whiteBorderFade = settings.whiteBorderFade
            glassView.whiteBorderPosition = settings.whiteBorderPosition
            glassView.whiteBorderFadeSpread = settings.whiteBorderFadeSpread
        }
        glassView.invalidate()
    }

    private val shaderCache = android.util.LongSparseArray<Shader>(16)

    private fun createBorderShader(
        borderPosition: Int,
        isWhite: Boolean,
        peakAlpha: Int,
        edgeAlpha: Int,
        spreadRatio: Float,
        viewHeight: Float
    ): Shader? {
        val r = if (isWhite) 255 else 0
        val g = if (isWhite) 255 else 0
        val b = if (isWhite) 255 else 0

        when (borderPosition) {
            POSITION_ALL -> return null
            POSITION_TOP -> {
                if (edgeAlpha >= peakAlpha) return null
                val solidStop = (spreadRatio * 0.5f).coerceIn(0f, 0.98f)
                val fadeStop = spreadRatio.coerceIn(solidStop + 0.01f, 1.0f)
                val colors = intArrayOf(
                    Color.argb(peakAlpha, r, g, b),
                    Color.argb(peakAlpha, r, g, b),
                    Color.argb(edgeAlpha, r, g, b),
                    Color.argb(edgeAlpha, r, g, b)
                )
                val positions = floatArrayOf(0f, solidStop, fadeStop, 1.0f)
                return LinearGradient(
                    0f, 0f, 0f, viewHeight,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }
            POSITION_BOTTOM -> {
                if (edgeAlpha >= peakAlpha) return null
                val fadeStart = (1.0f - spreadRatio).coerceIn(0f, 0.98f)
                val solidStart = (1.0f - spreadRatio * 0.5f).coerceIn(fadeStart + 0.01f, 1.0f)
                val colors = intArrayOf(
                    Color.argb(edgeAlpha, r, g, b),
                    Color.argb(edgeAlpha, r, g, b),
                    Color.argb(peakAlpha, r, g, b),
                    Color.argb(peakAlpha, r, g, b)
                )
                val positions = floatArrayOf(0f, fadeStart, solidStart, 1.0f)
                return LinearGradient(
                    0f, 0f, 0f, viewHeight,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }
            POSITION_TOP_BOTTOM -> {
                if (edgeAlpha >= peakAlpha) return null
                val clampedSpread = (spreadRatio * 0.35f).coerceIn(0.04f, 0.16f)
                val colors = intArrayOf(
                    Color.argb(peakAlpha, r, g, b),
                    Color.argb(edgeAlpha, r, g, b),
                    Color.argb(edgeAlpha, r, g, b),
                    Color.argb(peakAlpha, r, g, b)
                )
                val positions = floatArrayOf(0f, clampedSpread, 1f - clampedSpread, 1.0f)
                return LinearGradient(
                    0f, 0f, 0f, viewHeight,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }
            else -> {
                if (edgeAlpha >= peakAlpha) return null
                val clampedSpread = spreadRatio.coerceIn(0.05f, 0.48f)
                val colors = intArrayOf(
                    Color.argb(edgeAlpha, r, g, b),
                    Color.argb(peakAlpha, r, g, b),
                    Color.argb(peakAlpha, r, g, b),
                    Color.argb(edgeAlpha, r, g, b)
                )
                val positions = floatArrayOf(0f, clampedSpread, 1f - clampedSpread, 1.0f)
                return LinearGradient(
                    0f, 0f, 0f, viewHeight,
                    colors, positions,
                    Shader.TileMode.CLAMP
                )
            }
        }
    }

    /**
     * Renders a thin, elegant outline border on [path] with customizable placement (Sides, Top,
     * Bottom, All Around), width, opacity, and fade transition spread.
     */
    @JvmStatic
    fun drawBorder(
        canvas: Canvas,
        path: Path,
        paint: Paint,
        borderWidthPx: Float,
        borderOpacity: Float,
        borderFade: Float,
        borderPosition: Int = POSITION_SIDES,
        borderFadeSpread: Float = DEFAULT_BORDER_FADE_SPREAD,
        isWhite: Boolean = false,
        viewHeight: Float
    ) {
        if (borderWidthPx <= 0f || borderOpacity <= 0f || viewHeight <= 0f) return
        val peakAlpha = (255 * (borderOpacity / 100f).coerceIn(0f, 1f)).toInt()
        val fadeFactor = (borderFade / 100f).coerceIn(0f, 1f)
        val edgeAlpha = (peakAlpha * (1f - fadeFactor)).toInt()
        val r = if (isWhite) 255 else 0
        val g = if (isWhite) 255 else 0
        val b = if (isWhite) 255 else 0
        val spreadRatio = (borderFadeSpread / 100f).coerceIn(0.05f, 0.80f)

        if (borderPosition == POSITION_ALL || edgeAlpha >= peakAlpha) {
            paint.shader = null
            paint.color = Color.argb(peakAlpha, r, g, b)
        } else {
            val spreadInt = (spreadRatio * 1000f).toInt()
            val cacheKey = (viewHeight.toInt().toLong() shl 36) or
                    (borderPosition.toLong() shl 32) or
                    (if (isWhite) 1L shl 31 else 0L) or
                    (peakAlpha.toLong() shl 20) or
                    (edgeAlpha.toLong() shl 10) or
                    (spreadInt.toLong() and 0x3FFL)

            var shader = shaderCache.get(cacheKey)
            if (shader == null) {
                shader = createBorderShader(borderPosition, isWhite, peakAlpha, edgeAlpha, spreadRatio, viewHeight)
                if (shaderCache.size() > 64) {
                    shaderCache.clear()
                }
                if (shader != null) {
                    shaderCache.put(cacheKey, shader)
                }
            }
            paint.shader = shader
            if (shader == null) {
                paint.color = Color.argb(peakAlpha, r, g, b)
            }
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidthPx * 2f
        canvas.drawPath(path, paint)
    }

    /**
     * Applies standard master glass parameters from persisted settings (or QWEA0 defaults).
     */
    @JvmStatic
    fun applyDefaultConfig(glassView: LiquidGlassView) {
        val settings = loadSettings(glassView.context)
        applyConfig(glassView, settings)
    }
}
