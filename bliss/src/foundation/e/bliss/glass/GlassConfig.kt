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

    // --- Default Parameter Constants (iOS 26 Liquid Glass defaults from QWEA0) ---
    const val DEFAULT_REFRACTION_HEIGHT: Float = 160f
    const val DEFAULT_BEVEL_WIDTH: Float = 48f
    const val DEFAULT_REFRACTION_FALLOFF: Float = 2f
    const val DEFAULT_DISPERSION_STRENGTH: Float = 0.10f
    const val DEFAULT_BLUR_AMOUNT: Float = 0.0625f
    const val DEFAULT_SATURATION: Float = 140f
    const val DEFAULT_ABERRATION_INTENSITY: Float = 2f
    const val DEFAULT_DISPLACEMENT_SCALE: Float = 70f
    const val DEFAULT_ELASTICITY: Float = 0.15f
    const val DEFAULT_EDGE_HIGHLIGHT_BORDER_WIDTH: Float = 1.5f
    const val DEFAULT_EDGE_HIGHLIGHT_OPACITY: Float = 100f
    const val DEFAULT_EDGE_SOFTNESS: Float = 0f
    const val DEFAULT_ENABLE_EDGE_HIGHLIGHT: Boolean = true
    const val DEFAULT_ENABLE_SENSOR_HIGHLIGHT: Boolean = true
    const val DEFAULT_ENABLE_ADAPTIVE_TINT: Boolean = true
    const val DEFAULT_REFRACTION_NO_FOLD: Boolean = false
    const val DEFAULT_REFRACTION_OUTWARD: Boolean = false
    const val DEFAULT_BORDER_ENABLED: Boolean = true
    const val DEFAULT_BORDER_WIDTH: Float = 0.25f        // in dp (ultra-thin hairline)
    const val DEFAULT_BORDER_OPACITY: Float = 80f        // 80% peak opacity at sides
    const val DEFAULT_BORDER_FADE: Float = 100f          // 100% fade at top and bottom

    // Backward-compatibility aliases
    const val DEFAULT_DISPERSION: Float = DEFAULT_DISPERSION_STRENGTH

    // --- SharedPreferences Keys ---
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
        var borderEnabled: Boolean = DEFAULT_BORDER_ENABLED,
        var borderWidth: Float = DEFAULT_BORDER_WIDTH,
        var borderOpacity: Float = DEFAULT_BORDER_OPACITY,
        var borderFade: Float = DEFAULT_BORDER_FADE,
    )

    @Volatile
    private var cachedSettings: GlassSettings? = null

    @JvmStatic
    fun loadSettings(context: Context): GlassSettings {
        cachedSettings?.let { return it.copy() }
        val prefs = LauncherPrefs.getPrefs(context)
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
            borderEnabled = prefs.getBoolean(PREF_KEY_BORDER_ENABLED, DEFAULT_BORDER_ENABLED),
            borderWidth = prefs.getFloat(PREF_KEY_BORDER_WIDTH, DEFAULT_BORDER_WIDTH).let {
                if (it == 1.0f || it == 0.5f) DEFAULT_BORDER_WIDTH else it
            },
            borderOpacity = prefs.getFloat(PREF_KEY_BORDER_OPACITY, DEFAULT_BORDER_OPACITY),
            borderFade = prefs.getFloat(PREF_KEY_BORDER_FADE, DEFAULT_BORDER_FADE)
        )
        cachedSettings = settings
        return settings.copy()
    }

    @JvmStatic
    fun saveSettings(context: Context, settings: GlassSettings) {
        cachedSettings = settings.copy()
        val prefs = LauncherPrefs.getPrefs(context)
        prefs.edit()
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
            .apply()
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
        glassView.material = GlassMaterial.REGULAR      // REGULAR (readability) | CLEAR (over media)
        glassView.refractionHeight = settings.refractionHeight
        glassView.bevelWidth = settings.bevelWidth
        glassView.refractionFalloff = settings.refractionFalloff
        glassView.dispersionStrength = settings.dispersionStrength
        glassView.enableEdgeHighlight = settings.enableEdgeHighlight
        glassView.edgeHighlightBorderWidth = settings.edgeHighlightBorderWidth
        glassView.edgeHighlightOpacity = settings.edgeHighlightOpacity
        glassView.enableSensorHighlight = settings.enableSensorHighlight
        glassView.enableAdaptiveTint = settings.enableAdaptiveTint

        // --- Optical blur, saturation, aberration & physics defaults ---
        glassView.blurAmount = settings.blurAmount
        glassView.saturation = settings.saturation
        glassView.aberrationIntensity = settings.aberrationIntensity
        glassView.displacementScale = settings.displacementScale
        glassView.elasticity = settings.elasticity
        glassView.glassTint = Color.TRANSPARENT
        glassView.useShaderPipeline = true
        glassView.adaptiveLensScale = true
        glassView.refractionNoFold = settings.refractionNoFold
        glassView.refractionOutward = settings.refractionOutward
        glassView.edgeSoftness = settings.edgeSoftness

        glassView.enablePressEffect = false
        glassView.enableDynamicBackground = true
        glassView.collectFrameStats = false
        glassView.accessibilityMode = GlassAccessibilityMode.AUTO

        if (glassView is BorderedGlassView) {
            glassView.borderEnabled = settings.borderEnabled
            glassView.borderWidthPx = settings.borderWidth * glassView.resources.displayMetrics.density
            glassView.borderOpacity = settings.borderOpacity
            glassView.borderFade = settings.borderFade
            glassView.invalidate()
        }
    }

    /**
     * Renders a thin, elegant outline border on [path] with a vertical linear gradient that fades
     * at the top and bottom edges according to [borderFade].
     */
    @JvmStatic
    fun drawBorder(
        canvas: Canvas,
        path: Path,
        paint: Paint,
        borderWidthPx: Float,
        borderOpacity: Float,
        borderFade: Float,
        viewHeight: Float
    ) {
        if (borderWidthPx <= 0f || borderOpacity <= 0f || viewHeight <= 0f) return
        val peakAlpha = (255 * (borderOpacity / 100f).coerceIn(0f, 1f)).toInt()
        val fadeFactor = (borderFade / 100f).coerceIn(0f, 1f)
        val edgeAlpha = (peakAlpha * (1f - fadeFactor)).toInt()

        if (edgeAlpha >= peakAlpha) {
            paint.shader = null
            paint.color = Color.argb(peakAlpha, 0, 0, 0)
        } else {
            val colors = intArrayOf(
                Color.argb(edgeAlpha, 0, 0, 0),
                Color.argb(peakAlpha, 0, 0, 0),
                Color.argb(peakAlpha, 0, 0, 0),
                Color.argb(edgeAlpha, 0, 0, 0)
            )
            val positions = floatArrayOf(0f, 0.35f, 0.65f, 1.0f)
            paint.shader = LinearGradient(
                0f, 0f, 0f, viewHeight,
                colors, positions,
                Shader.TileMode.CLAMP
            )
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
        glassView.cornerRadius = 0f
    }
}
