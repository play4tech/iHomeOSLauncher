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

def set_condition(cull, loop_opt, early_out, fetch_collapse, hoist):
    run_adb(f"shell setprop debug.launcher.cull {1 if cull else 0}")
    run_adb(f"shell setprop debug.launcher.loop_opt {1 if loop_opt else 0}")
    run_adb(f"shell setprop debug.launcher.early_out {1 if early_out else 0}")
    run_adb(f"shell setprop debug.launcher.fetch_collapse {1 if fetch_collapse else 0}")
    run_adb(f"shell setprop debug.launcher.hoist {1 if hoist else 0}")
    time.sleep(0.2)

def go_page_1():
    run_adb("shell am start -n com.ihomeos.launcher/com.android.launcher3.Launcher")
    time.sleep(0.4)
    for _ in range(3):
        run_adb("shell input swipe 200 1000 850 1000 120")
        time.sleep(0.12)
    time.sleep(0.3)
    run_adb("shell input swipe 850 1000 200 1000 120")
    time.sleep(0.4)

def run_10_swipes():
    all_frames = []
    for swipe_idx in range(10):
        go_page_1()
        run_adb("shell dumpsys gfxinfo com.ihomeos.launcher reset")
        time.sleep(0.15)
        # Smooth swipe from Page 1 to App Library (~350ms)
        run_adb("shell input swipe 850 1000 200 1000 350")
        time.sleep(0.4)
        frames = parse_gfxinfo()
        all_frames.extend(frames)
        time.sleep(0.15)
    return all_frames

def analyze_frames(frames):
    if not frames:
        return {"total": 0, "jank_count": 0, "jank_pct": 0, "c120_pct": 0, "c60_pct": 0, "c_low_pct": 0, "p50": 0, "gpu_p50": 0, "cluster": (0,0,0)}
    
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
            progress = idx / total_f
            if progress < 0.25:
                cluster_janks[0] += 1
            elif progress < 0.75:
                cluster_janks[1] += 1
            else:
                cluster_janks[2] += 1

    durations.sort()
    gpu_times.sort()
    p50 = durations[len(durations) // 2] if durations else 0
    gpu_p50 = gpu_times[len(gpu_times) // 2] if gpu_times else 0
    total_intervals = cadence_120 + cadence_60 + cadence_low
    if total_intervals == 0: total_intervals = 1
    
    return {
        "total": total_f,
        "jank_count": jank_count,
        "jank_pct": (jank_count / total_f) * 100.0,
        "c120_pct": (cadence_120 / total_intervals) * 100.0,
        "c60_pct": (cadence_60 / total_intervals) * 100.0,
        "c_low_pct": (cadence_low / total_intervals) * 100.0,
        "p50": p50,
        "gpu_p50": gpu_p50,
        "cluster": (cluster_janks[0], cluster_janks[1], cluster_janks[2])
    }

conditions = [
    ("1. Baseline (Stock, No Cull, No Opt)", False, False, False, False, False),
    ("2. + Culling Only",                   True,  False, False, False, False),
    ("3. + Loop Cleanup",                   True,  True,  False, False, False),
    ("4. + Fetch Collapse",                 True,  True,  False, True,  False),
    ("5. + Early Out",                      True,  True,  True,  True,  False),
    ("6. + Hoist (All Optimizations)",      True,  True,  True,  True,  True),
]

print("=== STARTING 10-SWIPE ABLATION STUDY MATRIX ===", flush=True)
results = []
for name, cull, loop, early, fetch, hoist in conditions:
    print(f"\nCondition: {name}", flush=True)
    set_condition(cull, loop, early, fetch, hoist)
    frames = run_10_swipes()
    m = analyze_frames(frames)
    print(f"  Frames: {m['total']} | Jank: {m['jank_count']}/{m['total']} ({m['jank_pct']:.2f}%) | 120Hz: {m['c120_pct']:.1f}% | 60Hz: {m['c60_pct']:.1f}% | <60Hz: {m['c_low_pct']:.1f}%", flush=True)
    print(f"  Duration p50: {m['p50']:.1f}ms | GPU p50: {m['gpu_p50']:.1f}ms | Entry/Mid/Exit Jank Cluster: {m['cluster']}", flush=True)
    results.append((name, m))

print("\n" + "="*95, flush=True)
print(f"{'Condition':36s} | {'Jank %':8s} | {'120Hz %':7s} | {'60Hz %':6s} | {'<60Hz %':7s} | {'Dur p50':7s} | {'GPU p50':7s}", flush=True)
print("="*95, flush=True)
for name, m in results:
    print(f"{name:36s} | {m['jank_pct']:7.2f}% | {m['c120_pct']:6.1f}% | {m['c60_pct']:5.1f}% | {m['c_low_pct']:6.1f}% | {m['p50']:5.1f}ms | {m['gpu_p50']:5.1f}ms", flush=True)
print("="*95, flush=True)
