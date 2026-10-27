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
    p50 = 0
    p90 = 0
    p95 = 0
    
    m = re.search(r"Total frames rendered:\s+(\d+)", output)
    if m:
        total_frames = int(m.group(1))
    m = re.search(r"Janky frames:\s+(\d+)\s+\(([\d\.]+)%\)", output)
    janky_percent = float(m.group(2)) if m else 0.0
    if m:
        janky_frames = int(m.group(1))
        
    m = re.search(r"50th percentile:\s+(\d+)ms", output)
    if m:
        p50 = int(m.group(1))
    m = re.search(r"90th percentile:\s+(\d+)ms", output)
    if m:
        p90 = int(m.group(1))
    m = re.search(r"95th percentile:\s+(\d+)ms", output)
    if m:
        p95 = int(m.group(1))
        
    return {
        "total": total_frames,
        "janky": janky_frames,
        "janky_pct": janky_percent,
        "p50": p50,
        "p90": p90,
        "p95": p95
    }

def run_applib_benchmark(pkg, activity):
    print(f"\n--- Testing App Library on {pkg} ---")
    adb(f"shell am start -n {pkg}/{activity}")
    time.sleep(2)
    
    # Go to home first
    adb("shell input keyevent KEYCODE_HOME")
    time.sleep(1)
    
    # Swipe left twice to reach App Library (assuming 1-2 home pages)
    adb("shell input swipe 900 1200 100 1200 200")
    time.sleep(0.5)
    adb("shell input swipe 900 1200 100 1200 200")
    time.sleep(1)
    
    # Reset gfxinfo
    adb(f"shell dumpsys gfxinfo {pkg} reset")
    time.sleep(0.5)
    
    # Perform 8 vertical flings
    for i in range(4):
        adb("shell input swipe 540 1800 540 600 150") # fling down
        time.sleep(0.6)
        adb("shell input swipe 540 600 540 1800 150") # fling up
        time.sleep(0.6)
        
    out = adb(f"shell dumpsys gfxinfo {pkg}")
    stats = parse_gfxinfo(out)
    print(f"App Library Results for {pkg}: {stats}")
    return stats

def run_spotlight_benchmark(pkg, activity):
    print(f"\n--- Testing Spotlight Search on {pkg} ---")
    adb(f"shell am start -n {pkg}/{activity}")
    time.sleep(1.5)
    
    # Go to home
    adb("shell input keyevent KEYCODE_HOME")
    time.sleep(1)
    
    # Pull down for Spotlight Search (middle swipe down)
    adb("shell input swipe 540 800 540 1600 250")
    time.sleep(1.5)
    
    # Dismiss soft keyboard so list scrolling is tested
    adb("shell input keyevent KEYCODE_BACK")
    time.sleep(1)
    
    # Reset gfxinfo
    adb(f"shell dumpsys gfxinfo {pkg} reset")
    time.sleep(0.5)
    
    # Perform 8 vertical flings on the search list
    for i in range(4):
        adb("shell input swipe 540 1500 540 700 150")
        time.sleep(0.6)
        adb("shell input swipe 540 700 540 1500 150")
        time.sleep(0.6)
        
    out = adb(f"shell dumpsys gfxinfo {pkg}")
    stats = parse_gfxinfo(out)
    print(f"Spotlight Results for {pkg}: {stats}")
    
    # Close spotlight
    adb("shell input keyevent KEYCODE_BACK")
    time.sleep(1)
    return stats

if __name__ == "__main__":
    vivo_pkg = "foundation.e.blisslauncher"
    head_pkg = "com.ihome.launcher"
    act = "com.android.launcher3.Launcher"
    
    print("=========================================")
    print("BENCHMARK 1: VIVO BUILD (foundation.e.blisslauncher)")
    print("=========================================")
    adb(f"shell cmd package set-home-activity {vivo_pkg}/{act}")
    v_applib = run_applib_benchmark(vivo_pkg, act)
    v_spot = run_spotlight_benchmark(vivo_pkg, act)
    
    print("\n=========================================")
    print("BENCHMARK 2: HEAD BUILD (com.ihome.launcher)")
    print("=========================================")
    adb(f"shell cmd package set-home-activity {head_pkg}/{act}")
    h_applib = run_applib_benchmark(head_pkg, act)
    h_spot = run_spotlight_benchmark(head_pkg, act)
    
    print("\n================ FINAL COMPARISON ================")
    print(f"{'Metric':<30} | {'Vivo Build':<20} | {'HEAD Build':<20}")
    print("-" * 75)
    print(f"{'AppLib Jank Rate':<30} | {v_applib['janky_pct']:.2f}%{'':<14} | {h_applib['janky_pct']:.2f}%")
    print(f"{'AppLib Median Latency (p50)':<30} | {v_applib['p50']}ms{'':<16} | {h_applib['p50']}ms")
    print(f"{'AppLib 90th Percentile (p90)':<30} | {v_applib['p90']}ms{'':<16} | {h_applib['p90']}ms")
    print("-" * 75)
    print(f"{'Spotlight Jank Rate':<30} | {v_spot['janky_pct']:.2f}%{'':<14} | {h_spot['janky_pct']:.2f}%")
    print(f"{'Spotlight Median Latency (p50)':<30} | {v_spot['p50']}ms{'':<16} | {h_spot['p50']}ms")
    print(f"{'Spotlight 90th Percentile (p90)':<30} | {v_spot['p90']}ms{'':<16} | {h_spot['p90']}ms")
    print("==================================================")
