# Windows Migration & Antigravity Setup Guide

This document is the single source of truth for setting up, building, and developing the **iHome Launcher** project on a Windows machine. When starting a session in Antigravity on Windows, use this file to bootstrap the workspace and verify all tools, dependencies, and architectural invariants.

---

## 1. Project & Repository Context

- **Project**: iHome Launcher (iOS-styled BlissLauncher3 / Android Launcher3 fork)
- **Active Branch**: `argon`
- **GitHub Repository**: `https://github.com/play4tech/OS_Launcher.git`
- **GitHub Account**: `play4tech`
- **Submodule**: `libs_systemui` (`https://gitlab.e.foundation/e/os/android_frameworks_libs_systemui`)
- **Target Application ID**: `com.ihome.launcher`
- **Target Component**: `com.ihome.launcher/com.android.launcher3.Launcher`
- **Primary Build Variant**: `blissWithoutQuickstepDebug`

---

## 2. Windows Prerequisites Checklist

Before opening the project in Android Studio or Antigravity, ensure the following tools are installed on Windows:

| Dependency | Required Version | Download / Installation | Purpose |
| :--- | :--- | :--- | :--- |
| **Git for Windows** | 2.40+ | `winget install Git.Git` or [git-scm.com](https://git-scm.com/download/win) | Version control with long path support |
| **Java JDK** | **JDK 17** | `winget install EclipseAdoptium.Temurin.17.JDK` or Android Studio's bundled JBR | Required by Gradle 8.9 & AGP 8.7.3 (`compileOptions`) |
| **Android Studio** | Ladybug / Koala / latest | [developer.android.com/studio](https://developer.android.com/studio) | SDK, emulator, and tooling manager |
| **Android SDK** | **API 36** (`compileSdk 36`) | Installed via Android Studio SDK Manager | Required compilation SDK |
| **Build-Tools** | 35.0.0 or 36.0.0 | Installed via Android Studio SDK Manager | D8 / AAPT2 |
| **Platform-Tools** | Latest | Installed via SDK Manager | Provides `adb.exe` |
| **Python** | Python 3.10+ | `winget install Python.Python.3.11` (Check *"Add python.exe to PATH"*) | Runs `scripts/update_launcher_logic_md.py` |
| **OEM USB Driver** | Specific to test phone | Google USB Driver (SDK Manager) or Realme/Samsung/OnePlus OEM driver | Enables `adb devices` over USB on Windows |

---

## 3. Git & Environment Setup (Run Once on Windows)

### Step 3.1: Configure Git for Long Paths & Line Endings
Open **PowerShell** or **Command Prompt as Administrator** and execute:
```cmd
:: Enable NTFS long file path support (prevents MAX_PATH 260-char clone/build errors)
git config --system core.longpaths true

:: Keep LF line endings (prevents CRLF translation from corrupting shell/python scripts)
git config --global core.autocrlf false
```

### Step 3.2: Set Windows Environment Variables
Add or verify the following System / User Environment Variables:
- **`JAVA_HOME`**: Points to JDK 17 (e.g. `C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot` or `C:\Program Files\Android\Android Studio\jbr`)
- **`ANDROID_HOME`**: `C:\Users\<YourUsername>\AppData\Local\Android\Sdk`
- **`Path`**: Append:
  - `%JAVA_HOME%\bin`
  - `%ANDROID_HOME%\platform-tools`
  - `%ANDROID_HOME%\cmdline-tools\latest\bin`

---

## 4. Cloning the Project & Initializing Submodules

Open terminal in your desired workspace folder (e.g. `C:\Users\<YourUsername>\Documents\Projects`):

```cmd
:: Clone with submodule recursion directly on argon branch
git clone -b argon --recurse-submodules https://github.com/play4tech/OS_Launcher.git iOSLauncher
cd iOSLauncher
```

> [!IMPORTANT]
> If already cloned without submodules, initialize them immediately:
> ```cmd
> git submodule update --init --recursive
> ```

---

## 5. Applying the `libs_systemui` Patch (Mandatory)

The `libs_systemui` submodule contains custom icon specular highlight rendering, notification dot alignment, and icon cache database versioning. Because the upstream submodule resides on `/e/OS` GitLab, these local changes are captured in `patches/libs_systemui.patch`.

Run the Windows batch script to apply the patch:
```cmd
scripts\apply_patches.bat
```

Verify that the patch applied cleanly:
```cmd
git -C libs_systemui status
```
*(You should see modified files in `iconloaderlib` ready and active).*

---

## 6. Configuring `local.properties`

Create a file named `local.properties` in the project root (`iOSLauncher\local.properties`):

```properties
sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
```
*(Replace `<YourUsername>` with your Windows user profile name. Note the escaped colons and double backslashes, or use forward slashes `C:/Users/<YourUsername>/AppData/Local/Android/Sdk`).*

---

## 7. Building & Testing (Commands & Rules)

### 7.1. Rule: Target Build Variant (`withoutQuickstep`)
Always build the **`withoutQuickstep`** variant for retail OEM devices, emulators, and Play Store compliance:

```cmd
:: Build Debug APK
gradlew.bat assembleBlissWithoutQuickstepDebug

:: Or Build Release APK
gradlew.bat assembleBlissWithoutQuickstepRelease
```

> [!WARNING]
> Do NOT use `withQuickstep` unless building for a custom system ROM in `/system/priv-app`. Non-rooted retail devices (Realme UI, One UI, ColorOS) reject 3rd-party Quickstep recents providers.

### 7.2. Installing & Launching
To install the freshly compiled APK onto a connected device or emulator:
```cmd
adb install -r build\outputs\apk\blissWithoutQuickstep\debug\BlissLauncher-api36-debug.apk
```

### 7.3. Rule: Explicit Launcher Start for Verification
Never rely on `adb shell input keyevent KEYCODE_HOME` alone to test changes. `KEYCODE_HOME` may launch the OEM's default launcher rather than iHome.

**Always explicitly launch iHome**:
```cmd
adb shell am start -n com.ihome.launcher/com.android.launcher3.Launcher
```

To capture screenshots:
```cmd
adb exec-out screencap -p > screenshot.png
```

---

## 8. Architectural Invariants (Zero-Regression Contract)

When modifying code in Antigravity on Windows, enforce these non-negotiable rules:

1. **Folder Open State Invariant (`Folder.getOpen(mLauncher) != null`)**:
   - While any folder is open, workspace items (`ShortcutAndWidgetContainer`), dock icons (`Hotseat`), dock glass (`dock_glass`), and page indicator / search pill MUST have `alpha = 0f`.
   - Blur layer (`mBlurLayer`) MUST remain `alpha = 1f`.
   - Never bypass folder suppression using `DragController.isDragging()` (as that is true during internal folder reordering as well).
2. **Drag Exit to Workspace Invariant**:
   - On drag exit from folder to workspace, folder closes immediately and workspace items, dock icons, dock glass, and page indicator MUST restore to `alpha = 1f` mid-drag before drop.
3. **Ghost / Orphan Icon Prevention (`ShortcutAndWidgetContainer.java`)**:
   - Every `removeView()`, `removeViewInLayout()`, `removeViewAt()`, and `removeAllViews()` call MUST call `view.clearAnimation()` before removal to prevent disappearing children rendering loops in jiggle mode.
4. **Sensor Highlight IPC Flood Guard (`PortraitSensorHighlightController.kt`)**:
   - Never unregister sensor listeners inside high-frequency sensor callbacks (~60Hz) without an `isRegistered` guard to prevent Binder IPC logcat floods.
5. **App Removal Flow & Contrast**:
   - Destructive shortcut label must be `"Remove App"` in `#FF3B30` red.
   - User apps confirm with *"Uninstall"* (red) or *"Remove from Home Screen"* (blue, keeping in App Library).
   - Change Category row labels must render in solid `#000000` black for high contrast against glass.

---

## 9. Rule: Mandatory Synchronization of `launcher_important_logic.md`

Every time the codebase is updated, refactored, or new features are added:
```cmd
python scripts\update_launcher_logic_md.py
```
This script updates `launcher_important_logic.md`, `launcher_important_logic.txt`, and the modular bundles in `launcher_logic_parts/` so AI models have synchronized context.

---

## 10. Antigravity Quick Start Command Summary

When opening this workspace in Antigravity on Windows, execute these verification commands in the terminal:

```cmd
:: 1. Verify submodule patch is present
git -C libs_systemui status

:: 2. If not applied, apply patch
scripts\apply_patches.bat

:: 3. Test clean build
gradlew.bat assembleBlissWithoutQuickstepDebug

:: 4. Verify ADB connection
adb devices

:: 5. Install & run
adb install -r build\outputs\apk\blissWithoutQuickstep\debug\BlissLauncher-api36-debug.apk
adb shell am start -n com.ihome.launcher/com.android.launcher3.Launcher
```
