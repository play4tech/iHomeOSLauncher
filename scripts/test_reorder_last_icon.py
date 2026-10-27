import time
import subprocess
import uiautomator2 as u2

SERIAL = "693a9b29"
d = u2.connect(SERIAL)
print("Device connected. Screen state:", d.info.get("screenOn"))

# Wake device and launch Launcher
subprocess.run(["adb", "-s", SERIAL, "shell", "input", "keyevent", "KEYCODE_WAKEUP"])
subprocess.run(["adb", "-s", SERIAL, "shell", "wm", "dismiss-keyguard"])
subprocess.run(["adb", "-s", SERIAL, "shell", "am", "start", "-n", "com.ihome.launcher/com.android.launcher3.Launcher"])
time.sleep(1.0)

x_icon = d(text="X")
arattai_icon = d(textStartsWith="Arattai")

print("X exists:", x_icon.exists())
print("Arattai exists:", arattai_icon.exists())

assert x_icon.exists(), "X not found!"
assert arattai_icon.exists(), "Arattai not found!"

x_b = x_icon.info['bounds']
a_b = arattai_icon.info['bounds']

x_center = ((x_b['left'] + x_b['right']) // 2, (x_b['top'] + x_b['bottom']) // 2)
a_center = ((a_b['left'] + a_b['right']) // 2, (a_b['top'] + a_b['bottom']) // 2)

print(f"X at {x_center}, Arattai at {a_center}")

# Clear logcat
subprocess.run(["adb", "-s", SERIAL, "logcat", "-c"])

print("Starting long press on X...")
d.touch.down(x_center[0], x_center[1])
time.sleep(0.8) # long press to start drag

# Drag towards Arattai in steps
target_x = a_center[0]
target_y = a_center[1]

STEPS = 8
for i in range(1, STEPS + 1):
    cx = int(x_center[0] + (target_x - x_center[0]) * (i / STEPS))
    cy = int(x_center[1] + (target_y - x_center[1]) * (i / STEPS))
    d.touch.move(cx, cy)
    time.sleep(0.05)
    if i == STEPS // 2:
        print(f"Midway at ({cx}, {cy})")

print(f"Holding near/at target ({target_x}, {target_y}) for 400ms to allow reorder displacement...")
time.sleep(0.4)
d.screenshot("test_results/test_swap_hover.png")

print("Dropping at target...")
d.touch.up(target_x, target_y)
time.sleep(1.0)
d.screenshot("test_results/test_swap_dropped.png")
print("Finished drag.")

# Fetch logs
res = subprocess.run(
    ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"],
    capture_output=True, text=True, errors="replace"
)

keywords = ["CellLayout", "ReorderAlgorithm", "DragTrace", "Workspace.createUserFolder", "ReorderAlarmListener", "commitTempPlacement", "ReorderDebug"]
print("\n--- KEY LOGCAT LINES ---")
for line in res.stdout.splitlines():
    if any(k in line for k in keywords):
        print(line)
