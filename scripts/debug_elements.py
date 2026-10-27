import sys
import os

sys.path.insert(0, os.path.abspath("."))

from tests.framework.device import LauncherDevice
from tests.framework.ui_inspector import UIInspector

dev = LauncherDevice()
insp = UIInspector(dev)

elems = insp.get_screen_elements()
print(f"Total raw elements: {len(elems)}")

icons = insp.get_workspace_icons()
print(f"Workspace icons filtered: {len(icons)}")

folders = insp.find_all_folders()
print(f"Folders found: {len(folders)}")

print("\n--- RAW ELEMENTS SAMPLE ---")
for e in elems[:15]:
    w = e["bounds"][2] - e["bounds"][0]
    h = e["bounds"][3] - e["bounds"][1]
    print(f"Name='{e['name']}', Bounds={e['bounds']}, W={w}, H={h}, Center={e['center']}, Cls={e['class']}")
