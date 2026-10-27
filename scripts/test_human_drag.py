import time
import subprocess
import uiautomator2 as u2

SERIAL = "693a9b29"
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

print(f"My Files at ({m_x}, {m_y}) -> Play Store at ({p_x}, {p_y})")

# Clear logcat
subprocess.run(["adb", "-s", SERIAL, "logcat", "-c"])

print("Starting realistic human drag with intermediate touch moves...")
d.touch.down(m_x, m_y)
time.sleep(0.8) # long press to initiate drag

# Slowly move towards Play Store in 10 increments
STEPS = 10
for i in range(1, STEPS + 1):
    cur_x = int(m_x + (p_x - m_x) * (i / STEPS))
    cur_y = int(m_y + (p_y - m_y) * (i / STEPS))
    d.touch.move(cur_x, cur_y)
    time.sleep(0.08) # ~80ms per move, total ~800ms
    if i == STEPS // 2:
        d.screenshot("test_results/drag_midway.png")
        print("Captured midway screenshot at step", i)

print("Arrived at target Play Store. Holding for 500ms...")
time.sleep(0.5)
d.screenshot("test_results/drag_at_target.png")

d.touch.up(p_x, p_y)
time.sleep(0.5)
d.screenshot("test_results/drag_dropped.png")
print("Finished drag test.")

res = subprocess.run(
    ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"],
    capture_output=True, text=True, errors="replace"
)

log_lines = []
for line in res.stdout.splitlines():
    if any(k in line for k in ["DragTrace", "performReorder", "SpatialGrid", "animateChildToPosition", "scheduleReorder", "revertTempState", "manageFolderFeedback", "ReorderAlarm", "IconDragLogger"]):
        log_lines.append(line)

print(f"\n--- MATCHED {len(log_lines)} LOG LINES ---")
for l in log_lines:
    print(l)
