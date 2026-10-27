import subprocess
import time
import os

def adb(cmd):
    return subprocess.run(f"adb shell \"{cmd}\"", shell=True, capture_output=True, text=True)

def screencap(dest):
    subprocess.run("adb shell screencap -p /sdcard/screen.png", shell=True)
    subprocess.run(f"adb pull /sdcard/screen.png {dest}", shell=True)

def test_scenario_2():
    print("Testing Scenario 2: Rearrange Inside Folder")
    # Wake up
    adb("input keyevent KEYCODE_WAKEUP")
    
    # Drag from WhatsApp (276, 915) to LinkedIn (779, 912)
    x1, y1 = 276, 915
    x2, y2 = 779, 912
    
    # 1. DOWN
    adb(f"input motionevent DOWN {x1} {y1}")
    time.sleep(1.0) # Long press duration
    
    # 2. MOVE towards target
    steps = 15
    for i in range(1, steps + 1):
        x = int(x1 + (x2 - x1) * (i / steps))
        y = int(y1 + (y2 - y1) * (i / steps))
        adb(f"input motionevent MOVE {x} {y}")
        time.sleep(0.05)
    
    # Mid-drag capture
    time.sleep(0.5)
    screencap("test_results/folder_scenario2_mid_drag.png")
    
    # 3. UP (drop)
    adb(f"input motionevent UP {x2} {y2}")
    time.sleep(1.0)
    
    # Post-drop capture
    screencap("test_results/folder_scenario2_post_drop.png")
    print("Scenario 2 test completed.")

if __name__ == "__main__":
    test_scenario_2()
