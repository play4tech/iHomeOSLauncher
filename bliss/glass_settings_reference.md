# Bliss Launcher Liquid Glass Settings Reference

Saved on: October 7, 2026
Version: Config Version 5 (Refraction Inward iOS match)

---

## 1. Optical Parameters & Values

| Setting | SharedPreferences Key | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **Refraction Height** | `pref_glass_refraction_height` | `143.0f` | Height of the simulated 3D lens surface curve in pixels. |
| **Bevel Width** | `pref_glass_bevel_width` | `65.0f` | Width of the perimeter bevel border where refraction occurs. |
| **Refraction Falloff** | `pref_glass_refraction_falloff` | `2.94f` | Curvature exponent for the lens profile (smooth falloff). |
| **Dispersion Strength** | `pref_glass_dispersion_strength` | `0.0f` | Chromatic dispersion separating color channels (set to 0 for clean iOS look). |
| **Blur Amount** | `pref_glass_blur_amount` | `0.25f` | Backdrop blur intensity factor. |
| **Saturation** | `pref_glass_saturation` | `120.0f` | Color saturation percentage of backdrop content through the glass (120% for iOS vibrant look). |
| **Aberration Intensity** | `pref_glass_aberration_intensity` | `2.0f` | Chromatic fringe/aberration intensity at extreme edges. |
| **Displacement Scale** | `pref_glass_displacement_scale` | `43.0f` | Maximum pixel displacement for refractive bending. |
| **Elasticity** | `pref_glass_elasticity` | `0.15f` | Dynamic spring/inertia response factor during motion. |
| **Edge Softness** | `pref_glass_edge_softness` | `2.0f` | Anti-aliasing radius / softness of the glass boundary. |
| **Glass Tint** | `pref_glass_tint` | `0x28FFFFFF` (`687865855`) | 16% alpha pure white translucent milky glass body tint (matches Apple iOS). |
| **Adaptive Tint** | `pref_glass_enable_adaptive_tint` | `false` | Whether to dynamically adapt glass tint from wallpaper palette. |
| **Refraction No Fold** | `pref_glass_refraction_no_fold` | `false` | Prevents edge reflection folding if enabled. |
| **Refraction Outward** | `pref_glass_refraction_outward` | `false` | **Crucial iOS Match:** `false` samples inward (`refractDir = -1.0f`), deflecting dock refraction lines to the **left**. (`true` was convex/outward, deflecting lines to the right). |

---

## 2. Dynamic Specular Highlights & Sensor

| Setting | SharedPreferences Key | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **Edge Highlight Border Width**| `pref_glass_edge_highlight_border_width` | `0.4f` | Stroke width (in dp) of the rim highlight. |
| **Edge Highlight Opacity** | `pref_glass_edge_highlight_opacity` | `100.0f` | Peak opacity percentage of the specular highlight. |
| **Enable Edge Highlight** | `pref_glass_enable_edge_highlight` | `true` | Enables specular sheen on upper edges. |
| **Enable Sensor Highlight** | `pref_glass_enable_sensor_highlight` | `false` | Specular light direction is locked vertically to top & bottom (0.0, 1.0) with zero horizontal component to eliminate side specular highlights and prevent sensor IPC battery drain. |

---

## 3. Hairline Border Settings

| Setting | SharedPreferences Key | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **Border Enabled** | `pref_glass_border_enabled` | `true` | Hairline border enabled along edges. |
| **Border Width** | `pref_glass_border_width` | `0.25f` | Hairline border thickness in dp. |
| **Border Opacity** | `pref_glass_border_opacity` | `55.0f` | Peak opacity percentage (55%). |
| **Border Fade** | `pref_glass_border_fade` | `100.0f` | End fade percentage (100% transparent at tips). |
| **Border Position** | `pref_glass_border_position` | `4` (`POSITION_TOP_BOTTOM`) | Position flag (`0` = sides, `1` = top, `2` = bottom, `3` = all, `4` = top & bottom). |
| **Border Fade Spread** | `pref_glass_border_fade_spread` | `35.0f` | Gradient transition spread percentage (35%). |

---

## 4. White Accent Border Settings

