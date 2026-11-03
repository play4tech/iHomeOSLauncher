import time
import subprocess
import uiautomator2 as u2

SERIAL = "693a9b29"
d = u2.connect(SERIAL)

print("Starting Scenario Verification Suite on device:", SERIAL)

# Ensure screen is on and unlocked
d.screen_on()
subprocess.run(["adb", "-s", SERIAL, "shell", "wm", "dismiss-keyguard"])
subprocess.run(["adb", "-s", SERIAL, "shell", "am", "start", "-n", "com.ihomeos.launcher/com.android.launcher3.Launcher"])
time.sleep(1.5)

# -------------------------------------------------------------
# TEST 1: Page 0 Dynamic Drag & Real-time Displacement
# -------------------------------------------------------------
print("\n=== TEST 1: Page 0 Dynamic Drag ===")
# Ensure we are on Page 0 by swiping right
for _ in range(3):
    d.swipe(100, 1000, 900, 1000, 0.15)
    time.sleep(0.3)

subprocess.run(["adb", "-s", SERIAL, "logcat", "-c"])

# Find elements
play_store = d(text="Play Store")
maps = d(text="Maps")
assert play_store.exists(), "Play Store not found on Page 0!"
assert maps.exists(), "Maps not found on Page 0!"

p_b = play_store.info['bounds']
m_b = maps.info['bounds']
p_x, p_y = (p_b['left'] + p_b['right']) // 2, (p_b['top'] + p_b['bottom']) // 2
m_x, m_y = (m_b['left'] + m_b['right']) // 2, (m_b['top'] + m_b['bottom']) // 2

print(f"Dragging Play Store ({p_x}, {p_y}) towards Maps ({m_x}, {m_y})...")
d.touch.down(p_x, p_y)
time.sleep(0.7) # initiate drag

# Move in 8 steps towards Maps
STEPS = 8
for i in range(1, STEPS + 1):
    cx = int(p_x + (m_x - p_x) * (i / STEPS))
    cy = int(p_y + (m_y - p_y) * (i / STEPS))
    d.touch.move(cx, cy)
    time.sleep(0.06)
    if i == STEPS - 2:
        d.screenshot("test_results/test1_dynamic_motion.png")
        print("Captured screenshot during active motion!")

time.sleep(0.3)
d.screenshot("test_results/test1_hover_hold.png")
print("Captured screenshot during hover hold!")

d.touch.up(m_x, m_y)
time.sleep(0.5)
d.screenshot("test_results/test1_after_drop.png")
print("Captured screenshot after drop!")

# Inspect logs for Test 1
res = subprocess.run(
    ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"],
    capture_output=True, text=True, errors="replace"
)
test1_logs = [l for l in res.stdout.splitlines() if any(k in l for k in ["animateChildToPosition", "CellLayout.performReorder", "DirectionVector", "scheduleReorder"])]
print(f"Test 1 logged {len(test1_logs)} reorder events:")
for l in test1_logs[:10]:
    print(" ", l)

# -------------------------------------------------------------
# TEST 2: Page 1 (100% Full Screen: 24 items) Dynamic Reorder
# -------------------------------------------------------------
print("\n=== TEST 2: Page 1 Full-Screen (24 items) Reorder ===")
# Swipe left to Page 1
d.swipe(900, 1000, 100, 1000, 0.2)
time.sleep(1.0)

subprocess.run(["adb", "-s", SERIAL, "logcat", "-c"])

claude = d(text="Claude")
indus = d(text="Indus")
assert claude.exists(), "Claude not found on Page 1!"
assert indus.exists(), "Indus not found on Page 1!"

c_b = claude.info['bounds']
i_b = indus.info['bounds']
c_x, c_y = (c_b['left'] + c_b['right']) // 2, (c_b['top'] + c_b['bottom']) // 2
i_x, i_y = (i_b['left'] + i_b['right']) // 2, (i_b['top'] + i_b['bottom']) // 2

print(f"Dragging Claude ({c_x}, {c_y}) over Indus ({i_x}, {i_y}) on 100% full screen...")
d.touch.down(c_x, c_y)
time.sleep(0.7)

for step in range(1, 6):
    cur_x = int(c_x + (i_x - c_x) * (step / 5))
    cur_y = int(c_y + (i_y - c_y) * (step / 5))
    d.touch.move(cur_x, cur_y)
    time.sleep(0.06)

time.sleep(0.3)
d.screenshot("test_results/test2_full_page_hover.png")
print("Captured Test 2 full page hover screenshot!")

d.touch.up(i_x, i_y)
time.sleep(0.5)
d.screenshot("test_results/test2_full_page_after_drop.png")
print("Captured Test 2 after drop screenshot!")

res2 = subprocess.run(
    ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"],
    capture_output=True, text=True, errors="replace"
)
test2_logs = [l for l in res2.stdout.splitlines() if any(k in l for k in ["animateChildToPosition", "CellLayout.performReorder", "commitTempPlacement"])]
print(f"Test 2 logged {len(test2_logs)} reorder events:")
for l in test2_logs[:10]:
    print(" ", l)

print("\nAll scenario tests executed successfully!")
