# BlissLauncher3 (OS Launcher)

**BlissLauncher3** is a modified version of the AOSP [Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3), with features inspired from [BlissLauncher v1](https://gitlab.e.foundation/e/os/BlissLauncher/-/tree/master).

It is designed to reproduce the **Apple iOS Home Screen** experience as accurately as possible, with pixel-level visual fidelity.

## Architecture & Key Components

To build on top of this launcher without breaking existing features, it is critical to understand how the iOS-specific UI components are integrated into the Launcher3 backend:

### 1. Workspace & Navigation (`Workspace.java`)
- **Role:** Manages the home screen pages, icon grids, page scrolling, and status bar inset transitions.
- **iOS Modifications:** 
  - Overrides standard page transitions.
  - Dynamically injects the App Library as the final (rightmost) pinned page (`bindAndInitAppLibraryScreen()`).
  - Ensures the status bar doesn't prematurely fade when transitioning between folders and the home screen.

### 2. Glassmorphism & Wallpaper Rendering
- **Role:** Reproduces the iconic iOS real-time blur and glass effects on the Dock, Folders, and App Library.
- **Components:**
  - `WallpaperRepository.kt`: Responsible for safely extracting the system wallpaper. On Android 11+ (API 30+), it automatically requests `MANAGE_EXTERNAL_STORAGE` (All Files Access) on first launch to bypass modern Android storage restrictions and allow real-time wallpaper reading.
  - `WallpaperProxyView.kt`: Renders the extracted wallpaper behind translucent UI elements.
  - **Do Not Break:** The glass effect relies on sampling the real wallpaper. Never fake wallpaper positioning or use static background colors for these elements.

### 3. App Library (`com.android.launcher3.applibrary.*`)
- **Role:** Replaces the standard Android App Drawer with an iOS-style categorized App Library.
- **Components:**
  - `AppLibraryPage.java`: The main container embedded into the rightmost Workspace page.
  - `CategoryCardView.java`: Renders the 2x2 folder-like grids for app categories.
  - `MiniClusterView.java`: Handles the dynamically scaled micro-icons inside a category card.
  - `ExpandedCategoryAdapter.java`: Manages the list of apps when a category card is clicked/expanded.
  - **Do Not Break:** Icons within the App Library cards use specialized layouts (`app_library_card_icon.xml`) with text forced to empty (`""`) and specific icon dimensions (`folderChildIconSizePx`) to prevent them from cutting off at the bottom.

### 4. Folders (`Folder.java`, `FolderPagedView.java`)
- **Role:** Replicates iOS folder behavior, paginated grids, and drag-and-drop mechanics.
- **iOS Modifications:**
  - Folders use the real-time glass background.
  - **Do Not Break:** Drag and drop mechanics out of folders require strict lifecycle management. Do not prematurely nullify drag views (`mCurrentDragView`) inside `completeDragExit()` if the drop animation is still in progress, as this will trigger NullPointerExceptions when returning to the workspace.

## Development Guidelines

If you are modifying the UI, strictly follow these rules:

1. **Do not hardcode arbitrary `dp` values.** Derive values from the iOS reference (e.g., Dock Radius / Dock Height). Shared geometry must remain synchronized across the UI.
2. **Never claim a UI task is complete without visual evidence.** If uncertainty exists, assume the implementation is still wrong.
3. **Respect Android Security Policies.** When dealing with file access (like wallpapers), always account for Scoped Storage and the strict permissions required on Android 13+.
4. **Maintain Performance.** Avoid unnecessary recompositions, bitmap copies, or allocations during animations and scrolls.

---

## Building

- Clone the repository

  ```git
  git clone --recurse-submodules https://gitlab.e.foundation/e/os/BlissLauncher3 -b v1-u BlissLauncher3u
  ```

- To build this project, we need the following jars inside the `prebuilts` folder from our AOSP build directory:

  - **framework-14.jar**: `out/soong/.intermediates/frameworks/base/framework/android_common/turbine-combined/framework.jar`

  - We need to build the `Launcher3QuickStepLib` module using `m Launcher3QuickStepLib` and then copy the jar from the following directory:

    - **classes.jar**: `out/target/common/obj/JAVA_LIBRARIES/Launcher3QuickStepLib_intermediates/classes.jar`

- Now we need to run two tasks to generate the proper jars:

  ```bash
  # Unzip the classes.jar file
  ./gradlew unzipJar

  # Generate the final jar for Quickstep
  ./gradlew makeReleaseJar
  ```

- Launch Android Studio and Import the project

- Build the project through the IDE or run the following command:

  ```bash
  ./gradlew assembleBlissWithQuickstepDebug
  ```

## Installing

- Below conditions are required to install the app:

  - User should be on **Android 16 (API 36)** /e/OS or LineageOS (or a compatible ROM)
  - Rom should be signed with **test keys**

- Download and install the APK like any other normal app from the [pipeline](https://gitlab.e.foundation/e/os/BlissLauncher/-/pipelines/latest?ref=v1-u)

- Download and install the icon mask like normal app [SquircleMask.apk](https://gitlab.e.foundation/internal/wiki/-/wikis/uploads/320461a58f097993b29772abe0d2b0b9/KGLN4.apk)

- Go to _Settings > Apps > Default apps > Launcher_ and change launcher to `Blisslauncher` (with green icon)

- It will open a page about `Usage access`. Allow the new BlissLauncher (`Permit usage access`)

- Go to Settings > Display > Icon Shape > Select **Squircle**

- Clear the data of Blisslauncher3 manually through settings or run the command through adb:

  ```bash
  adb shell pm clear com.ihome.launcher
  ```

- Reboot

- Now it is totally ready to use and play around with!

## License

```text
Copyright © MURENA SAS 2024.

All rights reserved. This program and the accompanying materials
are made available under the terms of the GNU Public License v3.0
which accompanies this distribution, and is available at
http://www.gnu.org/licenses/gpl.html
```
