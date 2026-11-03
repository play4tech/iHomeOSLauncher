import time
import subprocess
import uiautomator2 as u2

SERIAL = "693a9b29"
d = u2.connect(SERIAL)
print("Device connected. Screen state:", d.info.get("screenOn"))

# Wake device and launch Launcher
subprocess.run(["adb", "-s", SERIAL, "shell", "input", "keyevent", "KEYCODE_WAKEUP"])
subprocess.run(["adb", "-s", SERIAL, "shell", "am", "start", "-n", "com.ihomeos.launcher/com.android.launcher3.Launcher"])
time.sleep(1.0)

x_icon = d(text="X")
gmail_icon = d(text="Gmail")

print("X exists:", x_icon.exists())
print("Gmail exists:", gmail_icon.exists())

if not x_icon.exists() or not gmail_icon.exists():
    print("Could not find X or Gmail on screen!")
    exit(1)

x_b = x_icon.info['bounds']
g_b = gmail_icon.info['bounds']

x_center = ((x_b['left'] + x_b['right']) // 2, (x_b['top'] + x_b['bottom']) // 2)
g_center = ((g_b['left'] + g_b['right']) // 2, (g_b['top'] + g_b['bottom']) // 2)

print(f"X at {x_center}, Gmail at {g_center}")

# Clear logcat
subprocess.run(["adb", "-s", SERIAL, "logcat", "-c"])

print("Starting touch down on X...")
d.touch.down(x_center[0], x_center[1])
time.sleep(0.8) # long press to start drag

STEPS = 10
# Drag from X to Gmail's position
for i in range(1, STEPS + 1):
    cx = int(x_center[0] + (g_center[0] - x_center[0]) * (i / STEPS))
    cy = int(x_center[1] + (g_center[1] - x_center[1]) * (i / STEPS))
    d.touch.move(cx, cy)
    time.sleep(0.08)

print("Arrived at Gmail center. Holding for 1.2s to observe reorder/folder behavior...")
time.sleep(0.6)
d.screenshot("test_results/drag_hover_gmail.png")
time.sleep(0.6)

print("Dropping at Gmail position...")
d.touch.up(g_center[0], g_center[1])
time.sleep(1.0)
d.screenshot("test_results/drag_dropped_gmail.png")

# Fetch relevant logs
res = subprocess.run(
    ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"],
    capture_output=True, text=True, errors="replace"
)

print("\n--- RELEVANT LOGCAT OUTPUT ---")
keywords = ["CellLayout", "ReorderAlgorithm", "DragTrace", "Workspace", "createFolder", "performReorder", "commitTempPlacement"]
for line in res.stdout.splitlines():
    if any(k in line for k in keywords):
        print(line)
