import os
import sys
import time
import subprocess
from PIL import Image, ImageChops

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def run_adb(cmd):
    return run_cmd(f"adb {cmd}")

def capture_screenshot(out_path):
    # Capture directly to binary file via adb exec-out to prevent CRLF/powershell corruption
    with open(out_path, "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f)

def compute_diff_metrics(img_path_a, img_path_b):
    im_a = Image.open(img_path_a).convert("RGB")
    im_b = Image.open(img_path_b).convert("RGB")
    diff = ImageChops.difference(im_a, im_b)
    
    extrema = diff.getextrema() # ((min_r, max_r), (min_g, max_g), (min_b, max_b))
    max_channel_diff = max(extrema[0][1], extrema[1][1], extrema[2][1])
    
    hist = diff.histogram() # 256 for R, 256 for G, 256 for B
    r_hist = hist[0:256]
    g_hist = hist[256:512]
    b_hist = hist[512:768]
    
    total_pixels = im_a.width * im_a.height
    diff_gt_2 = 0
    channel_diffs = []
    
    # Combined channel diff histogram
    for val in range(256):
        count = r_hist[val] + g_hist[val] + b_hist[val]
        if val > 2:
            diff_gt_2 += count
        channel_diffs.extend([val] * count)
        
    # Compute p99
    # Total channel samples = 3 * total_pixels
    total_samples = 3 * total_pixels
    target_p99 = int(total_samples * 0.99)
    accum = 0
    p99_val = 0
    for val in range(256):
        accum += (r_hist[val] + g_hist[val] + b_hist[val])
        if accum >= target_p99:
            p99_val = val
            break
            
    return {
        "max_diff": max_channel_diff,
        "p99_diff": p99_val,
        "diff_gt_2_count": diff_gt_2,
        "total_pixels": total_pixels,
        "diff_gt_2_pct": (diff_gt_2 / (3 * total_pixels)) * 100.0
    }

def set_shader_opt(enabled: bool):
    val = "1" if enabled else "0"
    run_adb(f"shell setprop debug.launcher.early_out {val}")
    run_adb(f"shell setprop debug.launcher.fetch_collapse {val}")
    run_adb(f"shell setprop debug.launcher.hoist {val}")
    # Small scroll to trigger redraw
    run_adb("shell input swipe 540 800 540 801 10")
    time.sleep(0.3)

def ensure_home_page(page_idx):
    # 0 = Page 0, 1 = Page 1, 2 = App Library
    run_adb("shell am start -n com.ihome.launcher/com.android.launcher3.Launcher")
    time.sleep(0.8)
    # Go to Page 0 first by swiping right several times
    for _ in range(4):
        run_adb("shell input swipe 200 1000 850 1000 150")
        time.sleep(0.15)
    time.sleep(0.5)
    if page_idx == 0:
        return
    for _ in range(page_idx):
        run_adb("shell input swipe 850 1000 200 1000 150")
        time.sleep(0.3)
    time.sleep(0.5)

print("Starting Diagnostic Audits...")