| Setting | SharedPreferences Key | Default Value | Description |
| :--- | :--- | :--- | :--- |
| **White Border Enabled** | `pref_glass_white_border_enabled` | `false` | Disabled by default. |
| **White Border Width** | `pref_glass_white_border_width` | `0.25f` | Thickness in dp. |
| **White Border Opacity** | `pref_glass_white_border_opacity` | `80.0f` | Opacity percentage. |
| **White Border Fade** | `pref_glass_white_border_fade` | `100.0f` | End fade percentage. |
| **White Border Position** | `pref_glass_white_border_position` | `4` (`POSITION_TOP_BOTTOM`) | Top & Bottom position. |
| **White Border Fade Spread** | `pref_glass_white_border_fade_spread` | `35.0f` | Gradient spread. |

---

## 5. Raw SharedPreferences XML Representation (Device Snapshot)

```xml
<map>
    <int name="pref_glass_config_version" value="13" />
    <int name="pref_glass_tint" value="687865855" />
    <float name="pref_glass_refraction_height" value="143.0" />
    <float name="pref_glass_bevel_width" value="65.0" />
    <float name="pref_glass_refraction_falloff" value="2.94" />
    <float name="pref_glass_dispersion_strength" value="0.0" />
    <float name="pref_glass_blur_amount" value="0.25" />
    <float name="pref_glass_saturation" value="120.0" />
    <float name="pref_glass_aberration_intensity" value="2.0" />
    <float name="pref_glass_displacement_scale" value="43.0" />
    <float name="pref_glass_elasticity" value="0.15" />
    <float name="pref_glass_edge_highlight_border_width" value="0.4" />
    <float name="pref_glass_edge_highlight_opacity" value="100.0" />
    <float name="pref_glass_edge_softness" value="2.0" />
    <boolean name="pref_glass_enable_edge_highlight" value="true" />
    <boolean name="pref_glass_enable_sensor_highlight" value="true" />
    <boolean name="pref_glass_enable_adaptive_tint" value="false" />
    <boolean name="pref_glass_refraction_no_fold" value="false" />
    <boolean name="pref_glass_refraction_outward" value="false" />
    <boolean name="pref_glass_dock_enabled" value="true" />
    <boolean name="pref_glass_border_enabled" value="true" />
    <float name="pref_glass_border_width" value="0.25" />
    <float name="pref_glass_border_opacity" value="80.0" />
    <float name="pref_glass_border_fade" value="100.0" />
    <int name="pref_glass_border_position" value="0" />
    <float name="pref_glass_border_fade_spread" value="35.0" />
    <boolean name="pref_glass_white_border_enabled" value="false" />
    <float name="pref_glass_white_border_width" value="0.25" />
    <float name="pref_glass_white_border_opacity" value="80.0" />
    <float name="pref_glass_white_border_fade" value="100.0" />
    <int name="pref_glass_white_border_position" value="1" />
    <float name="pref_glass_white_border_fade_spread" value="35.0" />
</map>
```

---

## 6. App Icon Specular Highlights (Squircle Edge)

- **Architecture:** Baked directly into the generated icon squircle bitmap in [`BaseIconFactory.java`](file:///Users/sandhiya/Documents/iOSLauncher/libs_systemui/iconloaderlib/src/com/android/launcher3/icons/BaseIconFactory.java) using the exact `squirclePath` and superellipse formula ($n = 4.0$).
- **Why Baked vs Runtime:** Different Android devices and OEM skins (e.g. Realme UI / ColorOS vs stock AOSP AVD) use different normalization scales (`scale = 0.826` on Realme vs `0.912` on AVD). Runtime drawing with a fixed ratio produced a floating highlight ~22px above/below the icon on Realme. Baking into `BaseIconFactory` clips and strokes strictly along the true squircle geometry for 100% pixel-perfect alignment on any device and zero per-frame canvas overhead.
- **Top Specular Arc:** Centered at $270^\circ$ (top rim), span $85^\circ$, peak alpha 80 (31% opacity), horizontal linear gradient fade to 0 alpha at tips.
- **Bottom Specular Arc:** Centered at $90^\circ$ (bottom rim), span $80^\circ$, peak alpha 55 (21% opacity), horizontal linear gradient fade to 0 alpha at tips.
- **Cache Invalidation:** Managed via `BaseIconCache.IconDB.RELEASE_VERSION` (bumped to 35) to automatically recreate icon SQLite tables when updated.

