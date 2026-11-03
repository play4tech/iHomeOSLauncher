import subprocess
import time
import re

DEVICE = "693a9b29"

def adb(cmd):
    full_cmd = f"adb -s {DEVICE} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def parse_gfxinfo(output):
    total_frames = 0
    janky_frames = 0
    janky_pct = 0.0
    p50 = 0
    p90 = 0
    p95 = 0
    gpu50 = 0
    
    m = re.search(r"Total frames rendered:\s+(\d+)", output)
    if m:
        total_frames = int(m.group(1))
    m = re.search(r"Janky frames:\s+(\d+)\s+\(([\d\.]+)%\)", output)
    if m:
        janky_frames = int(m.group(1))
        janky_pct = float(m.group(2))
        
    m = re.search(r"50th percentile:\s+(\d+)ms", output)
    if m:
        p50 = int(m.group(1))
    m = re.search(r"90th percentile:\s+(\d+)ms", output)
    if m:
        p90 = int(m.group(1))
    m = re.search(r"95th percentile:\s+(\d+)ms", output)
    if m:
        p95 = int(m.group(1))
    m = re.search(r"50th gpu percentile:\s+(\d+)ms", output)
    if m:
        gpu50 = int(m.group(1))
        
    return {
        "total": total_frames,
        "janky": janky_frames,
        "janky_pct": janky_pct,
        "p50": p50,
        "p90": p90,
        "p95": p95,
        "gpu50": gpu50
    }

def run_suite(pkg):
    act = "com.android.launcher3.Launcher"
    print(f"\n=======================================================")
    print(f" TESTING: {pkg}")
    print(f"=======================================================")
    
    # 1. Set home default & bring to front
    adb(f"shell cmd package set-home-activity {pkg}/{act}")
    adb(f"shell am start -n {pkg}/{act}")
    time.sleep(1.5)
    adb("shell input keyevent KEYCODE_HOME")
    time.sleep(1.0)
    
    # 2. Go to App Library (swipe left 4 times to ensure reaching the rightmost page)
    for _ in range(4):
        adb("shell input swipe 950 1200 50 1200 150")
        time.sleep(0.3)
    time.sleep(1.0)
    
    # Reset gfxinfo
    adb(f"shell dumpsys gfxinfo {pkg} reset")
    time.sleep(0.3)
    
    # Perform 8 vertical flings on App Library
    for _ in range(4):
        adb("shell input swipe 540 1800 540 600 150")
        time.sleep(0.55)
        adb("shell input swipe 540 600 540 1800 150")
        time.sleep(0.55)
        
    applib_raw = adb(f"shell dumpsys gfxinfo {pkg}")
    applib_stats = parse_gfxinfo(applib_raw)
    print(f"App Library -> Frames: {applib_stats['total']}, Jank: {applib_stats['janky_pct']:.2f}%, p50: {applib_stats['p50']}ms, p90: {applib_stats['p90']}ms, GPU: {applib_stats['gpu50']}ms")
    
    # 3. Test Spotlight Search
    adb("shell input keyevent KEYCODE_HOME")
    time.sleep(1.0)
    
    # Tap search pill to open Spotlight
    adb("shell input tap 540 1960")
    time.sleep(1.5)
    
    adb(f"shell dumpsys gfxinfo {pkg} reset")
    time.sleep(0.3)
    
    # Fling list between y=1100 and y=400 (above keyboard area)
    for _ in range(4):
        adb("shell input swipe 540 1100 540 400 150")
        time.sleep(0.55)
        adb("shell input swipe 540 400 540 1100 150")
        time.sleep(0.55)
        
    spot_raw = adb(f"shell dumpsys gfxinfo {pkg}")
    spot_stats = parse_gfxinfo(spot_raw)
    print(f"Spotlight   -> Frames: {spot_stats['total']}, Jank: {spot_stats['janky_pct']:.2f}%, p50: {spot_stats['p50']}ms, p90: {spot_stats['p90']}ms")
    
    # Return to home
    adb("shell input keyevent KEYCODE_HOME")
    time.sleep(0.5)
    
    return applib_stats, spot_stats

if __name__ == "__main__":
    v_applib, v_spot = run_suite("foundation.e.blisslauncher")
    h_applib, h_spot = run_suite("com.ihomeos.launcher")
    
    print("\n" + "=" * 80)
    print("                      OFFICIAL HEAD vs VIVO BENCHMARK                      ")
    print("=" * 80)
    print(f"{'Test Scenario':<25} | {'Metric':<18} | {'Vivo Build':<15} | {'HEAD Build':<15}")
    print("-" * 80)
    print(f"{'App Library Fling':<25} | {'Jank Rate':<18} | {v_applib['janky_pct']:.2f}%{'':<9} | {h_applib['janky_pct']:.2f}%")
    print(f"{'App Library Fling':<25} | {'50th % (p50)':<18} | {v_applib['p50']}ms{'':<11} | {h_applib['p50']}ms")
    print(f"{'App Library Fling':<25} | {'90th % (p90)':<18} | {v_applib['p90']}ms{'':<11} | {h_applib['p90']}ms")
    print(f"{'App Library Fling':<25} | {'Total Frames':<18} | {v_applib['total']}{'':<13} | {h_applib['total']}")
    print("-" * 80)
    print(f"{'Spotlight Search Fling':<25} | {'Jank Rate':<18} | {v_spot['janky_pct']:.2f}%{'':<9} | {h_spot['janky_pct']:.2f}%")
    print(f"{'Spotlight Search Fling':<25} | {'50th % (p50)':<18} | {v_spot['p50']}ms{'':<11} | {h_spot['p50']}ms")
    print(f"{'Spotlight Search Fling':<25} | {'90th % (p90)':<18} | {v_spot['p90']}ms{'':<11} | {h_spot['p90']}ms")
    print(f"{'Spotlight Search Fling':<25} | {'Total Frames':<18} | {v_spot['total']}{'':<13} | {h_spot['total']}")
    print("=" * 80)
