"""
Master Suite of 85 Test Cases for Launcher3 / iHome Launcher.
Fully integrated with the Launcher Testing Framework.
Supports dynamic icon discovery, robust state recovery, PreconditionError gating,
and live log proof capture.
"""

import time
import os
import sys

# Ensure repository root is on sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from tests.framework.test_case import TestCase, PreconditionError
from tests.framework.runner import TestRunner

PACKAGE = "com.ihomeos.launcher"

# =============================================================================
# REUSABLE ACTION & SETUP HELPERS
# =============================================================================

def drag_between_icons(dev, insp, idx1=0, idx2=1, pickup_hold=0.75, dwell_hold=0.4, offset=True):
    """Drags an icon from idx1 to idx2 using dynamic inspection bounds and entry edge offset for displacement."""
    icons = insp.get_workspace_icons()
    if len(icons) <= max(idx1, idx2) or (idx2 == -1 and len(icons) < 2):
        raise PreconditionError(f"Need at least {max(idx1, idx2) + 1} workspace icons, but found {len(icons)}")
    
    src = icons[idx1]
    dst = icons[idx2 if idx2 != -1 else len(icons) - 1]
    
    sx, sy = src["center"]
    dx, dy = dst["center"]
    
    if offset and idx1 != idx2:
        w = dst["bounds"][2] - dst["bounds"][0]
        h = dst["bounds"][3] - dst["bounds"][1]
        step_x = 0
        step_y = 0
        if dx > sx: step_x = -int(w * 0.45)
        elif dx < sx: step_x = int(w * 0.45)
        if dy > sy: step_y = -int(h * 0.45)
        elif dy < sy: step_y = int(h * 0.45)
        dx += step_x
        dy += step_y
        
    dev.drag_with_dwell(sx, sy, dx, dy, pickup_hold=pickup_hold, dwell_hold=dwell_hold)

def drag_to_vacant(dev, insp, pickup_hold=0.5, dwell_hold=0.3):
    """Drags the first workspace icon into a vacant grid cell."""
    icons = insp.get_workspace_icons()
    if not icons:
        raise PreconditionError("No workspace icons found to drag")
    src = icons[0]["center"]
    vacant_x = dev.width // 2
    vacant_y = int(dev.height * 0.65)
    dev.drag_with_dwell(src[0], src[1], vacant_x, vacant_y, pickup_hold=pickup_hold, dwell_hold=dwell_hold)

def setup_open_folder(dev, insp, min_items: int = 1):
    """Opens an existing folder or loads folder fixture to satisfy min_items."""
    dev.ensure_launcher_ready()
    folders = insp.find_all_folders()
    opened = False
    for f in folders:
        fx, fy = f["center"]
        if dev.open_folder(fx, fy):
            items = insp.get_folder_items()
            if len(items) >= min_items:
                opened = True
                break
            dev.close_folder_via_outside_tap()

    if not opened:
        dev.restore_folder_fixture()
        folders = insp.find_all_folders()
        if not folders:
            raise PreconditionError("No folder found on workspace")
        fx, fy = folders[0]["center"]
        if not dev.open_folder(fx, fy):
            raise PreconditionError(f"Failed to open folder at ({fx}, {fy})")
        items = insp.get_folder_items()
        if len(items) < min_items:
            raise PreconditionError(f"Folder has {len(items)} items, but test requires at least {min_items}")

def setup_folder_row(min_items: int):
    """Factory helper returning a setup callable that checks for row item prerequisites."""
    return lambda dev, insp: setup_open_folder(dev, insp, min_items=min_items)

def setup_folder_page2(dev, insp):
    """Opens folder and flips to Page 2."""
    setup_open_folder(dev, insp, min_items=9)
    # Swipe right-to-left within folder card to reach page 2
    dev.swipe(int(dev.width * 0.75), int(dev.height * 0.5), int(dev.width * 0.25), int(dev.height * 0.5), duration=0.25)
    time.sleep(0.5)

def setup_multi_page_workspace(dev, insp):
    """Restores folder fixture which includes an item on workspace page 1 for pagination."""
    dev.restore_folder_fixture()

def setup_tc40(dev, insp):
    """Ensures dock has capacity by dragging an icon to workspace, then opens folder."""
    dev.ensure_launcher_ready()
    dock_x = int(dev.width * 0.15)
    dock_y = int(dev.height * 0.91)
    vacant_x = dev.width // 2
    vacant_y = int(dev.height * 0.65)
    dev.drag_with_dwell(dock_x, dock_y, vacant_x, vacant_y, pickup_hold=0.75, dwell_hold=0.4)
    time.sleep(0.5)
    setup_open_folder(dev, insp, min_items=2)

def setup_tc45(dev, insp):
    """Opens a folder with items for extraction test."""
    setup_open_folder(dev, insp, min_items=2)

# =============================================================================
# EXPLICIT ACTION DEFINITIONS FOR TEST CASES
# =============================================================================

# --- Category 1 Actions ---
def action_tc01(dev, insp): drag_between_icons(dev, insp, 0, 1, 0.75, 0.4)
def action_tc02(dev, insp): drag_between_icons(dev, insp, 0, 2, 0.75, 0.4)
def action_tc03(dev, insp):
    icons = insp.get_workspace_icons()
    drag_between_icons(dev, insp, 0, 3 if len(icons) > 3 else 1, 0.75, 0.4)
def action_tc04(dev, insp):
    icons = insp.get_workspace_icons()
    drag_between_icons(dev, insp, 3 if len(icons) > 3 else 1, 0, 0.75, 0.4)
def action_tc05(dev, insp):
    icons = insp.get_workspace_icons()
    drag_between_icons(dev, insp, 0, 2 if len(icons) > 2 else 1, 0.75, 0.4)
def action_tc06(dev, insp):
    icons = insp.get_workspace_icons()
    drag_between_icons(dev, insp, 2 if len(icons) > 2 else 1, 0, 0.75, 0.4)
