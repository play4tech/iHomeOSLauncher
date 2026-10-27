import time
import subprocess
import uiautomator2 as u2

SERIAL = "693a9b29"
PACKAGE = "com.ihome.launcher"
ACTIVITY = "com.android.launcher3.Launcher"

d = u2.connect(SERIAL)
print("Device connected. Screen state:", d.info.get("screenOn"))

# Find Play Store and My Files
play_store = d(text="Play Store")
my_files = d(text="My Files")

assert play_store.exists(), "Play Store not found!"
assert my_files.exists(), "My Files not found!"

p_b = play_store.info['bounds']
m_b = my_files.info['bounds']

p_x = (p_b['left'] + p_b['right']) // 2
p_y = (p_b['top'] + p_b['bottom']) // 2

m_x = (m_b['left'] + m_b['right']) // 2
m_y = (m_b['top'] + m_b['bottom']) // 2

print(f"Play Store at ({p_x}, {p_y})")
print(f"My Files at ({m_x}, {m_y})")

# Clear logcat
subprocess.run(["adb", "-s", SERIAL, "logcat", "-c"])

print("Starting drag: My Files -> Play Store...")
d.touch.down(m_x, m_y)
time.sleep(0.8) # long press
d.touch.move(m_x - 30, m_y) # trigger drag
time.sleep(0.2)
d.touch.move(p_x, p_y) # move over Play Store

time.sleep(0.4)
d.screenshot("test_results/live_drag_0_4s.png")
print("Captured screenshot at 0.4s (during hover)")

time.sleep(0.6)
d.screenshot("test_results/live_drag_1_0s.png")
print("Captured screenshot at 1.0s (during hover)")

d.touch.up(p_x, p_y)
time.sleep(0.5)
d.screenshot("test_results/live_drag_after_drop.png")
print("Captured screenshot after drop")

res = subprocess.run(
    ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"],
    capture_output=True, text=True, errors="replace"
)

log_lines = []
for line in res.stdout.splitlines():
    if any(k in line for k in ["DragTrace", "performReorder", "SpatialGrid", "animateChildToPosition", "scheduleReorder", "revertTempState", "manageFolderFeedback", "ReorderAlarm"]):
        log_lines.append(line)

print(f"\n--- MATCHED {len(log_lines)} LOG LINES ---")
for l in log_lines:
    print(l)
