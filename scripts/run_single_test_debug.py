import sys
import os
import time

sys.path.insert(0, os.path.abspath("."))
print("STEP 1: Importing modules", flush=True)
from tests.framework.device import LauncherDevice
from tests.framework.ui_inspector import UIInspector
from tests.framework.tracer import DragTracer
from tests.suite_85 import build_suite

print("STEP 2: Initializing LauncherDevice", flush=True)
dev = LauncherDevice("emulator-5554")

print("STEP 3: Calling ensure_launcher_ready()", flush=True)
dev.ensure_launcher_ready()

print("STEP 4: Initializing UIInspector", flush=True)
insp = UIInspector(dev)

print("STEP 5: Getting workspace icons", flush=True)
icons = insp.get_workspace_icons()
print(f"Icons found: {len(icons)}", flush=True)
for ic in icons:
    print(f"  {ic['name']} at {ic['center']}", flush=True)

print("STEP 6: Initializing DragTracer", flush=True)
tracer = DragTracer("emulator-5554")
tracer.clear()

print("STEP 7: Executing action_tc01", flush=True)
from tests.suite_85 import action_tc01
action_tc01(dev, insp)

print("STEP 8: Capturing logcat proof", flush=True)
log_str, truncated, crash = tracer.capture()
print(f"Captured {len(log_str.splitlines())} log lines. Truncated: {truncated}, Crash: {crash}", flush=True)
print("SUCCESS: TC-01 completed!", flush=True)
