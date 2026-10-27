"""
Device controller for Launcher3 testing framework.
Manages ADB interactions, uiautomator2 connection, and launcher state normalization.
"""

import time
import subprocess
import uiautomator2 as u2

PACKAGE = "com.ihome.launcher"
ACTIVITY = "com.android.launcher3.Launcher"

class LauncherDevice:
    def __init__(self, serial="emulator-5554"):
        self.serial = serial
        self.d = u2.connect(serial)
        try:
            self.width, self.height = self.d.window_size()
        except Exception:
            self.width, self.height = 720, 1600

    def adb(self, *args):
        cmd = ["adb", "-s", self.serial] + list(args)
        res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
        return res.stdout.strip()

    def ensure_launcher_ready(self):
        """Ensures the launcher is running in NORMAL state on Page 1 with no open overlays."""
        # 0. Wake screen and unlock keyguard if sleeping
        self.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        self.adb("shell", "wm", "dismiss-keyguard")
        self.adb("shell", "input", "swipe", str(self.width // 2), str(int(self.height * 0.75)), str(self.width // 2), str(int(self.height * 0.25)), "150")
        time.sleep(0.2)

        # 1. Bring activity to top
        self.adb("shell", "am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
        time.sleep(0.3)
        
        # 2. Dismiss Jiggle/Edit Mode ("Done" button) if present
        if self.d(text="Done").exists(timeout=0.2):
            self.d(text="Done").click()
            time.sleep(0.3)
            
        # 2b. Dismiss Onboarding wizard if present
        if self.d(resourceId=f"{PACKAGE}:id/onboarding_title").exists(timeout=0.2) or self.d(text="Show notification dots.").exists(timeout=0.2):
            self.d.press("back")
            time.sleep(0.3)
            self.d.press("back")
            time.sleep(0.3)

        # 2c. Dismiss system ANR dialog if present
        if self.d(resourceId="android:id/aerr_close").exists(timeout=0.2) or self.d(text="Close app").exists(timeout=0.2):
            try:
                if self.d(resourceId="android:id/aerr_close").exists():
                    self.d(resourceId="android:id/aerr_close").click()
                elif self.d(text="Close app").exists():
                    self.d(text="Close app").click()
            except Exception:
                pass
        # 2d. Dismiss any open PopupContainerWithArrow
        if self.d(text="Remove App").exists(timeout=0.1) or self.d(resourceId=f"{PACKAGE}:id/deep_shortcuts_container").exists(timeout=0.1):
            self.d.press("back")
            time.sleep(0.3)

        # 3. Press Home to dismiss floating overlays, folders, or sheets and snap to Page 0
        self.d.press("home")
        time.sleep(0.4)
        self.adb("shell", "am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
        time.sleep(0.3)

    def restore_clean_fixture(self):
        """Restores hermetic clean launcher database fixture via run-as without requiring root."""
        self.adb("shell", "am", "force-stop", PACKAGE)
        self.adb("shell", f"run-as {PACKAGE} cp /data/user/0/{PACKAGE}/databases/launcher_fixture.db /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db")
        self.adb("shell", f"run-as {PACKAGE} rm -f /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db-wal /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db-shm /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db-journal")
        self.adb("shell", f"run-as {PACKAGE} cp /data/user/0/{PACKAGE}/shared_prefs/prefs_fixture.xml /data/user/0/{PACKAGE}/shared_prefs/com.android.launcher3.prefs.xml")
        time.sleep(0.3)
        self.ensure_launcher_ready()
        time.sleep(1.5)

    def restore_folder_fixture(self):
        """Restores the multi-row, multi-page folder fixture DB on device."""
        self.adb("shell", "am", "force-stop", PACKAGE)
        self.adb("shell", f"run-as {PACKAGE} cp /data/user/0/{PACKAGE}/databases/launcher_folder_fixture.db /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db")
        self.adb("shell", f"run-as {PACKAGE} rm -f /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db-wal /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db-shm /data/user/0/{PACKAGE}/databases/launcher_4_by_6.db-journal")
        self.adb("shell", f"run-as {PACKAGE} cp /data/user/0/{PACKAGE}/shared_prefs/prefs_fixture.xml /data/user/0/{PACKAGE}/shared_prefs/com.android.launcher3.prefs.xml")
        time.sleep(0.3)
        self.ensure_launcher_ready()
        time.sleep(1.5)

    def drag_with_dwell(self, sx, sy, ex, ey, pickup_hold=0.50, dwell_hold=0.40, move_steps=12):
        """
        Executes a realistic drag with pickup hold (long-press) and arrival dwell hold.
        """
        sx, sy, ex, ey = int(sx), int(sy), int(ex), int(ey)
        move_steps = int(move_steps) if int(move_steps) >= 1 else 12

        # 1. Touch down at source
        self.d.touch.down(sx, sy)
        time.sleep(pickup_hold)  # Long-press pickup delay
        
        # 2. Smoothly step to destination
        for i in range(1, move_steps + 1):
            cur_x = int(sx + (ex - sx) * (i / move_steps))
            cur_y = int(sy + (ey - sy) * (i / move_steps))
            self.d.touch.move(cur_x, cur_y)
            time.sleep(0.02)
            
        # 3. Dwell at destination to let ReorderAlarm / FolderCreationAlarm fire
        if dwell_hold > 0:
            time.sleep(dwell_hold)
            
        # 4. Release touch
        self.d.touch.up(ex, ey)
        time.sleep(0.4)

    def drag(self, sx, sy, ex, ey, duration=0.8):
        """Standard drag delegating to drag_with_dwell."""
        self.drag_with_dwell(sx, sy, ex, ey, pickup_hold=0.45, dwell_hold=0.35)

    def click(self, x, y):
        self.d.click(x, y)
        time.sleep(0.3)

    def long_click(self, x, y, duration=0.8):
        self.d.long_click(x, y, duration=duration)
        time.sleep(0.4)

    def swipe(self, sx, sy, ex, ey, duration=0.3):
        self.d.swipe(sx, sy, ex, ey, duration=duration)
        time.sleep(0.4)

    def press_key(self, key):
        self.d.press(key)
        time.sleep(0.3)

    def input_text(self, text):
        self.adb("shell", "input", "text", text)
        time.sleep(0.3)

    def send_keyevent(self, keycode):
        self.adb("shell", "input", "keyevent", str(keycode))
        time.sleep(0.3)

    def is_folder_open(self) -> bool:
        """Determines if a Folder view is currently open and visible."""
        return bool(self.d(resourceId=f"{PACKAGE}:id/folder_content").exists(timeout=0.2)) or \
               bool(self.d(className="com.android.launcher3.folder.Folder").exists(timeout=0.2))

    def open_folder(self, x, y) -> bool:
        """Clicks to open a folder and returns whether the folder opened."""
        self.click(x, y)
        time.sleep(0.6)
        return self.is_folder_open()

    def close_folder_via_outside_tap(self) -> bool:
        """Taps the outside margin of the screen to close the open folder."""
        self.click(int(self.width * 0.5), int(self.height * 0.12))
        time.sleep(0.5)
        if self.is_folder_open():
            self.click(int(self.width * 0.5), int(self.height * 0.90))
            time.sleep(0.5)
        return not self.is_folder_open()
