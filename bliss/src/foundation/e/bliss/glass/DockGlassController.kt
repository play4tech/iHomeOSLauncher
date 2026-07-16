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
package foundation.e.bliss.glass

import android.content.SharedPreferences
import android.os.Build

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.android.launcher3.Hotseat
import com.android.launcher3.R
import foundation.e.bliss.blur.BlurWallpaperProvider
import foundation.e.bliss.preferences.BlissPrefs


/**
 * Controls the LiquidGlass dock panel ([DockGlassView]) on API 33+, falling back
 * to the classic frosted [BlurViewDelegate] path on API 30–32.
 *
 * Owns the on/off switch ([enabled]), keeps the wallpaper proxy [ImageView] in sync
 * with [BlurWallpaperProvider]'s raw bitmap, and gates [Hotseat.drawBlur] so the
 * two paths don't overlap.
 *
 * Instantiate at the end of [Launcher.setupViews()], after [mHotseat] is assigned.
 */
class DockGlassController(
    private val hotseat: Hotseat,
    private val dockGlass: DockGlassView,
    private val wallpaperProxy: ImageView,
    private val glassSource: ViewGroup,
    private val provider: BlurWallpaperProvider,
    private val sharedPrefs: SharedPreferences,
) : BlurWallpaperProvider.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    /** True only if the device runs Android 13+ (API 33), where RenderEffect is available. */
    private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether the glass effect is user-enabled. Set this from the persisted SharedPreference.
     * Changing it at runtime flips the visible effect immediately.
     */
    var enabled: Boolean = false
        set(value) {
            field = value
            apply()
        }

    init {
        provider.addListener(this)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        apply()
    }

    // region BlurWallpaperProvider.Listener

    override fun onWallpaperChanged() {
        // Keep the proxy in sync so the glass panel refracts the latest wallpaper.
        wallpaperProxy.setImageBitmap(provider.rawWallpaper ?: provider.placeholder)
        if (supported && enabled) dockGlass.invalidate()
    }

    override fun onEnabledChanged() = onWallpaperChanged()

    // endregion

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == BlissPrefs.PREF_GLASS_DOCK_ENABLED) {
            enabled = sharedPrefs.getBoolean(key, true)
        }
    }

    private fun apply() {
        val active = supported && enabled

        dockGlass.visibility = if (active) View.VISIBLE else View.GONE

        // Gate Hotseat's classic blur — the two paths must not paint simultaneously.
        // drawBlur=true  → frosted bitmap crop (API 30–32 or glass disabled)
        // drawBlur=false → glass panel handles the background (API 33+ glass enabled)
        hotseat.drawBlur = !active

        if (active) {
            // Feed current wallpaper into the proxy before binding so the first
            // recorded frame already has wallpaper texture.
            wallpaperProxy.setImageBitmap(provider.rawWallpaper ?: provider.placeholder)

            // bind() attaches an OnPreDrawListener to glassSource that re-records
            // the subtree on every frame — no manual re-bind needed after scroll/layout.
            dockGlass.bind(glassSource)

            // Glass parameters matched to iOS dock aesthetic.
            // All values are tunable here without touching other files.
            val density = hotseat.resources.displayMetrics.density
            val cornerRadiusPx = hotseat.resources.getDimension(R.dimen.dock_glass_corner_radius)
            dockGlass.setCornerRadius(cornerRadiusPx)
            dockGlass.setBlurRadius(25f)
            dockGlass.setDispersion(0.2f)
            dockGlass.setTintAlpha(0.25f)     // iOS frosted look
            dockGlass.setTintColorRed(1.0f)
            dockGlass.setTintColorGreen(1.0f)
            dockGlass.setTintColorBlue(1.0f)
            dockGlass.setDraggableEnabled(false)
            dockGlass.setElasticEnabled(false)
            dockGlass.setTouchEffectEnabled(false)
        }
    }

    /**
     * Call from [Launcher.onDestroy] to avoid a listener leak.
     */
    fun destroy() {
        provider.removeListener(this)
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}
