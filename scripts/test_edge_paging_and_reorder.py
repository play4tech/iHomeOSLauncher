"""
Automated test script for:
1. Pushing an app to the right/left edge to switch pages.
2. Dragging an app into Column 0 without flipping pages (in-page reorder available).
"""

import time
import subprocess
import uiautomator2 as u2

SERIAL = "693a9b29"
PACKAGE = "com.ihomeos.launcher"
ACTIVITY = "com.android.launcher3.Launcher"

def adb(*args):
    cmd = ["adb", "-s", SERIAL] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip()

def clear_logcat():
    subprocess.run(["adb", "-s", SERIAL, "logcat", "-c"])

def get_logcat(tags=None):
    if tags:
        filter_args = [f"{t}:V" for t in tags] + ["*:S"]
        cmd = ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"] + filter_args
    else:
        cmd = ["adb", "-s", SERIAL, "logcat", "-d", "-v", "threadtime"]
    res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    return res.stdout

def restore_backup():
    adb("shell", "run-as", PACKAGE, "cp", "databases/launcher_user_backup.db", "databases/launcher_4_by_6.db")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
    time.sleep(1.5)

def ensure_page(d, target_page=0):
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("shell", "am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
    time.sleep(0.5)
    
    # Check if target page is visible
    if target_page == 0:
        if not d(text="Play Store").exists():
            d.swipe(200, 1200, 800, 1200, steps=15)
            time.sleep(0.8)
    elif target_page == 1:
        if not d(text="Brave").exists():
            d.swipe(800, 1200, 200, 1200, steps=15)
            time.sleep(0.8)

def test_push_to_edge_switches_pages(d):
    print("\n" + "="*50)
    print("TEST 1: Push to Edge Switches Pages (SpringLoaded Edge Paging)")
    print("="*50)
    restore_backup()
    ensure_page(d, 0)
    
    assert d(text="Play Store").exists(), "Play Store not on Page 0 at start"
    b = d(text="Play Store").info['bounds']
    startX = (b['left'] + b['right']) // 2
    startY = (b['top'] + b['bottom']) // 2
    print(f"Play Store at ({startX}, {startY}) on Page 0")
    
    clear_logcat()
    
    # Drag Play Store from Page 0 towards RIGHT edge (x=1060, y=800) and hold
    print("Dragging Play Store icon to right edge (1060, 800)...")
    
    d.touch.down(startX, startY)
    time.sleep(0.8) # trigger long press
    d.touch.move(startX + 50, startY) # trigger drag threshold
    time.sleep(0.2)
    d.touch.move(1060, 800)
    time.sleep(1.5) # hold at edge for 500ms alarm + 300ms scroll animation
    
    # Move into the center of the new page and drop
    print("Moving onto Page 1 content area (660, 800) and releasing...")
    d.touch.move(660, 800)
    time.sleep(0.5)
    d.touch.up(660, 800)
    time.sleep(1.0)
    
    # Check logs for snapToPage
    logs = get_logcat(tags=["Workspace", "PagedView", "DragTrace", "DropDebug"])
    snap_found = False
    for l in logs.splitlines():
        if any(k in l for k in ["snapToPage", "dropTargetLayout", "onDrop"]):
            print("  [LOG]", l.strip())
        if "snapToPage whichPage=1" in l:
            snap_found = True
            
    assert snap_found, "snapToPage whichPage=1 was not triggered by right edge push!"
    print("CONFIRMED: snapToPage whichPage=1 triggered successfully!")
    
    # Verify Brave is on screen (we are on Page 1)
    assert d(text="Brave").exists(timeout=2.0), "Expected to be on Page 1 (Brave visible)"
    print("PASS: Pushing to right edge successfully flipped from Page 0 to Page 1!")
    
    # Now push to LEFT edge to return to Page 0
    clear_logcat()
    print("\nDragging an icon on Page 1 to left edge (20, 800) to return to Page 0...")
    d.touch.down(660, 800)
    time.sleep(0.8)
    d.touch.move(660 - 50, 800)
    time.sleep(0.2)
    d.touch.move(20, 800)
    time.sleep(1.5) # hold at edge
    
    print("Moving onto Page 0 content area (540, 800) and releasing...")
    d.touch.move(540, 800)
    time.sleep(0.5)
    d.touch.up(540, 800)
    time.sleep(1.0)
    
    logs = get_logcat(tags=["Workspace", "PagedView", "DragTrace", "DropDebug"])
    snap_back_found = False
    for l in logs.splitlines():
        if any(k in l for k in ["snapToPage", "dropTargetLayout", "onDrop"]):
            print("  [LOG]", l.strip())
        if "snapToPage whichPage=0" in l:
            snap_back_found = True
            
    assert snap_back_found, "snapToPage whichPage=0 was not triggered by left edge push!"
    print("CONFIRMED: snapToPage whichPage=0 triggered successfully!")
    print("PASS: Pushing to left edge successfully flipped from Page 1 back to Page 0!")

def test_column0_in_page_reorder_does_not_flip_page(d):
    print("\n" + "="*50)
    print("TEST 2: Column 0 In-Page Hover Does NOT Flip Page")
    print("="*50)
    restore_backup()
    ensure_page(d, 1)
    
    assert d(text="Brave").exists(), "Brave not on Page 1 at start"
    clear_logcat()
    
    # On Page 1, drag an icon from Column 2 (x=663, y=550) into Column 0 (x=171, y=550)
    # Hover in Column 0 for 1.5s (longer than the 500ms alarm)
    print("Dragging icon from Column 2 (663, 550) into Column 0 (171, 550) and hovering for 1.5s...")
    d.touch.down(663, 550)
    time.sleep(0.8) # long press
    d.touch.move(663 - 40, 550) # trigger drag threshold
    time.sleep(0.2)
    d.touch.move(171, 550) # hover in Column 0 (center ~57dp / 171px)
    time.sleep(1.5) # hold in Column 0
    d.touch.up(171, 550)
    time.sleep(1.0)
    
    logs = get_logcat(tags=["Workspace", "PagedView", "DragTrace"])
    snap_occurred = False
    for l in logs.splitlines():
        if "snapToPage" in l:
            print("  [UNEXPECTED SNAP]", l.strip())
            snap_occurred = True
            
    assert not snap_occurred, "FAIL: Page flipped while hovering inside Column 0!"
    assert d(text="Brave").exists(), "Expected to remain on Page 1!"
    print("PASS: Page remained on Page 1! Column 0 hover did NOT flip the page. In-page reorder preserved.")

if __name__ == "__main__":
    d = u2.connect(SERIAL)
    print(f"Connected to {SERIAL}")
    try:
        test_push_to_edge_switches_pages(d)
        test_column0_in_page_reorder_does_not_flip_page(d)
        print("\n" + "="*50)
        print("ALL EDGE PAGING & COLUMN 0 REORDER TESTS PASSED!")
        print("="*50)
    finally:
        restore_backup()
        print("Restored original user backup homescreen.")
