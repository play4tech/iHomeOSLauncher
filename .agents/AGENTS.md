# Rule: Real Device Screenshot Verification

When taking screenshots on an emulator or real device to verify UI changes in Bliss Launcher, do not rely on `adb shell input keyevent KEYCODE_HOME` alone. `KEYCODE_HOME` may launch the device's default launcher (e.g., Nexus Launcher) instead of the freshly compiled Bliss Launcher, resulting in inaccurate screenshots of the wrong launcher.

**Always explicitly open the Bliss Launcher before taking a screenshot** by using:
```bash
adb shell am start -n foundation.e.blisslauncher/com.android.launcher3.uioverrides.QuickstepLauncher
```
After running this command, wait a few seconds (e.g. `sleep 2`) to ensure the launcher has fully rendered before running `adb exec-out screencap -p`.
