import subprocess
import time
import sys

DEVICE = "10.247.83.170:5555"
PKG = "com.ihomeos.launcher"

def adb(cmd):
    full = f"adb -s {DEVICE} {cmd}"
    res = subprocess.run(full, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def ensure_screen():
    pwr = adb("shell dumpsys power")
    if "mWakefulness=Asleep" in pwr or "mWakefulness=Dozing" in pwr:
        adb("shell input keyevent KEYCODE_WAKEUP")
        time.sleep(0.5)
        adb("shell wm dismiss-keyguard")
        time.sleep(0.5)

def scroll_to_top():
    # Fast swipes to top of list
    for _ in range(3):
        adb("shell input swipe 540 600 540 1500 150")
        time.sleep(0.3)

def do_5_flings():
    # 5 flings alternating to keep list moving and not hit bottom
    flings = [
        (1400, 500), # down
        (1400, 500), # down
        (500, 1400), # up
        (1400, 500), # down
        (500, 1400), # up
    ]
    for i, (y1, y2) in enumerate(flings):
        adb(f"shell input swipe 540 {y1} 540 {y2} 200")
        time.sleep(0.9)

def main():
    ensure_screen()
    
    print("[1] Pinning refresh rate to 60.0 Hz...")
    adb("shell settings put system peak_refresh_rate 60.0")
    adb("shell settings put system min_refresh_rate 60.0")
    
    print(f"[2] Force-stopping {PKG} (without reinstalling)...")
    adb(f"shell am force-stop {PKG}")
    time.sleep(1.5)
    
    print(f"[3] Launching {PKG} from cold process...")
    adb(f"shell am start -n {PKG}/com.android.launcher3.Launcher")
    time.sleep(2.5)
    
    print("[4] Opening Spotlight Search...")
    adb("shell input tap 540 1963")
    time.sleep(1.5)
    
    print("[5] Running 4 consecutive 5-fling series...")
    for r in range(1, 5):
        print(f"\n==========================================")
        print(f"  HEAD (Cold Process) - RUN {r} of 4")
        print(f"==========================================")
        
        scroll_to_top()
        time.sleep(0.5)
        
        adb(f"shell dumpsys gfxinfo {PKG} reset")
        time.sleep(0.3)
        
        do_5_flings()
        time.sleep(0.5)
        
        raw = adb(f"shell dumpsys gfxinfo {PKG}")
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
        print(f"\n[GFXINFO - RUN {r}]:\n{full_block}")

if __name__ == "__main__":
    main()
