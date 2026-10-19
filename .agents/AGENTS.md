# 1. Hierarchy of Truth & Skill Routing
This repository uses the Android MCP Server (`android-mcp`) and platform skills.
1. **`AGENTS.md` Outranks `SKILL.md`:** Installed skills provide general platform guidance. If a loaded skill (e.g., `navigation`, `system`, `testing-setup`) suggests Jetpack Compose (`@Composable`) or Compose APIs, **ignore the Compose path** and apply the AOSP Launcher3 Android View / XML equivalent.
2. **Never Generate Jetpack Compose:** All UI in this launcher is built on the Android View system (`Launcher.java`, `Workspace`, `CellLayout`, `BubbleTextView`, `Folder`, custom views, and XML layouts). Do NOT introduce `@Composable` dependencies or code.

---

# 1B. AOSP Launcher3 Architecture & Custom View Rules
When modifying UI or behavior, follow Launcher3's internal architecture rather than standard Android app patterns:

1. **View Lookup & Inflation:**
   - `viewBinding` and `dataBinding` are **disabled**. Use standard `findViewById` and `LayoutInflater.from(context).inflate(...)`.
   - Views are heavily pooled and recycled across `Workspace`, `Hotseat`, `Folder`, and `AllAppsContainerView`. Always reset transient view properties (alpha, scale, translation, animations) when binding or removing views.
2. **State Machine (`LauncherState` & `StateManager`):**
   - Screen transitions (Normal, SpringLoaded, Folder, Overview, AllApps) are driven by `Launcher.getStateManager().goToState(...)` and `StateManager.StateHandler`, **never** by Activity/Fragment transactions or Navigation graphs.
   - When animating properties during state changes, hook into `StateAnimationConfig` and `PropertySetter` so both animated and non-animated state transitions stay in sync.
3. **Data Binding & Persistence (`LauncherModel` & `ItemInfo`):**
   - Workspace items are represented by `ItemInfo` subclasses (`WorkspaceItemInfo`, `FolderInfo`, `LauncherAppWidgetInfo`).
   - Any position, container, or cell change (`container`, `screenId`, `cellX`, `cellY`) must go through `ModelWriter` (`launcher.getModelWriter().modifyItemInDatabase(...)` / `addOrMoveItemInDatabase(...)`) on `Executors.MODEL_EXECUTOR`.
4. **Touch & Drag Pipeline (`DragLayer` & `DragController`):**
   - Touch interception flows through `DragLayer` -> `TouchController` implementations -> child `CellLayout` / `BubbleTextView`.
   - Never block touch events in child views with naive `OnTouchListener` overrides that starve `DragLayer` gesture detectors or long-press drag initiation (`DragOptions`).
5. **Sizing & Grid Math (`DeviceProfile` & `InvariantDeviceProfile`):**
   - Icon sizes, cell dimensions, hotseat padding, and folder bounds are computed dynamically in `DeviceProfile` (`getDeviceProfile()`).
   - Avoid hardcoding dimensions in XML when `DeviceProfile` dynamically overrides margins/padding in `onMeasure` or `setInsets(Rect)`.

---

# Rule: Target Build Variant (`withoutQuickstep` vs `withQuickstep`)

Always use the **`withoutQuickstep`** variant for development, testing on real OEM devices (e.g., Realme, Samsung, Pixel), emulators, and Google Play Store releases:
- **Build command**:
  ```bash
  ./gradlew assembleBlissWithoutQuickstepDebug
  # or for release:
  ./gradlew assembleBlissWithoutQuickstepRelease
  ```
- **Install command**:
  ```bash
  ./gradlew installBlissWithoutQuickstepDebug
  ```
- **Why**:
  - `withoutQuickstep` uses standard launcher permissions (`com.android.launcher3.Launcher`) and is compliant with Google Play Store policies.
  - On non-rooted retail OEM ROMs (Realme UI, One UI, MIUI, etc.), Android restricts the Quickstep recents provider to system-privileged apps; third-party launchers cannot take over the system recents switcher anyway.
  - Only use `withQuickstep` if specifically targeting a custom ROM build where the launcher is installed in `/system/priv-app`, or on rooted devices using QuickSwitch.

---

# Rule: Real Device & Emulator Verification via Android MCP

When taking screenshots or bringing the launcher to the foreground on an emulator or real device to verify UI changes, do not rely on `adb shell input keyevent KEYCODE_HOME` alone. `KEYCODE_HOME` may launch the device's default system launcher (e.g., Nexus Launcher / Realme UI Launcher) instead of the freshly compiled iHome Launcher, resulting in inaccurate screenshots of the wrong launcher.

**Always explicitly open the launcher before taking a screenshot or verifying UI**:
- For the standard **`withoutQuickstep`** variant (default):
  ```bash
  adb shell am start -n com.ihome.launcher/com.android.launcher3.Launcher
  ```
- For the **`withQuickstep`** variant (if used):
  ```bash
  adb shell am start -n com.ihome.launcher/com.android.launcher3.uioverrides.QuickstepLauncher
  ```

After running the command, wait a few seconds (e.g., `sleep 2`) to ensure the launcher has fully rendered before capturing:
```bash
adb exec-out screencap -p > screenshot.png
```
Or use `android-mcp` tools (`Snapshot`, `Click`, `Drag`, `Swipe`) to interact with and verify the live UI hierarchy and visual state.

---

# Rule: UI State Invariants for Folder, Dock, and Drag Operations