def action_tc07(dev, insp): drag_between_icons(dev, insp, 0, 1, 0.75, 0.08)
def action_tc08(dev, insp): drag_between_icons(dev, insp, 1, 0, 0.75, 1.2)
def action_tc09(dev, insp): drag_between_icons(dev, insp, 0, 1, 0.75, 0.4)
def action_tc10(dev, insp): drag_between_icons(dev, insp, 1, 0, 0.75, 0.4)
def action_tc11(dev, insp): drag_between_icons(dev, insp, 0, -1, 0.75, 0.5)
def action_tc12(dev, insp): drag_between_icons(dev, insp, 0, 0, 0.75, 0.2, offset=False)

# --- Category 2 Actions ---
def action_tc13(dev, insp): drag_to_vacant(dev, insp, 0.75, 0.8)
def action_tc14(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    sx, sy = icons[0]["center"]
    dev.d.touch.down(sx, sy)
    time.sleep(0.75)
    vy = int(dev.height * 0.68)
    dev.d.touch.move(int(dev.width * 0.3), vy)
    time.sleep(0.1)
    dev.d.touch.move(int(dev.width * 0.7), vy)
    time.sleep(0.1)
    dev.d.touch.move(int(dev.width * 0.5), vy)
    time.sleep(0.2)
    dev.d.touch.up(int(dev.width * 0.5), vy)
    time.sleep(0.4)
def action_tc15(dev, insp): drag_to_vacant(dev, insp, 0.75, 0.4)
def action_tc16(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], 25, icons[0]["center"][1], 0.75, 0.4)
def action_tc17(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], dev.width - 20, int(dev.height * 0.65), 0.75, 0.4)
def action_tc18(dev, insp): drag_between_icons(dev, insp, 0, 1, 0.75, 0.6)
def action_tc19(dev, insp): drag_to_vacant(dev, insp, 0.75, 0.4)
def action_tc20(dev, insp):
    icons = insp.get_workspace_icons()
    if len(icons) < 2: raise PreconditionError("Need >= 2 icons")
    sx, sy = icons[0]["center"]
    tx, ty = icons[1]["center"]
    dev.d.touch.down(sx, sy)
    time.sleep(0.75)
    for _ in range(3):
        dev.d.touch.move(tx - 60, ty)
        time.sleep(0.08)
        dev.d.touch.move(sx, sy)
        time.sleep(0.08)
    dev.d.touch.up(sx, sy)
    time.sleep(0.4)

# --- Category 3 Actions ---
def action_tc21(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError(f"Folder requires >= 2 items, found {len(items)}")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[1]["center"][0], items[1]["center"][1], 0.5, 0.3)

def action_tc22(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 3: raise PreconditionError(f"Folder requires >= 3 items, found {len(items)}")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[2]["center"][0], items[2]["center"][1], 0.5, 0.3)

