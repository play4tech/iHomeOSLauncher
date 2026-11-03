import subprocess
import time

def run_adb(cmd):
    return subprocess.run(f"adb {cmd}", shell=True, capture_output=True, text=True).stdout.strip()

print("=== STARTING POD REVEAL & OVERSCROLL AUDIT ===")

# 1. Clear logcat
run_adb("logcat -c")

# 2. Open App Library
run_adb("shell am start -n com.ihomeos.launcher/com.android.launcher3.Launcher")
time.sleep(0.5)
# Go to App Library (page 2)
for _ in range(4):
    run_adb("shell input swipe 200 1000 850 1000 120")
    time.sleep(0.15)
run_adb("shell input swipe 850 1000 200 1000 120")
time.sleep(0.3)
run_adb("shell input swipe 850 1000 200 1000 120")
time.sleep(0.6)

print("In App Library. Starting 10 Fast Flings (Up & Down)...")
for i in range(10):
    # Fling down
    run_adb("shell input swipe 540 1800 540 400 100")
    time.sleep(0.3)
    # Fling up
    run_adb("shell input swipe 540 400 540 1800 100")
    time.sleep(0.3)

print("Starting 5 Heavy Rubber-Band Overscroll Pulls & Rebounds...")
for i in range(5):
    # Pull down at top to trigger top rubber band
    run_adb("shell input swipe 540 400 540 1600 500")
    time.sleep(0.4)
    # Scroll to bottom
    run_adb("shell input swipe 540 1800 540 300 200")
    time.sleep(0.3)
    # Pull up at bottom to trigger bottom rubber band
    run_adb("shell input swipe 540 1600 540 300 500")
    time.sleep(0.4)
    # Scroll back to top
    run_adb("shell input swipe 540 400 540 1900 200")
    time.sleep(0.4)

# 3. Check logcat
print("\nChecking logcat for GLASS_REVEAL_AUDIT violations...")
logs = run_adb("logcat -d -s GLASS_REVEAL_AUDIT:W")
lines = [l for l in logs.splitlines() if "GLASS_REVEAL_AUDIT" in l]

print(f"Total GLASS_REVEAL_AUDIT violations logged: {len(lines)}")
if lines:
    for l in lines[:10]:
        print("  Violation:", l)
else:
    print("  PERFECT: ZERO violations! Not a single pod was ever drawn while glass was INVISIBLE during flings or overscroll!")