To eliminate regressions caused by ad-hoc patches across disparate animation and drag-drop files, all code touching `Folder`, `GridFolder`, `WorkspaceStateTransitionAnimation`, `Workspace`, or `Hotseat` must adhere strictly to these architectural invariants:

### 1. Invariant: Folder Open State (`Folder.getOpen(mLauncher) != null`)
While any folder is open—whether idle, actively wobbling in jiggle mode, or while dragging/reordering apps **inside** the folder:
- **Workspace page items (`ShortcutAndWidgetContainer`)**: MUST be `alpha = 0f`.
- **Hotseat (dock icons)**: MUST be `alpha = 0f`.
- **Dock Glass (`dock_glass`)**: MUST be `alpha = 0f`.
- **Page Indicator / Search Pill (`mWorkspace.getPageIndicator()`)**: MUST be `alpha = 0f`.
- **Blur Layer (`mBlurLayer`)**: MUST remain `alpha = 1f`.
- **Prohibited Anti-Pattern**: NEVER use global flags such as `DragController.isDragging()` to bypass folder open alpha suppression. `isDragging()` is `true` during internal folder reordering as well. If bypassed, rearranging icons inside an open folder will leak dock icons, dock glass, and the search pill underneath/behind the folder card.

### 2. Invariant: Drag Exit to Workspace (`Folder.onDragExit()`)
When an app is dragged **out** of an open folder onto the workspace or dock:
- The folder exits immediately (`completeDragExit()` -> `close(true)`), setting `mIsOpen = false` synchronously so `Folder.getOpen(mLauncher)` becomes `null`.
- Upon `Workspace.onDragEnter()`, the workspace items, dock icons, dock glass, and page indicator / search pill MUST be restored to `alpha = 1f` immediately mid-drag before release.
- The blur layer (`mBlurLayer`) MUST return to `0f`.

### 3. Mandatory 4-Step Regression Verification Matrix
Whenever modifying `Folder`, `GridFolder`, `Workspace`, `WorkspaceStateTransitionAnimation`, or drag logic, verify ALL 4 scenarios before completing the task:
1. **Scenario 1 (Folder Open Idle)**: Tap a folder to open. Verify desktop icons, dock icons, dock glass, and search pill are completely hidden; only folder card and blur layer are visible.
2. **Scenario 2 (Rearrange Inside Folder)**: Long-press an app inside the folder and drag it around within the folder card, then release. Verify dock icons, dock glass, and search pill NEVER become visible at any point (neither during drag nor after drop).
3. **Scenario 3 (Drag Out of Folder)**: Long-press an app in the folder and drag it outside the folder card onto the workspace. Verify the folder closes and homescreen icons, dock icons, dock glass, and search pill immediately become visible mid-drag before release.
4. **Scenario 4 (Drop on Workspace & Restore)**: Release the dragged app onto the workspace. Verify the app is placed in the target cell, the folder collapses cleanly if only 1 item remained, and all homescreen elements remain fully visible.

---

# Rule: Zero-Regression Contract Across All Code Changes

Any future modification to this codebase MUST NOT unfix or regress any previously resolved issues. Always respect the following invariants:

### 1. Invariant: Ghost / Orphan Icon Prevention (`ShortcutAndWidgetContainer.java`)
- **Rule**: Every `removeView()`, `removeViewInLayout()`, `removeViewAt()`, `removeAllViews()`, and `removeAllViewsInLayout()` call in `ShortcutAndWidgetContainer` MUST invoke `view.clearAnimation()` before removal.
- **Why**: `ViewGroup` keeps any view with an active `Animation` in `mDisappearingChildren` and continues drawing it indefinitely if the animation does not terminate (such as wobbling icons in jiggle mode).

### 2. Invariant: Sensor Highlight IPC Flood Guard (`PortraitSensorHighlightController.kt`)
- **Rule**: Never unregister sensor listeners inside high-frequency sensor callbacks (~60Hz) without a registration guard (`isRegisteredInLib`). Repeated unregister Binder IPC calls flood logcat and cause device micro-stutter.

### 3. Invariant: App Removal Flow & Labeling (`SystemShortcut.java`, `LiquidGlassAlertView.kt`)
- **Rule**:
  - The long-press destructive shortcut label MUST be "Remove App" in iOS destructive red (`#FF3B30`), not "Uninstall".
  - The confirmation dialog MUST offer:
    - User/3rd-party apps: "Uninstall" (red, uninstalls package) and "Remove from Home Screen" (blue, removes from desktop/dock, marks in `RemovedAppStore`, but keeps in App Library).
    - System apps: "Remove from Home Screen" (red).

### 4. Invariant: Change Category Picker Contrast (`ChangeCategoryPickerView.kt`)
- **Rule**: All category row labels MUST render in crisp solid black (`#000000` / `Color.BLACK`) with `#7A000000` subtitle for high contrast against the frosted card backdrop.

### 5. Invariant: Spotlight Search & App Library State Consistency
- **Rule**: Spotlight search back gesture must cleanly dismiss the soft keyboard and animate the overlay without glitching. Exiting App Library search must synchronously re-bind categories so the library is never left blank.

---

# Rule: Mandatory Synchronization of `launcher_important_logic.md`

Every time the codebase is updated, modified, or refactored:
- **Rule**: `launcher_important_logic.md` MUST also be updated to ensure that all core code logic remains completely synchronized with the codebase for Gemini analysis.
- **How to update**: Run `python3 scripts/update_launcher_logic_md.py` after code changes to regenerate `launcher_important_logic.md`.
