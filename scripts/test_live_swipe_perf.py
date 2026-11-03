import os
import sys
import time
import subprocess

def run_cmd(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout.strip()

def run_adb(cmd):
    return run_cmd(f"adb {cmd}")

def parse_gfxinfo():
    out = run_adb("shell dumpsys gfxinfo com.ihomeos.launcher framestats")
    lines = out.splitlines()
    in_profile = False
    frames = []
    for line in lines:
        line = line.strip()
        if line.startswith("Flags,"):
            in_profile = True
            continue
        if in_profile:
            if not line or not line[0].isdigit():
                if frames:
                    break
                continue
            try:
                parts = [int(p) for p in line.strip().rstrip(",").split(",") if p]
            except ValueError:
                continue
            if len(parts) >= 22:
                frames.append(parts)
    return frames

def go_page_1():
    run_adb("shell am start -n com.ihomeos.launcher/com.android.launcher3.Launcher")
    time.sleep(0.3)
    for _ in range(3):
        run_adb("shell input swipe 200 1000 850 1000 120")
        time.sleep(0.1)
    run_adb("shell input swipe 850 1000 200 1000 120")
    time.sleep(0.3)

def go_app_library():
    run_adb("shell am start -n com.ihomeos.launcher/com.android.launcher3.Launcher")
    time.sleep(0.3)
    for _ in range(4):
        run_adb("shell input swipe 850 1000 200 1000 120")
        time.sleep(0.1)
    time.sleep(0.3)

def analyze_frames(frames, label):
    if not frames:
        print(f"[{label}] No frames captured!")
        return

    durations = []
    gpu_times = []
    cadence_120 = 0
    cadence_60 = 0
    cadence_low = 0
    jank_count = 0
    cluster_janks = [0, 0, 0] # first 25%, mid 50%, last 25%
    total_f = len(frames)
    
    prev_present = None
    for idx, f in enumerate(frames):
        intended_vsync = f[2]
        vsync = f[3]
        deadline = f[9]
        issue_draw = f[14]
        completed = f[16]
        if completed == 0 or intended_vsync == 0:
            continue
        present_time = f[21] if len(f) > 21 and f[21] > 0 else vsync
        
        dur_ms = (completed - intended_vsync) / 1_000_000.0
        durations.append(dur_ms)
        
        gpu_ms = (completed - issue_draw) / 1_000_000.0
        gpu_times.append(gpu_ms)
        
        if prev_present is not None and present_time > prev_present:
            interval = (present_time - prev_present) / 1_000_000.0
            if interval > 50.0:
                pass
            elif interval <= 10.5:
                cadence_120 += 1
            elif interval <= 19.5:
                cadence_60 += 1
            else:
                cadence_low += 1
        elif prev_present is None:
            if dur_ms <= 10.5:
                cadence_120 += 1
            elif dur_ms <= 19.5:
                cadence_60 += 1
            else:
                cadence_low += 1
        prev_present = present_time
        
        is_jank = completed > deadline
        if is_jank:
            jank_count += 1
            prog = idx / total_f
            if prog < 0.25:
                cluster_janks[0] += 1
            elif prog < 0.75:
                cluster_janks[1] += 1
            else:
                cluster_janks[2] += 1

    durations.sort()
    gpu_times.sort()
    p50 = durations[len(durations) // 2] if durations else 0
    p95 = durations[int(len(durations) * 0.95)] if durations else 0
    gpu_p50 = gpu_times[len(gpu_times) // 2] if gpu_times else 0
    gpu_p95 = gpu_times[int(len(gpu_times) * 0.95)] if gpu_times else 0
    
    total_intervals = cadence_120 + cadence_60 + cadence_low
    if total_intervals == 0: total_intervals = 1
    
    c120_pct = (cadence_120 / total_intervals) * 100.0
    c60_pct = (cadence_60 / total_intervals) * 100.0
    clow_pct = (cadence_low / total_intervals) * 100.0
    jank_pct = (jank_count / total_f) * 100.0
    
    print("\n" + "="*75, flush=True)
    print(f"BENCHMARK RESULTS: {label}", flush=True)
    print("="*75, flush=True)
    print(f"Total Frames:               {total_f}", flush=True)
    print(f"Janky Frames:               {jank_count} / {total_f} ({jank_pct:.2f}%)", flush=True)
    print(f"120Hz Cadence (<=10.5ms):   {cadence_120} ({c120_pct:.2f}%)", flush=True)
    print(f"60Hz Cadence  (10.5-19.5ms): {cadence_60} ({c60_pct:.2f}%)", flush=True)
    print(f"<60Hz Cadence (>19.5ms):    {cadence_low} ({clow_pct:.2f}%)", flush=True)
    print(f"Frame Duration (p50 / p95): {p50:.2f}ms / {p95:.2f}ms", flush=True)
    print(f"GPU Turnaround (p50 / p95): {gpu_p50:.2f}ms / {gpu_p95:.2f}ms", flush=True)
    print(f"Jank Clustering (Entry/Mid/Exit): {cluster_janks[0]} / {cluster_janks[1]} / {cluster_janks[2]}", flush=True)
    print("="*75, flush=True)

def run_entry_swipes():
    print("Testing 10 swipes: Homescreen (Page 1) -> App Library (Entry)...", flush=True)
    entry_frames = []
    for i in range(10):
        go_page_1()
        run_adb("shell dumpsys gfxinfo com.ihomeos.launcher reset")
        time.sleep(0.15)
        run_adb("shell input swipe 850 1000 200 1000 350")
        time.sleep(0.4)
        frames = parse_gfxinfo()
        entry_frames.extend(frames)
        time.sleep(0.1)
    analyze_frames(entry_frames, "PAGE 1 -> APP LIBRARY (ENTRY)")

def run_exit_swipes():
    print("Testing 10 swipes: App Library -> Homescreen (Exit)...", flush=True)
    exit_frames = []
    for i in range(10):
        go_app_library()
        run_adb("shell dumpsys gfxinfo com.ihomeos.launcher reset")
        time.sleep(0.15)
        run_adb("shell input swipe 200 1000 850 1000 350")
        time.sleep(0.4)
        frames = parse_gfxinfo()
        exit_frames.extend(frames)
        time.sleep(0.1)
    analyze_frames(exit_frames, "APP LIBRARY -> PAGE 1 (EXIT)")

if __name__ == "__main__":
    run_entry_swipes()
    run_exit_swipes()
