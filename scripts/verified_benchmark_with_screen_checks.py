import subprocess
import time
import re
import os
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(line_buffering=True)

DEVICE = "693a9b29"
ARTIFACTS_DIR = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2"

def adb(cmd):
    full_cmd = f"adb -s {DEVICE} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def identify_current_screen():
    adb("shell uiautomator dump /sdcard/cur_ui.xml")
    local_path = os.path.join(ARTIFACTS_DIR, "cur_ui.xml")
    adb(f"pull /sdcard/cur_ui.xml \"{local_path}\"")
    if not os.path.exists(local_path):
        return "Unknown"
    try:
        tree = ET.parse(local_path)
    except Exception:
        return "Unknown"
        
    for el in tree.iter():
        rid = el.get("resource-id", "")
        if "spotlight_search_backdrop_glass" in rid or "spotlight_search_results" in rid:
            return "Spotlight Search"
        if "app_library_dashboard" in rid:
            bounds = el.get("bounds", "")
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
            if m and int(m.group(1)) < 500 and int(m.group(3)) > 500:
                return "App Library"

    for el in tree.iter():
        rid = el.get("resource-id", "")
        if "hotseat" in rid or "workspace" in rid or "page_indicator" in rid:
            return "Home Workspace"
            
    return "Other / Unknown"

def capture_screen(name):
    adb(f"shell screencap -p /sdcard/{name}")
    local_path = os.path.join(ARTIFACTS_DIR, name)
    adb(f"pull /sdcard/{name} \"{local_path}\"")
    print(f"  [Screen Verified] Captured {name}")
    return local_path

def parse_gfxinfo(output):
    blocks = re.findall(r'\*\* Graphics info for pid \d+.*?(?=(?:\*\* Graphics info for pid|\Z))', output, re.DOTALL)
    valid_blocks = [
        b for b in blocks 
        if (m := re.search(r'Total frames rendered:\s+(\d+)', b)) and int(m.group(1)) > 0
    ]
    block = valid_blocks[-1] if valid_blocks else (blocks[-1] if blocks else output)
    
    total = int(m.group(1)) if (m := re.search(r'Total frames rendered:\s+(\d+)', block)) else 0
    janky = int(m.group(1)) if (m := re.search(r'Janky frames:\s+(\d+)', block)) else 0
    janky_pct = float(m.group(1)) if (m := re.search(r'Janky frames:\s+\d+\s+\(([\d\.]+)%\)', block)) else 0.0
    p50 = int(m.group(1)) if (m := re.search(r'50th percentile:\s+(\d+)ms', block)) else 0
    p90 = int(m.group(1)) if (m := re.search(r'90th percentile:\s+(\d+)ms', block)) else 0
    p95 = int(m.group(1)) if (m := re.search(r'95th percentile:\s+(\d+)ms', block)) else 0
    gpu50 = int(m.group(1)) if (m := re.search(r'50th gpu percentile:\s+(\d+)ms', block)) else 0
    gpu90 = int(m.group(1)) if (m := re.search(r'90th gpu percentile:\s+(\d+)ms', block)) else 0
    
    return {
        'total': total,
        'janky': janky,
        'janky_pct': janky_pct,
        'p50': p50,
        'p90': p90,
        'p95': p95,
        'gpu50': gpu50,
        'gpu90': gpu90
    }

