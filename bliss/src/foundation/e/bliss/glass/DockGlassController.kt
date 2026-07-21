package foundation.e.bliss.glass

import android.content.SharedPreferences
import android.os.Build

import android.view.View
import android.view.ViewGroup
import com.android.launcher3.Hotseat
import com.android.launcher3.R
import foundation.e.bliss.preferences.BlissPrefs
import foundation.e.bliss.wallpaper.WallpaperEnvironment
import foundation.e.bliss.wallpaper.WallpaperEnvironmentBuilder


/**
 * Controls the LiquidGlass dock panel ([DockGlassView]) on API 33+, falling back
 * to the classic frosted [BlurViewDelegate] path on API 30–32.
 *
 * Owns the on/off switch ([enabled]) and gates [Hotseat.drawBlur] so the
 * two paths don't overlap.
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

        dockGlass.visibility = if (active) View.VISIBLE else View.GONE

        // Gate Hotseat's classic blur — the two paths must not paint simultaneously.
        // drawBlur=true  → frosted bitmap crop (API 30–32 or glass disabled)
        // drawBlur=false → glass panel handles the background (API 33+ glass enabled)
        hotseat.drawBlur = !active

        if (active) {
            // bind() attaches an OnPreDrawListener to glassSource that re-records
            // the subtree on every frame — no manual re-bind needed after scroll/layout.
            dockGlass.bind(glassSource)

            // Glass parameters matched to iOS dock aesthetic.
            // All values are tunable here without touching other files.
            dockGlass.setBlurRadius(2f)
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
        envBuilder.removeListener(this)
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}