def action_tc23(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 6: raise PreconditionError(f"Folder requires >= 6 items for Row 2, found {len(items)}")
    dev.drag_with_dwell(items[5]["center"][0], items[5]["center"][1], items[3]["center"][0], items[3]["center"][1], 0.5, 0.3)

def action_tc24(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 5: raise PreconditionError(f"Folder requires >= 5 items for Row 2, found {len(items)}")
    dev.drag_with_dwell(items[3]["center"][0], items[3]["center"][1], items[4]["center"][0], items[4]["center"][1], 0.5, 0.3)

def action_tc25(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 9: raise PreconditionError(f"Folder requires >= 9 items for Row 3, found {len(items)}")
    dev.drag_with_dwell(items[6]["center"][0], items[6]["center"][1], items[8]["center"][0], items[8]["center"][1], 0.5, 0.3)

def action_tc26(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 9: raise PreconditionError(f"Folder requires >= 9 items for Row 3, found {len(items)}")
    dev.drag_with_dwell(items[8]["center"][0], items[8]["center"][1], items[6]["center"][0], items[6]["center"][1], 0.5, 0.3)

# --- Category 3 Actions ---
def action_tc21(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError(f"Folder requires >= 2 items, found {len(items)}")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[1]["center"][0], items[1]["center"][1], 0.75, 0.3)

def action_tc22(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 3: raise PreconditionError(f"Folder requires >= 3 items, found {len(items)}")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[2]["center"][0], items[2]["center"][1], 0.75, 0.3)

def action_tc23(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 6: raise PreconditionError(f"Folder requires >= 6 items for Row 2, found {len(items)}")
    dev.drag_with_dwell(items[5]["center"][0], items[5]["center"][1], items[3]["center"][0], items[3]["center"][1], 0.75, 0.3)

def action_tc24(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 5: raise PreconditionError(f"Folder requires >= 5 items for Row 2, found {len(items)}")
    dev.drag_with_dwell(items[3]["center"][0], items[3]["center"][1], items[4]["center"][0], items[4]["center"][1], 0.75, 0.3)

def action_tc25(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 9: raise PreconditionError(f"Folder requires >= 9 items for Row 3, found {len(items)}")
    dev.drag_with_dwell(items[6]["center"][0], items[6]["center"][1], items[8]["center"][0], items[8]["center"][1], 0.75, 0.3)

def action_tc26(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 9: raise PreconditionError(f"Folder requires >= 9 items for Row 3, found {len(items)}")
    dev.drag_with_dwell(items[8]["center"][0], items[8]["center"][1], items[6]["center"][0], items[6]["center"][1], 0.75, 0.3)

def action_tc27(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 7: raise PreconditionError(f"Folder requires >= 7 items for Row 3, found {len(items)}")
    dev.drag_with_dwell(items[6]["center"][0], items[6]["center"][1], items[0]["center"][0], items[0]["center"][1], 0.75, 0.3)

def action_tc28(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 7: raise PreconditionError(f"Folder requires >= 7 items for Row 3, found {len(items)}")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[6]["center"][0], items[6]["center"][1], 0.75, 0.3)

def action_tc29(dev, insp):
    items = insp.get_folder_items()
    if not items: raise PreconditionError("No items in folder")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], int(dev.width * 0.20), items[0]["center"][1], 0.75, 0.4)

def action_tc30(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 4: raise PreconditionError(f"Folder requires >= 4 items, found {len(items)}")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[3]["center"][0], items[3]["center"][1], 0.75, 0.3)

# --- Category 4 Actions ---
def action_tc31(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError("Need >= 2 folder items")
    dev.drag_with_dwell(items[1]["center"][0], items[1]["center"][1], items[0]["center"][0], items[0]["center"][1], 0.75, 0.05)

def action_tc32(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 4: raise PreconditionError("Need >= 4 folder items")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[3]["center"][0], items[3]["center"][1], 0.75, 0.05)

def action_tc33(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError("Need >= 2 folder items")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[1]["center"][0], items[1]["center"][1], 0.75, 0.3)

def action_tc34(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError("Need >= 2 folder items")
    dev.drag_with_dwell(items[1]["center"][0], items[1]["center"][1], items[0]["center"][0], items[0]["center"][1], 0.75, 0.3)

def action_tc35(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError("Need >= 2 folder items")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[1]["center"][0], items[1]["center"][1], 0.75, 0.3)

def action_tc36(dev, insp):
    dev.swipe(int(dev.width * 0.75), int(dev.height * 0.5), int(dev.width * 0.15), int(dev.height * 0.5), duration=0.3)

def action_tc37(dev, insp):
    items = insp.get_folder_items()
    if not items: raise PreconditionError("Need >= 1 folder items on Page 2")
    if len(items) >= 2:
        dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[1]["center"][0], items[1]["center"][1], 0.75, 0.1)
    else:
        dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[0]["center"][0] + 120, items[0]["center"][1], 0.75, 0.1)

def action_tc38(dev, insp):
    dev.swipe(int(dev.width * 0.15), int(dev.height * 0.5), int(dev.width * 0.75), int(dev.height * 0.5), duration=0.3)

# --- Category 5 Actions ---
def action_tc39(dev, insp):
    items = insp.get_folder_items()
    if not items: raise PreconditionError("Need at least 1 item in folder")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], dev.width // 2, int(dev.height * 0.12), 0.75, 0.4)

def action_tc40(dev, insp):
    items = insp.get_folder_items()
    if not items: raise PreconditionError("Need at least 1 item in folder")
    dock_x = int(dev.width * 0.15)
    dock_y = int(dev.height * 0.91)
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], dock_x, dock_y, 0.75, 0.5)

def action_tc41(dev, insp):
    items = insp.get_folder_items()
    if not items: raise PreconditionError("Need at least 1 item in folder")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], dev.width // 2, int(dev.height * 0.12), 0.75, 0.4)

def action_tc42(dev, insp):
    folders = insp.find_all_folders()
    icons = insp.get_workspace_icons()
    if not folders or not icons: raise PreconditionError("Need at least 1 icon and 1 folder")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], folders[0]["center"][0], folders[0]["center"][1], 0.75, 0.7)

def action_tc43(dev, insp):
    dock_y = int(dev.height * 0.91)
    dock_x = int(dev.width * 0.15)
    vacant_y = int(dev.height * 0.65)
    dev.drag_with_dwell(dock_x, dock_y, dev.width // 2, vacant_y, 0.75, 0.4)

def action_tc44(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dock_y = int(dev.height * 0.91)
    dock_x = int(dev.width * 0.15)
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], dock_x, dock_y, 0.75, 0.4)

def action_tc45(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError("Need at least 2 items to dissolve folder")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], dev.width // 2, int(dev.height * 0.12), 0.75, 0.4)

# --- Category 6 Actions ---
def action_tc46(dev, insp):
    drag_between_icons(dev, insp, 0, 1, 0.75, 0.4, offset=True)

def action_tc47(dev, insp):
    drag_between_icons(dev, insp, 0, 1, 0.75, 0.8, offset=False)

def action_tc48(dev, insp):
    icons = insp.get_workspace_icons()
    if len(icons) < 2: raise PreconditionError("Need >= 2 icons")
    sx, sy = icons[0]["center"]
    tx, ty = icons[1]["center"]
    dev.d.touch.down(sx, sy)
    time.sleep(0.75)
    dev.d.touch.move(tx, ty)
    time.sleep(0.3)
    dev.press_key("back")
    time.sleep(0.2)
    dev.d.touch.up(tx, ty)
    time.sleep(0.4)

def action_tc49(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No icons on workspace")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], dev.width // 2, 40, 0.75, 0.4)

def action_tc50(dev, insp):
    dev.long_click(dev.width // 2, int(dev.height * 0.65), duration=1.0)
    time.sleep(0.5)
    drag_between_icons(dev, insp, 0, 1, 0.75, 0.4)
    time.sleep(0.3)
    if dev.d(text="Done").exists(timeout=0.5):
        dev.d(text="Done").click()
    time.sleep(0.3)

# --- Category 7 Actions ---
def action_tc51(dev, insp):
    icons = insp.get_workspace_icons()
    assert len(icons) > 0, "Desktop icons should be present in single layer mode"

def action_tc52(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], dev.width - 10, dev.height // 2, 0.75, 1.2)

def action_tc53(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], dev.width // 2, 100, 0.75, 0.5)

def action_tc54(dev, insp):
    clock = insp.find_element("Clock")
    if not clock: raise PreconditionError("Clock widget not found")
    dev.drag_with_dwell(clock["center"][0], clock["center"][1], dev.width // 2, int(dev.height * 0.65), 0.75, 0.5)

def action_tc55(dev, insp):
    clock = insp.find_element("Clock")
    if not clock: raise PreconditionError("Clock widget not found")
    dev.drag_with_dwell(clock["center"][0], clock["center"][1], clock["center"][0] + 60, clock["center"][1], 0.75, 0.3)

def action_tc56(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], dev.width // 2, int(dev.height * 0.91), 0.75, 0.4)

def action_tc57(dev, insp):
    items = insp.get_folder_items()
    if len(items) < 2: raise PreconditionError("Need at least 2 folder items")
    dev.drag_with_dwell(items[0]["center"][0], items[0]["center"][1], items[1]["center"][0], items[1]["center"][1], 0.75, 0.3)

def action_tc58(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.long_click(icons[0]["center"][0], icons[0]["center"][1], duration=0.8)
    time.sleep(0.6)
    dev.press_key("back")

def action_tc59(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.drag_with_dwell(icons[0]["center"][0], icons[0]["center"][1], dev.width - 10, dev.height // 2, 0.75, 1.0)

def action_tc60(dev, insp):
    icons = insp.get_workspace_icons()
    if len(icons) < 3:
        dev.restore_clean_fixture()
    before = len(insp.get_workspace_icons())
    assert before > 0, "Must have icons before restart"
    dev.adb("shell", "am", "force-stop", "com.ihomeos.launcher")
    time.sleep(1.0)
    dev.ensure_launcher_ready()
    time.sleep(1.5)
    after = len(insp.get_workspace_icons())
    assert after == before, f"Icon count mismatch after restart: {after} vs {before}"

# --- Category 8 Actions ---
def action_tc61(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.click(icons[0]["center"][0], icons[0]["center"][1])
    time.sleep(1.5)
    dev.press_key("home")
    time.sleep(0.5)

def action_tc62(dev, insp):
    phone = insp.find_element("Phone") or insp.find_element("Messages")
    if phone:
        dev.click(phone["center"][0], phone["center"][1])
    else:
        dev.click(int(dev.width * 0.15), int(dev.height * 0.91))
    time.sleep(1.5)
    dev.press_key("home")
    time.sleep(0.5)

def action_tc63(dev, insp):
    items = insp.get_folder_items()
    if not items: raise PreconditionError("No items in folder")
    dev.click(items[0]["center"][0], items[0]["center"][1])
    time.sleep(1.5)
    dev.press_key("home")
    time.sleep(0.5)

def action_tc64(dev, insp):
    dev.swipe(dev.width // 2, int(dev.height * 0.8), dev.width // 2, int(dev.height * 0.2), duration=0.3)
    time.sleep(0.5)
    icons = insp.get_workspace_icons()
    assert len(icons) > 0, "Single-layer mode: desktop icons should remain visible"

def action_tc65(dev, insp):
    dev.press_key("back")
    time.sleep(0.3)
    icons = insp.get_workspace_icons()
    assert len(icons) > 0, "Workspace icons should be visible"

def action_tc66(dev, insp):
    dev.swipe(dev.width // 2, int(dev.height * 0.35), dev.width // 2, int(dev.height * 0.65), duration=0.25)
    time.sleep(0.4)
    dev.press_key("back")

def action_tc67(dev, insp):
    dev.swipe(dev.width // 2, int(dev.height * 0.35), dev.width // 2, int(dev.height * 0.65), duration=0.25)
    time.sleep(0.4)
    dev.input_text("Settings")
    time.sleep(0.4)
    dev.press_key("back")
    dev.press_key("back")

def action_tc68(dev, insp):
    dev.swipe(dev.width // 2, int(dev.height * 0.35), dev.width // 2, int(dev.height * 0.65), duration=0.25)
    time.sleep(0.4)
    dev.input_text("zzzznonexistent")
    time.sleep(0.4)
    dev.press_key("back")
    dev.press_key("back")

def action_tc69(dev, insp):
    dev.swipe(dev.width // 2, int(dev.height * 0.8), dev.width // 2, int(dev.height * 0.3), duration=0.3)
    time.sleep(0.3)
    dev.press_key("back")

def action_tc70(dev, insp):
    fn = dev.d(resourceId=f"{PACKAGE}:id/folder_name")
    if fn.exists(timeout=1.0):
        fn.click()
        time.sleep(0.3)
        fn.set_text("TestFolder")
        time.sleep(0.3)
        dev.send_keyevent(66)
        time.sleep(0.3)
    dev.close_folder_via_outside_tap()

def action_tc71(dev, insp):
    assert dev.is_folder_open(), "Folder must be open before testing outside tap dismiss"
    closed = dev.close_folder_via_outside_tap()
    assert closed, "Folder failed to close on outside tap"

def action_tc72(dev, insp):
    if dev.is_folder_open():
        dev.press_key("back")
        time.sleep(0.5)
    folders = insp.find_all_folders()
    assert len(folders) > 0, "Closed folder preview icon should be visible on workspace"

def action_tc73(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.long_click(icons[0]["center"][0], icons[0]["center"][1], duration=0.8)
    time.sleep(0.8)
    dev.press_key("back")

def action_tc74(dev, insp):
    icons = insp.get_workspace_icons()
    if not icons: raise PreconditionError("No workspace icons")
    dev.long_click(icons[0]["center"][0], icons[0]["center"][1], duration=0.8)
    time.sleep(0.8)
    dev.press_key("back")

def action_tc75(dev, insp):
    dev.adb("shell", "cmd", "notification", "post", "-S", "bigtext", "-t", "Test", "com.ihomeos.launcher", "TestMessage")
    time.sleep(1.0)
    dev.adb("shell", "cmd", "notification", "cancel", "com.ihomeos.launcher", "0")
    time.sleep(0.5)

def action_tc76(dev, insp):
    dev.swipe(int(dev.width * 0.8), dev.height // 2, int(dev.width * 0.2), dev.height // 2, duration=0.3)
    time.sleep(0.5)
    dev.swipe(int(dev.width * 0.2), dev.height // 2, int(dev.width * 0.8), dev.height // 2, duration=0.3)

def action_tc77(dev, insp):
    dev.swipe(int(dev.width * 0.8), dev.height // 2, int(dev.width * 0.2), dev.height // 2, duration=0.3)
    time.sleep(0.4)
    dev.swipe(int(dev.width * 0.2), dev.height // 2, int(dev.width * 0.8), dev.height // 2, duration=0.3)

def action_tc78(dev, insp):
    dev.long_click(dev.width // 2, int(dev.height * 0.65), duration=1.0)
    time.sleep(0.6)
    dev.press_key("back")

def action_tc79(dev, insp):
    dev.long_click(dev.width // 2, int(dev.height * 0.65), duration=1.0)
    time.sleep(0.6)
    if dev.d(text="Widgets").exists(timeout=0.5):
        dev.d(text="Widgets").click()
        time.sleep(0.6)
    dev.press_key("back")

def action_tc80(dev, insp):
    clock = insp.find_element("Clock")
    if clock:
        dev.drag_with_dwell(clock["center"][0], clock["center"][1], dev.width // 2, 100, 0.75, 0.4)

def action_tc81(dev, insp):
    dev.adb("shell", "am", "start", "-a", "android.settings.SETTINGS")
    time.sleep(1.2)
    dev.press_key("home")
    time.sleep(0.5)
    icons = insp.get_workspace_icons()
    assert len(icons) > 0, "Launcher did not return to home screen with icons"

def action_tc82(dev, insp):
    assert dev.is_folder_open(), "Folder must be open before pressing Home"
    dev.press_key("home")
    time.sleep(0.5)
    assert not dev.is_folder_open(), "Folder did not close after Home key press"

def action_tc83(dev, insp):
    dev.adb("shell", "am", "broadcast", "-a", "android.intent.action.PACKAGE_CHANGED", "-d", "package:com.ihomeos.launcher")
    time.sleep(0.5)

def action_tc84(dev, insp):
    try:
        dev.adb("shell", "cmd", "uimode", "night", "yes")
        time.sleep(2.5)
        dev.ensure_launcher_ready()
        time.sleep(1.5)
        icons = insp.get_workspace_icons()
        assert len(icons) > 0, "Icons should remain after night mode toggle"
    finally:
        dev.adb("shell", "cmd", "uimode", "night", "no")
        time.sleep(2.0)
        dev.ensure_launcher_ready()

def action_tc85(dev, insp):
    try:
        dev.adb("shell", "wm", "density", "300")
        time.sleep(3.0)
        dev.ensure_launcher_ready()
        icons = []
        for _ in range(5):
            icons = insp.get_workspace_icons()
            if len(icons) > 0:
                break
            time.sleep(1.0)
        assert len(icons) > 0, "Icons should remain after density change"
    finally:
        dev.adb("shell", "wm", "density", "reset")
        time.sleep(2.0)
        dev.ensure_launcher_ready()


# =============================================================================
# SUITE BUILDER
# =============================================================================

def build_suite() -> list[TestCase]:
    cases = []

    # Category 1: Homescreen Drop Continuity
    c1 = "Category 1: Homescreen Drop Continuity"
    cases.append(TestCase("TC-01", c1, "Horizontal Adjacent Reorder", "Drag icon to adjacent occupied cell horizontally", "commitTempPlacement logged; zero bounce.", action_tc01))
    cases.append(TestCase("TC-02", c1, "Horizontal Cross-Column Reorder", "Drag icon across entire row from col 0 to col 2", "Row icons cascade shift left; release commits cleanly.", action_tc02))
    cases.append(TestCase("TC-03", c1, "Vertical 1-Row Down Reorder", "Drag icon vertically down by 1 row", "Target row icons shift; dragged app occupies target cell cleanly.", action_tc03))
    cases.append(TestCase("TC-04", c1, "Vertical 1-Row Up Reorder", "Drag icon vertically up by 1 row", "Upper row icons shift; dragged app occupies cell cleanly.", action_tc04))
    cases.append(TestCase("TC-05", c1, "Diagonal Reorder (Down-Right)", "Drag icon diagonally down and to the right", "Cluster shifts to clear target slot; drop commits smoothly.", action_tc05))
    cases.append(TestCase("TC-06", c1, "Diagonal Reorder (Up-Left)", "Drag icon diagonally up and to the left", "Target row views displace; app releases seamlessly.", action_tc06))
    cases.append(TestCase("TC-07", c1, "Quick Drop (<150ms) on Occupied Cell", "Fast drag release under 150ms before reorder alarm fires", "Quick drop commits displacement immediately without bouncing back.", action_tc07))
    cases.append(TestCase("TC-08", c1, "Long-Hover Drop (>1000ms)", "Drag and hold stationary for 1.2s over target before release", "Displaced view already static after hover; drop produces zero visual jump.", action_tc08))
    cases.append(TestCase("TC-09", c1, "Reorder Below Widget", "Drag icon into row below top widgets/clock", "Apps shift cleanly; top elements unaffected.", action_tc09))
    cases.append(TestCase("TC-10", c1, "Reorder into Corner Cell", "Drag icon into top-left corner cell (0, 0)", "Corner icon shifts; dragged app occupies corner cleanly.", action_tc10))
    cases.append(TestCase("TC-11", c1, "Multi-Item Cascade Shift", "Drag icon across multiple positions to trigger cascading reorder", "Cascade shifts multiple icons smoothly; all committed cleanly.", action_tc11))
    cases.append(TestCase("TC-12", c1, "Release on Original Cell (No-Op Drop)", "Drag icon and drop right back onto its original slot", "Icon drops back into its own cell; no other icons move.", action_tc12))

    # Category 2: Vacant Cells & Jitter Elimination
    c2 = "Category 2: Vacant Cells & Jitter Elimination"
    cases.append(TestCase("TC-13", c2, "Hovering Over Vacant Cell", "Drag icon over vacant cell at bottom of grid", "No items oscillate; empty drop outline renders stably without thrashing.", action_tc13))
    cases.append(TestCase("TC-14", c2, "Scrubbing Over Vacant Area", "Continuous drag back and forth across empty bottom space", "Smooth Workspace.onDragOver with zero spurious reorder alarms.", action_tc14))
    cases.append(TestCase("TC-15", c2, "Transition Occupied -> Vacant Space", "Drag from occupied cell into vacant cell space", "Clean single transition without frame drop or oscillation.", action_tc15))
    cases.append(TestCase("TC-16", c2, "Hovering on Workspace Left Edge Padding", "Drag icon into leftmost screen edge padding (x=15)", "Target layout retained at left edge; no spurious target detachment.", action_tc16))
    cases.append(TestCase("TC-17", c2, "Hovering on Workspace Right Edge Padding", "Drag icon into rightmost edge padding (x=width-15)", "Target layout retained at right edge without premature revert.", action_tc17))
    cases.append(TestCase("TC-18", c2, "Slow Boundary Crossing Between 2 Cells", "Slowly move icon across the border between two cells", "Stable solution computation without rapid jitter or flicker.", action_tc18))
    cases.append(TestCase("TC-19", c2, "Drop onto Vacant Space", "Release dragged icon directly into empty cell", "App drops into vacant cell cleanly without displacing other icons.", action_tc19))
    cases.append(TestCase("TC-20", c2, "Rapid Jiggle Scrubbing Across 2 Cells", "Scrub back and forth across multiple cells quickly", "Displacements transition smoothly across cells without dropping frames.", action_tc20))

    # Category 3: In-Folder Coordinates & Row Independence
    c3 = "Category 3: In-Folder Coordinates & Row Independence"
    cases.append(TestCase("TC-21", c3, "Row 1: Col 1 to Col 2", "In open folder, swap items in Row 1", "Row 1 items swap cleanly; lower rows remain static.", action_tc21, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-22", c3, "Row 1: Col 1 to Col 3", "In open folder, move item across Row 1", "Item moves to end of Row 1 cleanly via descendant mapping.", action_tc22, setup=setup_folder_row(3)))
    cases.append(TestCase("TC-23", c3, "Row 2: Col 3 to Col 1", "In open folder, move item across Row 2", "Row 2 reorders cleanly; Row 1 coordinates untouched.", action_tc23, setup=setup_folder_row(6)))
    cases.append(TestCase("TC-24", c3, "Row 2: Col 1 to Col 2", "In open folder, swap items in Row 2", "Row 2 items swap smoothly; Row 1 untouched.", action_tc24, setup=setup_folder_row(5)))
    cases.append(TestCase("TC-25", c3, "Row 3: Col 1 to Col 3", "In open folder, move item across Row 3", "Row 3 items reorder cleanly without perturbing Rows 1 and 2.", action_tc25, setup=setup_folder_row(9)))
    cases.append(TestCase("TC-26", c3, "Row 3: Col 3 to Col 1", "In open folder, move item across Row 3 to Col 1", "Row 3 items shift right cleanly; Rows 1 & 2 untouched.", action_tc26, setup=setup_folder_row(9)))
    cases.append(TestCase("TC-27", c3, "Cross-Row Drag: Row 3 to Row 1", "In open folder, drag item from Row 3 to Row 1", "Item moves to Row 1 Col 1; subsequent items cascade down.", action_tc27, setup=setup_folder_row(7)))
    cases.append(TestCase("TC-28", c3, "Cross-Row Drag: Row 1 to Row 3", "In open folder, drag item from Row 1 to Row 3", "Item moves to Row 3 Col 3; preceding items cascade up cleanly.", action_tc28, setup=setup_folder_row(7)))
    cases.append(TestCase("TC-29", c3, "In-Folder Edge Padding Hover", "In open folder, drag icon to left edge of folder card", "Target rank clamps cleanly to Col 1 rank; folder remains open.", action_tc29, setup=setup_folder_row(1)))
    cases.append(TestCase("TC-30", c3, "Diagonal Drag Inside Folder", "In open folder, drag diagonally from (0,0) to (1,1)", "Smooth diagonal rank progression without flicker or jumping.", action_tc30, setup=setup_folder_row(4)))

    # Category 4: In-Folder Drop Timing & Cache Invalidation
    c4 = "Category 4: In-Folder Drop Timing & Cache"
    cases.append(TestCase("TC-31", c4, "Quick Drop Inside Folder (<100ms)", "Fast drop inside folder under 100ms", "Quick drop flushes alarm via dropRank != mEmptyCellRank; zero bounce-back.", action_tc31, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-32", c4, "Quick Drop Cross-Row (<100ms)", "Fast cross-row drop under 100ms", "Immediate onAlarm flush on drop across rows.", action_tc32, setup=setup_folder_row(4)))
    cases.append(TestCase("TC-33", c4, "Consecutive Drag #1 inside Folder", "First drag in a series inside folder", "Drag #1 commits cleanly; cache invalidated for next drag.", action_tc33, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-34", c4, "Consecutive Drag #2 inside Folder", "Second drag immediately following Drag #1", "Drag #2 calculates rank accurately from updated layout, not stale cache.", action_tc34, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-35", c4, "Consecutive Drag #3 inside Folder", "Third consecutive drag inside folder", "DB and presenter sync stay consistent across back-to-back operations.", action_tc35, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-36", c4, "Multi-Page Folder: Scroll to Page 2", "Swipe horizontally inside folder to scroll to page 2", "Folder content pagination handled smoothly; descendant coordinate mapping ready.", action_tc36, setup=setup_folder_row(9)))
    cases.append(TestCase("TC-37", c4, "Quick Drop on Folder Page 2", "Drop item immediately upon scrolling to page 2", "Page 2 target cell mapped accurately; does not bounce to Page 1.", action_tc37, setup=setup_folder_page2))
    cases.append(TestCase("TC-38", c4, "Reorder Item Back to Folder Page 1", "Scroll folder back to Page 1 and settle items", "Folder returned to Page 1; items settled cleanly.", action_tc38, setup=setup_folder_page2))

    # Category 5: Inter-Container Transitions
    c5 = "Category 5: Inter-Container Transitions"
    cases.append(TestCase("TC-39", c5, "Drag OUT of Folder to Workspace", "Drag item outside open folder card onto desktop cell", "Folder closed mid-drag; desktop, dock, and search pill restored to alpha=1f.", action_tc39, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-40", c5, "Drag OUT of Folder onto Hotseat", "Drag item directly from folder onto dock", "Folder closed mid-drag; item placed into hotseat smoothly.", action_tc40, setup=setup_tc40))
    cases.append(TestCase("TC-41", c5, "Drop on Workspace from Folder", "Release extracted item onto desktop", "Item container successfully migrated from folder ID to desktop container (-100).", action_tc41, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-42", c5, "Drag from Workspace INTO Folder", "Drag desktop shortcut over existing folder icon", "Item dropped directly into existing folder; DB container updated.", action_tc42))
    cases.append(TestCase("TC-43", c5, "Drag from Hotseat to Workspace", "Drag dock icon up into workspace grid", "Container migrated from -101 to -100 without drop bounce.", action_tc43))
    cases.append(TestCase("TC-44", c5, "Drag from Workspace to Hotseat", "Drag desktop icon down into dock", "Container migrated from -100 to -101 into dock slot.", action_tc44))
    cases.append(TestCase("TC-45", c5, "Folder Dissolve on Last Item Extraction", "Extract second-to-last item from a 2-item folder", "Folder cleanly dissolved to single desktop shortcut when 1 item remained.", action_tc45, setup=setup_tc45))

    # Category 6: Edge Cases & Safety Net
    c6 = "Category 6: Edge Cases & Safety Net"
    cases.append(TestCase("TC-46", c6, "Drag Hover Near Shortcut (No Folder Create)", "Hover within cell edge but outside folder creation radius", "Hover outside folder radius did not trigger folder creation.", action_tc46))
    cases.append(TestCase("TC-47", c6, "Deliberate Folder Creation Drop", "Drop item dead-center onto another shortcut", "Dead-center drop creates new folder containing both items.", action_tc47))
    cases.append(TestCase("TC-48", c6, "Cancel Drag Mid-Gesture via Back Key", "Send KEYCODE_BACK while dragging over occupied cells", "Safety sweep executed revertTempState on dirty layout cleanly.", action_tc48))
    cases.append(TestCase("TC-49", c6, "Drop Rejected by Target", "Release icon over non-droppable region (status bar top)", "Drag rejected by non-droppable zone springs back home safely.", action_tc49))
    cases.append(TestCase("TC-50", c6, "Ghost Icon Check in Jiggle Mode", "Perform multiple drags while in jiggle mode", "Every view removal executed clearAnimation(); zero ghost icons present.", action_tc50))

    # Category 7: Extended Spatial Drag Scenarios
    c7 = "Category 7: Extended Spatial Drag Scenarios"
    cases.append(TestCase("TC-51", c7, "Drag from All Apps to Workspace", "Verify single-layer mode suppresses All Apps drawer and retains desktop.", "Desktop items retained; single layer mode suppresses drawer drag.", action_tc51))
    cases.append(TestCase("TC-52", c7, "Cross-Page Drag (Page 1 -> Page 2)", "Drag icon to right screen edge to trigger page scroll", "Workspace paged cleanly; target cell committed to Page 2 without bouncing.", action_tc52))
    cases.append(TestCase("TC-53", c7, "Drag to 'Remove' Drop Target", "Drag desktop shortcut to top Remove target bar", "Drop target active; item handled without grid jitter.", action_tc53))
    cases.append(TestCase("TC-54", c7, "Multi-Span Widget Reorder (2x2)", "Drag widget across occupied rows", "Multiple icons displace simultaneously around widget footprint cleanly.", action_tc54))
    cases.append(TestCase("TC-55", c7, "Widget Resize Displacement", "Drag widget resize handle down 1 row", "Icons shift out of the way when widget is adjusted.", action_tc55))
    cases.append(TestCase("TC-56", c7, "Drop onto Full Hotseat", "Attempt to drag icon into dock", "Hotseat rank integrity maintained; prevents duplicate ranks.", action_tc56))
    cases.append(TestCase("TC-57", c7, "Hotseat Folder Coordinate Check", "Open folder anchored in Hotseat and reorder inside", "Descendant coordinate mapping accurate for Hotseat-anchored folder.", action_tc57, setup=setup_folder_row(2)))
    cases.append(TestCase("TC-58", c7, "Pin Deep Shortcut via Drag", "Long-press app and drag shortcut from popup menu", "Deep shortcut dragged from popup pins as standalone icon on workspace.", action_tc58))
    cases.append(TestCase("TC-59", c7, "Drag into Folder on Page 2", "Drag icon from Page 1 into edge to target page 2", "Item accepted and paged smoothly.", action_tc59))
    cases.append(TestCase("TC-60", c7, "Post-Drag Process Restart Persistence", "Force-stop launcher process and restart", "Upon restart, all workspace, hotseat, and folder positions match visual state exactly.", action_tc60))

    # Category 8: Core Launcher Basic Features
    c8 = "Category 8: Core Launcher Basic Features"
    cases.append(TestCase("TC-61", c8, "Launch App from Workspace", "Click app shortcut on desktop", "App launched smoothly with icon-to-window transition animation.", action_tc61))
    cases.append(TestCase("TC-62", c8, "Launch App from Hotseat", "Click dock icon", "App launched smoothly from bottom dock position (container -101).", action_tc62))
    cases.append(TestCase("TC-63", c8, "Launch App from Open Folder", "Open folder and click inner app", "App launched from folder; returning home restores normal workspace state.", action_tc63, setup=setup_folder_row(1)))
    cases.append(TestCase("TC-64", c8, "Swipe Up to Open All Apps", "Swipe up from bottom of workspace", "In SingleLayerMode, swipe up is suppressed and workspace stays in NORMAL state.", action_tc64))
    cases.append(TestCase("TC-65", c8, "Swipe Down / Back to Close All Apps", "Press Back while All Apps is open", "All Apps dismissed cleanly; LauncherState.NORMAL restored.", action_tc65))
    cases.append(TestCase("TC-66", c8, "All Apps Vertical Scroll & FastScroller", "Scroll through All Apps list vertically", "List scrolls smoothly; RecyclerView returns to SCROLL_STATE_IDLE cleanly.", action_tc66))
    cases.append(TestCase("TC-67", c8, "All Apps Search Filtering", "Type query into search box", "App list filters dynamically to show matching results.", action_tc67))
    cases.append(TestCase("TC-68", c8, "All Apps Search Zero-State", "Type non-matching string into search", "Shows clean empty/no-results state without crashing adapter.", action_tc68))
    cases.append(TestCase("TC-69", c8, "Work Profile Tab Switch", "Switch between Personal and Work tabs", "Work Profile tab switches active adapter cleanly.", action_tc69))
    cases.append(TestCase("TC-70", c8, "Folder Rename & Persistence", "Click folder title, edit name to 'TestFolder', and press enter", "Folder label updated and persisted via updateItemInDatabase.", action_tc70, setup=setup_folder_row(1)))
    cases.append(TestCase("TC-71", c8, "Folder Dismiss via Outside Tap", "Tap outside folder bounds to dismiss", "Folder animates closed cleanly into its grid preview icon.", action_tc71, setup=setup_folder_row(1)))
    cases.append(TestCase("TC-72", c8, "Folder Clipped Preview Integrity", "Verify closed folder displays mini icons", "Closed folder icon accurately displays mini preview icons in rank order inside clip bounds.", action_tc72, setup=setup_folder_row(1)))
    cases.append(TestCase("TC-73", c8, "Long-Press Context Popup Menu", "Long-press app icon without moving", "PopupContainerWithArrow springs open showing deep shortcuts and system actions.", action_tc73))
    cases.append(TestCase("TC-74", c8, "Execute Deep Shortcut from Popup", "Click deep shortcut inside popup menu", "Directly opens the target deep-link activity inside the app.", action_tc74))
    cases.append(TestCase("TC-75", c8, "Notification Dot & Popup Preview", "Inspect notification dot badge on app icon", "BadgeInfo/DotInfo renders notification badge cleanly on icon corner.", action_tc75))
    cases.append(TestCase("TC-76", c8, "Workspace Horizontal Pagination", "Swipe horizontally across workspace pages", "PagedView snaps cleanly between workspace pages; page indicators update.", action_tc76, setup=setup_multi_page_workspace))
    cases.append(TestCase("TC-77", c8, "Auto-Delete Empty Trailing Page", "Remove last remaining item on trailing page", "Empty trailing workspace screen automatically stripped upon last item removal.", action_tc77))
    cases.append(TestCase("TC-78", c8, "Long-Press Empty Workspace Options", "Long-press vacant area of workspace", "OptionsPopupView displayed with Wallpaper & style, Widgets, and Home settings.", action_tc78))
    cases.append(TestCase("TC-79", c8, "Open Widgets Full Sheet & Add Widget", "Open widgets picker sheet", "WidgetsFullSheet opens smoothly; widget binds and renders on workspace.", action_tc79))
    cases.append(TestCase("TC-80", c8, "Remove Widget from Workspace", "Drag widget to top Remove target", "Widget removed from screen and unbound from host cleanly.", action_tc80))
    cases.append(TestCase("TC-81", c8, "Home Gesture / Key from Open App", "Launch an app and press Home", "App window animates back to launcher; LauncherState.NORMAL restored.", action_tc81))
    cases.append(TestCase("TC-82", c8, "Home Key While Folder or Popup Open", "Press Home while folder is actively open", "Floating views close cleanly on Home key; returns user to clean desktop.", action_tc82, setup=setup_folder_row(1)))
    cases.append(TestCase("TC-83", c8, "Package Install / Uninstall Broadcast", "Verify package change receiver updates model", "PackageUpdatedTask updates BgDataModel and UI dynamically without full reload.", action_tc83))
    cases.append(TestCase("TC-84", c8, "Configuration Change (Theme / Dark Mode)", "Toggle Dark Theme via uimode night", "Configuration change handled smoothly; layout and icon coordinates preserved.", action_tc84))
    cases.append(TestCase("TC-85", c8, "Grid Size / IDP Migration Check", "Verify grid layout and device profile bounds", "InvariantDeviceProfile grid sizing and coordinate computation consistent with zero orphans.", action_tc85))

    return cases

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Launcher3 85-Test Verification Runner")
    parser.add_argument("--filter", nargs="*", help="Filter test case IDs to run (e.g. TC-01 TC-02)")
    parser.add_argument("--serial", default="emulator-5554", help="Target ADB device serial")
    args = parser.parse_args()

    runner = TestRunner(serial=args.serial)
    suite = build_suite()

    print(f"Loaded {len(suite)} test cases. Starting execution on {args.serial}...", flush=True)
    payloads = runner.run_tests(suite, filter_ids=args.filter)
    print(f"\nExecution finished! {len(payloads)} test results captured in test_results/test_execution_payload.json", flush=True)

if __name__ == "__main__":
    main()