def run_launcher_bench(pkg, other_pkg):
    act = "com.android.launcher3.Launcher"
    print(f"\n" + "=" * 60)
    print(f" STARTING VERIFIED BENCHMARK: {pkg}")
    print("=" * 60)
    
    # 0. Clean process isolation: force stop competitor AND start target cleanly
    print(f"Stopping competing package {other_pkg}...")
    adb(f"shell am force-stop {other_pkg}")
    time.sleep(0.5)
    
    print(f"Starting {pkg} cleanly as single HOME instance...")
    adb(f"shell am force-stop {pkg}")
    time.sleep(0.5)
    adb(f"shell cmd package set-home-activity {pkg}/{act}")
    adb(f"shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n {pkg}/{act}")
    adb("shell cmd statusbar collapse")
    time.sleep(2.0)
    adb("shell input keyevent KEYCODE_HOME")
    time.sleep(1.5)
    
    # 1. VERIFY INITIAL SCREEN
    init_screen = identify_current_screen()
    print(f"\n[Check 1] Screen before starting test: {init_screen}")
    capture_screen(f"{pkg}_1_initial.png")
    
    # 2. NAVIGATE TO APP LIBRARY & VERIFY
    print("\nNavigating to App Library...")
    for attempt in range(6):
        screen = identify_current_screen()
        if screen == "App Library":
            break
        print(f"  Swipe {attempt+1} towards App Library...")
        adb("shell input swipe 950 1200 50 1200 150")
        time.sleep(1.0)
        
    screen_after_nav = identify_current_screen()
    print(f"[Check 2] Verified Screen before App Library Fling: {screen_after_nav}")
    capture_screen(f"{pkg}_2_applib.png")
    
    if screen_after_nav != "App Library":
        print("  ERROR: Not on App Library screen! Aborting applib test.")
        applib_stats = {"total": 0, "janky_pct": 0, "p50": 0, "p90": 0, "gpu50": 0, "gpu90": 0}
    else:
        adb(f"shell dumpsys gfxinfo {pkg} reset")
        time.sleep(0.3)
        print("  -> Flinging App Library vertically 8 times...")
        for i in range(4):
            adb("shell input swipe 540 1600 540 600 150")
            time.sleep(0.55)
            adb("shell input swipe 540 600 540 1600 150")
            time.sleep(0.55)
            
        applib_out = adb(f"shell dumpsys gfxinfo {pkg}")
        applib_stats = parse_gfxinfo(applib_out)
        print(f"  App Library Fling Results: {applib_stats}")
        
    # 3. NAVIGATE TO SPOTLIGHT SEARCH & VERIFY
    print("\nReturning Home & Waiting for Search Pill...")
    adb("shell input keyevent KEYCODE_HOME")
    adb("shell cmd statusbar collapse")
    # Wait 3.5s for page indicator dots to crossfade to search pill
    time.sleep(3.5)
    
    print("  Tapping Search Pill at (540, 1963)...")
    adb("shell input tap 540 1963")
    time.sleep(1.5)
    
    spot_screen = identify_current_screen()
    print(f"[Check 3] Verified Screen before Spotlight Fling: {spot_screen}")
    capture_screen(f"{pkg}_3_spotlight.png")
    
    if spot_screen != "Spotlight Search":
        print("  ERROR: Not on Spotlight Search screen! Aborting spotlight test.")
        spot_stats = {"total": 0, "janky_pct": 0, "p50": 0, "p90": 0, "gpu50": 0, "gpu90": 0}
    else:
        adb(f"shell dumpsys gfxinfo {pkg} reset")
        time.sleep(0.3)
        print("  -> Flinging Spotlight Search results vertically 8 times...")
        for i in range(4):
            adb("shell input swipe 540 1700 540 600 200")
            time.sleep(0.55)
            adb("shell input swipe 540 800 540 1600 200")
            time.sleep(0.55)
            
        spot_out = adb(f"shell dumpsys gfxinfo {pkg}")
        spot_stats = parse_gfxinfo(spot_out)
        print(f"  Spotlight Fling Results: {spot_stats}")
        
    # Close spotlight and return home
    adb("shell input keyevent KEYCODE_HOME")
    adb("shell cmd statusbar collapse")
    time.sleep(1.0)
    
    return applib_stats, spot_stats

if __name__ == "__main__":
    v_applib, v_spot = run_launcher_bench("foundation.e.blisslauncher", "com.ihomeos.launcher")
    h_applib, h_spot = run_launcher_bench("com.ihomeos.launcher", "foundation.e.blisslauncher")
    
    print("\n" + "=" * 80)
    print("                 OFFICIAL VERIFIED SIDE-BY-SIDE BENCHMARK                 ")
    print("=" * 80)
    print(f"{'Test Scenario':<25} | {'Metric':<18} | {'Vivo Build':<15} | {'HEAD Build':<15}")
    print("-" * 80)
    print(f"{'App Library Fling':<25} | {'Jank Rate':<18} | {v_applib['janky_pct']:.2f}%{'':<9} | {h_applib['janky_pct']:.2f}%")
    print(f"{'App Library Fling':<25} | {'50th % (p50)':<18} | {v_applib['p50']}ms{'':<11} | {h_applib['p50']}ms")
    print(f"{'App Library Fling':<25} | {'90th % (p90)':<18} | {v_applib['p90']}ms{'':<11} | {h_applib['p90']}ms")
    print(f"{'App Library Fling':<25} | {'GPU 50th %':<18} | {v_applib['gpu50']}ms{'':<11} | {h_applib['gpu50']}ms")
    print(f"{'App Library Fling':<25} | {'GPU 90th %':<18} | {v_applib['gpu90']}ms{'':<11} | {h_applib['gpu90']}ms")
    print(f"{'App Library Fling':<25} | {'Total Frames':<18} | {v_applib['total']}{'':<13} | {h_applib['total']}")
    print("-" * 80)
    print(f"{'Spotlight Search Fling':<25} | {'Jank Rate':<18} | {v_spot['janky_pct']:.2f}%{'':<9} | {h_spot['janky_pct']:.2f}%")
    print(f"{'Spotlight Search Fling':<25} | {'50th % (p50)':<18} | {v_spot['p50']}ms{'':<11} | {h_spot['p50']}ms")
    print(f"{'Spotlight Search Fling':<25} | {'90th % (p90)':<18} | {v_spot['p90']}ms{'':<11} | {h_spot['p90']}ms")
    print(f"{'Spotlight Search Fling':<25} | {'GPU 50th %':<18} | {v_spot['gpu50']}ms{'':<11} | {h_spot['gpu50']}ms")
    print(f"{'Spotlight Search Fling':<25} | {'GPU 90th %':<18} | {v_spot['gpu90']}ms{'':<11} | {h_spot['gpu90']}ms")
    print(f"{'Spotlight Search Fling':<25} | {'Total Frames':<18} | {v_spot['total']}{'':<13} | {h_spot['total']}")
    print("=" * 80)
