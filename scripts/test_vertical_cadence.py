import os
import sys
import time
import subprocess

def run_cmd(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout.strip()

def run_adb(cmd):
    return run_cmd(f"adb {cmd}")

def parse_gfxinfo():
    out = run_adb("shell dumpsys gfxinfo com.ihome.launcher framestats")
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

def ensure_app_library():
    run_adb("shell am start -n com.ihome.launcher/com.android.launcher3.Launcher")
    time.sleep(0.4)
    # Swipe left 4 times to guarantee we are on App Library (rightmost screen)
    for _ in range(4):
        run_adb("shell input swipe 850 1000 200 1000 120")
        time.sleep(0.12)
    time.sleep(0.4)

def run_vertical_test():
    ensure_app_library()
    # Reset scroll position to top
    for _ in range(3):
        run_adb("shell input swipe 500 400 500 1600 150")
        time.sleep(0.1)
    time.sleep(0.5)

    all_frames = []
    print("Executing 10 vertical scrolls & flings in App Library...")
    for i in range(10):
        run_adb("shell dumpsys gfxinfo com.ihome.launcher reset")
        time.sleep(0.1)
        if i % 2 == 0:
            # Fling down (content moves up)
            run_adb("shell input swipe 500 1500 500 400 250")
        else:
            # Fling up (content moves down)
            run_adb("shell input swipe 500 400 500 1500 250")
        time.sleep(0.5)
        frames = parse_gfxinfo()
        all_frames.extend(frames)
        time.sleep(0.1)

    print(f"Collected {len(all_frames)} frames across 10 flings.")
    return all_frames

def analyze_vertical_frames(frames):
    if not frames:
        print("No frames captured!")
        return

    durations = []
    gpu_times = []
    cadence_120 = 0
    cadence_60 = 0
    cadence_low = 0
    jank_count = 0
    
    timeline = []
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
        
        interval = 0.0
        if prev_present is not None and present_time > prev_present:
            interval = (present_time - prev_present) / 1_000_000.0
            if interval > 50.0:
                # Idle gap between flings, not an active frame cadence drop
                pass
            elif interval <= 10.5:
                cadence_120 += 1
            elif interval <= 19.5:
                cadence_60 += 1
            else:
                cadence_low += 1
        elif prev_present is None:
            interval = dur_ms
            if dur_ms <= 10.5:
                cadence_120 += 1
            elif dur_ms <= 19.5:
                cadence_60 += 1
            else:
                cadence_low += 1
        
        is_jank = completed > deadline
        if is_jank:
            jank_count += 1
            
        timeline.append({
            "idx": idx,
            "dur": dur_ms,
            "gpu": gpu_ms,
            "interval": interval,
            "jank": is_jank
        })
        prev_present = present_time

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
    jank_pct = (jank_count / len(frames)) * 100.0
    
    print("\n" + "="*70)
    print("VERTICAL SCROLLING & FLING BENCHMARK (APP LIBRARY)")
    print("="*70)
    print(f"Total Frames Captured:      {len(frames)}")
    print(f"Janky Frames:               {jank_count} / {len(frames)} ({jank_pct:.2f}%)")
    print(f"120Hz Cadence (<=10.5ms):   {cadence_120} ({c120_pct:.2f}%)")
    print(f"60Hz Cadence  (10.5-19.5ms): {cadence_60} ({c60_pct:.2f}%)")
    print(f"<60Hz Cadence (>19.5ms):    {cadence_low} ({clow_pct:.2f}%)")
    print(f"Frame Duration (p50 / p95): {p50:.2f}ms / {p95:.2f}ms")
    print(f"GPU Turnaround (p50 / p95): {gpu_p50:.2f}ms / {gpu_p95:.2f}ms")
    print("="*70)
    
    # Representative 20-frame timeline sample
    sample_start = min(30, max(0, len(timeline) - 25))
    sample = timeline[sample_start:sample_start+20]
    print("\nRepresentative Per-Frame Timeline Sample (20 frames during active scroll):")
    print(f"{'Frame #':8s} | {'Duration (ms)':14s} | {'GPU Turnaround':15s} | {'Interval (ms)':14s} | {'Jank Status'}")
    print("-" * 70)
    for f in sample:
        jank_str = "JANK (Missed Deadline)" if f["jank"] else "OK (On Time)"
        print(f"{f['idx']:8d} | {f['dur']:12.2f}ms | {f['gpu']:13.2f}ms | {f['interval']:12.2f}ms | {jank_str}")
    print("="*70)

if __name__ == "__main__":
    frames = run_vertical_test()
    analyze_vertical_frames(frames)
