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

import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.Hotseat
import foundation.e.bliss.preferences.BlissPrefs
import foundation.e.bliss.wallpaper.WallpaperEnvironment
import foundation.e.bliss.wallpaper.WallpaperEnvironmentBuilder

/**
 * Controls the LiquidGlass dock panel ([DockGlassView]) on API 33+, falling back to the classic
 * frosted [BlurViewDelegate] path on API 30–32.
 *
 * Owns the on/off switch ([enabled]) and gates [Hotseat.drawBlur] so the two paths don't overlap.
 *
 * Instantiate at the end of [Launcher.setupViews()], after [mHotseat] is assigned.
 */
class DockGlassController(
    private val hotseat: Hotseat,
    private val dockGlass: DockGlassView,
    private val glassSource: ViewGroup,
    private val sharedPrefs: SharedPreferences,
) : WallpaperEnvironmentBuilder.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    private val envBuilder = WallpaperEnvironmentBuilder.INSTANCE.get(hotseat.context)

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
        envBuilder.addListener(this)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        apply()
    }

    override fun onEnvironmentChanged(environment: WallpaperEnvironment?, bitmapChanged: Boolean) {
        if (supported && enabled) dockGlass.invalidate()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == BlissPrefs.PREF_GLASS_DOCK_ENABLED) {
            enabled = sharedPrefs.getBoolean(key, true)
        }
    }

    private fun apply() {
        val active = supported && enabled
        android.util.Log.d(
            "DockGlassController",
            "LiquidGlass apply: supported=$supported, enabled=$enabled, active=$active"
        )

        dockGlass.visibility = if (active) View.VISIBLE else View.GONE

        // Gate Hotseat's classic blur — the two paths must not paint simultaneously.
        // drawBlur=true  → frosted bitmap crop (API 30–32 or glass disabled)
        // drawBlur=false → glass panel handles the background (API 33+ glass enabled)
        hotseat.drawBlur = !active

        if (active) {
            val wallpaperProxy = glassSource.findViewById<View>(com.android.launcher3.R.id.glass_wallpaper_proxy)
            dockGlass.backdropSource = wallpaperProxy ?: glassSource
            GlassConfig.applyDefaultConfig(dockGlass)
        } else {
            dockGlass.backdropSource = null
        }
    }

    /** Call from [Launcher.onDestroy] to avoid a listener leak. */
    fun destroy() {
        dockGlass.backdropSource = null
        envBuilder.removeListener(this)
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}
