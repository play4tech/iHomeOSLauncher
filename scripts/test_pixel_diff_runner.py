import os
import sys
import time
import subprocess
from PIL import Image, ImageChops

def run_cmd(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout.strip()

def run_adb(cmd):
    return run_cmd(f"adb {cmd}")

def capture(out_path):
    with open(out_path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f)

def compute_diff(path_a, path_b):
    im_a = Image.open(path_a).convert("RGB")
    im_b = Image.open(path_b).convert("RGB")
    diff = ImageChops.difference(im_a, im_b)
    
    extrema = diff.getextrema()
    max_diff = max(extrema[0][1], extrema[1][1], extrema[2][1])
    
    hist = diff.histogram()
    r_hist = hist[0:256]
    g_hist = hist[256:512]
    b_hist = hist[512:768]
    
    total_pixels = im_a.width * im_a.height
    diff_gt_2 = 0
    total_samples = 3 * total_pixels
    target_p99 = int(total_samples * 0.99)
    accum = 0
    p99_val = 0
    
    for val in range(256):
        count = r_hist[val] + g_hist[val] + b_hist[val]
        if val > 2:
            diff_gt_2 += count
        accum += count
        if p99_val == 0 and accum >= target_p99:
            p99_val = val
            
    return {
        "max_diff": max_diff,
        "p99_diff": p99_val,
        "diff_gt_2": diff_gt_2,
        "diff_gt_2_pct": (diff_gt_2 / total_samples) * 100.0,
        "total_pixels": total_pixels
    }

def set_opt(early_out, fetch_collapse, hoist):
    run_adb(f"shell setprop debug.launcher.early_out {1 if early_out else 0}")
    run_adb(f"shell setprop debug.launcher.fetch_collapse {1 if fetch_collapse else 0}")
    run_adb(f"shell setprop debug.launcher.hoist {1 if hoist else 0}")

def go_page(page_idx):
    run_adb("shell am start -n com.ihomeos.launcher/com.android.launcher3.Launcher")
    time.sleep(0.5)
    for _ in range(3):
        run_adb("shell input swipe 200 1000 850 1000 120")
        time.sleep(0.15)
    time.sleep(0.4)
    for _ in range(page_idx):
        run_adb("shell input swipe 850 1000 200 1000 120")
        time.sleep(0.3)
    time.sleep(0.5)

os.makedirs("scratch_diffs", exist_ok=True)

test_scenarios = [
    ("Page 0 (Widgets, Folders, Dock, Pill)", 0, None),
    ("Page 1 (Folders, Dock, Pill)", 1, None),
    ("App Library 100% (Category Pods, Search)", 2, None),
    ("Partial Swipe 25%", 1, (800, 1000, 650, 1000, 4000, 1.8)),
    ("Partial Swipe 50%", 1, (800, 1000, 500, 1000, 4000, 1.8)),
    ("Partial Swipe 75%", 1, (800, 1000, 350, 1000, 4000, 1.8)),
]

print("=== RUNNING PIXEL DIFF TESTS (SAVED CONFIG) ===")
results = []

for name, page, hold_swipe in test_scenarios:
    print(f"\nTesting: {name}")
    # 1. Stock capture
    set_opt(early_out=False, fetch_collapse=False, hoist=False)
    go_page(page)
    stock_path = f"scratch_diffs/stock_{name.replace(' ', '_').replace('(', '').replace(')', '').replace(',', '')}.png"
    if hold_swipe:
        x1, y1, x2, y2, dur, wait_t = hold_swipe
        proc = subprocess.Popen(f"adb shell input swipe {x1} {y1} {x2} {y2} {dur}", shell=True)
        time.sleep(wait_t)
        capture(stock_path)
        proc.wait()
    else:
        # Micro tap to trigger render with current setprops
        run_adb("shell input swipe 540 800 540 801 10")
        time.sleep(0.3)
        capture(stock_path)
        
    time.sleep(0.5)
    
    # 2. Optimized capture
    set_opt(early_out=True, fetch_collapse=True, hoist=True)
    go_page(page)
    opt_path = f"scratch_diffs/opt_{name.replace(' ', '_').replace('(', '').replace(')', '').replace(',', '')}.png"
    if hold_swipe:
        x1, y1, x2, y2, dur, wait_t = hold_swipe
        proc = subprocess.Popen(f"adb shell input swipe {x1} {y1} {x2} {y2} {dur}", shell=True)
        time.sleep(wait_t)
        capture(opt_path)
        proc.wait()
    else:
        run_adb("shell input swipe 540 800 540 801 10")
        time.sleep(0.3)
        capture(opt_path)
        
    diff = compute_diff(stock_path, opt_path)
    print(f"Result for {name}:")
    print(f"  Max Channel Diff: {diff['max_diff']}/255")
    print(f"  p99 Channel Diff: {diff['p99_diff']}/255")
    print(f"  Pixels with Diff > 2: {diff['diff_gt_2']} ({diff['diff_gt_2_pct']:.4f}%)")
    results.append((name, diff))

print("\n=== SUMMARY TABLE ===")
for name, diff in results:
    print(f"{name:40s} | Max: {diff['max_diff']:2d} | p99: {diff['p99_diff']:2d} | Diff>2: {diff['diff_gt_2']} ({diff['diff_gt_2_pct']:.4f}%)")
