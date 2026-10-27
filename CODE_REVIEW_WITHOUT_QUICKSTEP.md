# Code Review: Bliss Without Quickstep

**Date:** 2026-10-20  
**Review scope:** `blissWithoutQuickstep` build variant  
**Review type:** Static source review

## Scope

Gradle maps `blissWithoutQuickstep` to the Bliss manifest in [`build.gradle`](build.gradle#L277). This report covers the withoutQuickstep source set and the shared launcher code it uses. Quickstep-only permissions, recents, and taskbar findings are excluded.

The review focused on folder drag and persistence, onboarding back navigation, widget profile cleanup, wallpaper access, and Android launcher compatibility. No source files were changed and no tests or OEM device matrix were run.

## Findings

### [P2] Folder order can revert after dragging an app out

**Location:** [`Folder.java`](src/com/android/launcher3/folder/Folder.java#L1353), with reorder behavior in [`FolderPagedView.java`](src/com/android/launcher3/folder/FolderPagedView.java#L609)

`FolderPagedView.realTimeReorder()` changes icon positions and ranks, but `Folder.updateItemLocationsInDatabaseBatch()` persists items by iterating `mInfo.contents`. The list is not rebuilt from the live icon order before that write. The existing sort in `onDropCompleted()` only runs on failed drops. The current working-tree change also removes the prior code that rebuilt `mInfo.contents` from `getIconsInReadingOrder()`.

**Trigger:** Reorder items while dragging inside a multi-item folder, then successfully drag an item out of the folder.

**Impact:** The batch update can assign ranks from the stale list order. Reopening the folder can restore its previous order.

**Recommended fix:** Synchronize `mInfo.contents` from the current icon reading order before assigning and persisting ranks. Ensure reorders that cross pages also keep item ranks consistent with the displayed order.

### [P2] Onboarding back navigation can reopen onboarding on Android 16

**Location:** [`OnboardingActivity.kt`](bliss/src/foundation/e/blisslauncher/features/onboarding/OnboardingActivity.kt#L329), completion flag at [line 358](bliss/src/foundation/e/blisslauncher/features/onboarding/OnboardingActivity.kt#L358)

The activity relies on the deprecated `onBackPressed()` override to navigate between onboarding steps or call `completeOnboarding()`. The Bliss withoutQuickstep manifest targets API 36 and enables `OnBackInvokedCallback`. On Android 16, apps targeting API 36 no longer receive `onBackPressed()` for system back navigation.

**Trigger:** On Android 16, press Back during onboarding.

**Impact:** The activity can close without setting `FIRST_RUN_ONBOARDING_DONE`; Launcher may show onboarding again on resume.

**Recommended fix:** Register an `OnBackInvokedCallback` or a compatible AndroidX back callback for this activity, and route it through the existing step-navigation logic.

**Platform reference:** [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)

### [P2] Removing an app from one profile can delete widgets from another

**Location:** [`WidgetContainer.kt`](bliss/src/foundation/e/bliss/widgets/WidgetContainer.kt#L229), database schema in [`WidgetsDbHelper.kt`](bliss/src/foundation/e/bliss/widgets/WidgetsDbHelper.kt#L31)

`onPackageRemoved(packageName, user)` ignores the `user` argument and deletes every stored widget whose component has the matching package name. The widget table does not store profile identity.

**Trigger:** The same widget provider is installed in personal and work profiles; remove it from one profile.

**Impact:** Stored widgets for that package in the other profile can also be deleted.

**Recommended fix:** Persist profile identity with each widget and filter cleanup by both package and profile.

### [P2] Wallpaper sampling falls back on Android 14 and later

**Location:** [`WallpaperRepository.kt`](bliss/src/foundation/e/bliss/wallpaper/WallpaperRepository.kt#L309), fallback path at [line 165](bliss/src/foundation/e/bliss/wallpaper/WallpaperRepository.kt#L165); common permissions in [`AndroidManifest-common.xml`](AndroidManifest-common.xml#L34)

The repository tries `WallpaperManager.getWallpaperFile()` and then `WallpaperManager.drawable`. The common manifest does not declare `MANAGE_EXTERNAL_STORAGE` or the internal wallpaper permission. Android documents that direct wallpaper access requires one of these permissions and that these APIs are restricted on newer versions.

**Impact:** The caught access failures lead to the built-in ROM fallback, so the glass backdrop may not reflect the user’s current wallpaper. This should not prevent the launcher from starting.

**Recommended fix:** Choose a wallpaper presentation approach supported for a regular launcher app, or make the fallback behavior explicit in the UI. Avoid relying on broad storage access solely for wallpaper sampling.

**Platform reference:** [WallpaperManager API reference](https://developer.android.com/reference/android/app/WallpaperManager)

## Cross-device compatibility gaps

### Android Private Space is not supported

**Location:** [`Flags.java`](src/com/android/launcher3/Flags.java#L117)

`enablePrivateSpace()` returns `false`, and the withoutQuickstep manifest does not declare `ACCESS_HIDDEN_PROFILES`. As a result, apps in Android 15+ Private Space will not be visible in this launcher. Supporting Private Space requires more than declaring the permission: Android also requires a separate container with lock, unlock, and visibility handling.

**Platform reference:** [Android 15 launcher requirements](https://developer.android.com/about/versions/15/behavior-changes-all#launcher-apps)

### Recents and system gestures

The withoutQuickstep variant omits Quickstep-specific sources and manifest configuration. It can act as the Home launcher, while recents, taskbar, and gesture animations remain dependent on the device’s System UI and OEM implementation. Those behaviors need device-level checks if they are part of the product’s compatibility target.

## Validation limits

This is a static review, not a certification across OEM skins. No build, automated test suite, emulator run, or physical-device checks on Samsung One UI, Xiaomi HyperOS, Pixel, or other OEMs were performed. The findings above are source-level risks; actual launcher-role selection, gesture behavior, widget profile handling, and wallpaper behavior should be validated on representative devices before claiming universal compatibility.
