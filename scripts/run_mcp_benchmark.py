import subprocess
import time
import sys

DEVICE = "10.247.83.170:5555"

def adb(cmd):
    full = f"adb -s {DEVICE} {cmd}"
    res = subprocess.run(full, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def swipe_fling():
    # Fast fling from 1400 down to 450 (duration 200ms) within the RecyclerView bounds
    adb("shell input swipe 540 1400 540 450 200")

def swipe_reverse():
    adb("shell input swipe 540 450 540 1400 200")

def warmup():
    print("  [Warmup] Sweeping list down and up twice...")
    for _ in range(2):
        swipe_fling()
        time.sleep(0.6)
        swipe_fling()
        time.sleep(0.6)
        swipe_reverse()
        time.sleep(0.6)
        swipe_reverse()
        time.sleep(0.6)

def run_bench(pkg, name):
    print("\n" + "=" * 70)
    print(f"BENCHMARKING: {name} ({pkg})")
    print("=" * 70)
    
    # 1. Launch
    print(f"  [1] Starting {pkg}...")
    adb(f"shell am start -n {pkg}/com.android.launcher3.Launcher")
    time.sleep(2.0)
    
    # 2. Open Spotlight
    print("  [2] Opening Spotlight Search...")
    adb("shell input tap 540 1963")
    time.sleep(1.5)
    
    # 3. Warm-up
    print("  [3] Warming up JIT and RenderThread...")
    warmup()
    time.sleep(0.5)
    
    # 4. Three measured runs
    for r in range(1, 4):
        print(f"\n--- Run {r} of 3 for {name} ---")
        adb(f"shell dumpsys gfxinfo {pkg} reset")
        time.sleep(0.3)
        
        for i in range(5):
            print(f"    Fling {i+1}/5...")
            swipe_fling()
            time.sleep(0.9)
            
        time.sleep(0.5)
        raw = adb(f"shell dumpsys gfxinfo {pkg}")
        
        # Extract full gfxinfo block
        lines = raw.splitlines()
        capturing = False
        block = []
        for line in lines:
            if "Graphics info for pid" in line:
                capturing = True
            if capturing:
                block.append(line)
                if "View hierarchy:" in line:
                    break
        full_block = "\n".join(block) if block else raw
        print(f"\n[GFXINFO RESULT - {name} - RUN {r}]:")
        print(full_block)
        print("-" * 70)

def main():
    print("[Config] Setting display refresh rate to 60.0 Hz...")
    adb("shell settings put system peak_refresh_rate 60.0")
    adb("shell settings put system min_refresh_rate 60.0")
    
    run_bench("foundation.e.blisslauncher", "HEAD Build")
    run_bench("com.ihome.launcher", "Vivo Build")

if __name__ == "__main__":
    main()
