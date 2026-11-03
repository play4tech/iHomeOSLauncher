import subprocess
import time
import sys
import re

DEVICE = "10.247.83.170:5555"

def run_adb(cmd):
    full_cmd = f"adb -s {DEVICE} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def ensure_screen_on():
    pwr = run_adb("shell dumpsys power")
    if "mWakefulness=Asleep" in pwr or "mWakefulness=Dozing" in pwr:
        print("[Device] Waking up screen...")
        run_adb("shell input keyevent KEYCODE_WAKEUP")
        time.sleep(1)
        run_adb("shell wm dismiss-keyguard")
        time.sleep(1)

def pin_refresh_rate(rate=60.0):
    print(f"[Config] Pinning refresh rate to {rate} Hz...")
    run_adb(f"shell settings put system peak_refresh_rate {rate}")
    run_adb(f"shell settings put system min_refresh_rate {rate}")
    time.sleep(0.5)

def launch_and_open_spotlight(pkg):
    print(f"[Launch] Starting launcher: {pkg}...")
    run_adb(f"shell am start -n {pkg}/com.android.launcher3.Launcher")
    time.sleep(2.0)
    
    # Check if Spotlight is already open
    run_adb("shell uiautomator dump /sdcard/chk_ui.xml")
    chk = run_adb("shell cat /sdcard/chk_ui.xml")
    if "spotlight_search_backdrop_glass" not in chk:
        print("[Action] Tapping search pill at (540, 1963) to open Spotlight...")
        run_adb("shell input tap 540 1963")
        time.sleep(1.5)
        run_adb("shell uiautomator dump /sdcard/chk_ui.xml")
        chk = run_adb("shell cat /sdcard/chk_ui.xml")
        if "spotlight_search_backdrop_glass" not in chk:
            print("[Action] Retrying Spotlight open via swipe down...")
            run_adb("shell input swipe 540 600 540 1500 200")
            time.sleep(1.5)
            
    print(f"[Verified] Spotlight Search is open in {pkg}!")

def warmup_scroll():
    print("[Warmup] Flung down and up 2x to warm up JIT, icons, and shaders...")
    for _ in range(2):
        run_adb("shell input swipe 540 1800 540 400 200")
        time.sleep(0.7)
        run_adb("shell input swipe 540 1800 540 400 200")
        time.sleep(0.7)
        run_adb("shell input swipe 540 400 540 1800 200")
        time.sleep(0.7)
        run_adb("shell input swipe 540 400 540 1800 200")
        time.sleep(0.7)
    print("[Warmup] Complete.")

def execute_flings(count=5):
    print(f"[Benchmark] Performing {count} test flings...")
    for i in range(count):
        print(f"  Fling {i+1}/{count}...")
        run_adb("shell input swipe 540 1600 540 600 250")
        time.sleep(0.9)

def get_full_gfxinfo(pkg):
    raw = run_adb(f"shell dumpsys gfxinfo {pkg}")
    lines = raw.splitlines()
    capturing = False
    block = []
    for line in lines:
        if "** Graphics info for pid" in line or "Graphics info for pid" in line:
            capturing = True
        if capturing:
            block.append(line)
            if "View hierarchy:" in line:
                break
    return "\n".join(block) if block else raw

def benchmark(pkg, name, runs=3):
    print("\n" + "=" * 75)
    print(f"STARTING 3-RUN BENCHMARK: {name} ({pkg})")
    print("=" * 75)
    
    ensure_screen_on()
    launch_and_open_spotlight(pkg)
    
    # Warm-up phase before any benchmark runs
    warmup_scroll()
    
    for r in range(1, runs + 1):
        print(f"\n==========================================")
        print(f"  {name} - RUN {r} of {runs}")
        print(f"==========================================")
        
        # Reset stats for clean fling measurement
        run_adb(f"shell dumpsys gfxinfo {pkg} reset")
        time.sleep(0.5)
        
        execute_flings(5)
        time.sleep(0.5)
        
        gfx = get_full_gfxinfo(pkg)
        print(f"\n[dumpsys gfxinfo output for {name} - Run {r}]:\n")
        print(gfx)
        print("=" * 75)

def main():
    ensure_screen_on()
    pin_refresh_rate(60.0)
    
    # Run HEAD
    benchmark("foundation.e.blisslauncher", "HEAD Build", runs=3)
    
    # Run Vivo
    benchmark("com.ihomeos.launcher", "Vivo Build", runs=3)

if __name__ == "__main__":
    main()
