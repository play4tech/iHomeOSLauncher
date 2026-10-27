"""
Execution runner for Launcher3 test framework.
Iterates through test cases, normalizes state, checks preconditions,
runs actions, captures structured logcat proof, and records deterministic PASS/FAIL/ERROR/SKIP.
"""

import time
import json
import os
import traceback
from typing import List, Optional
from .device import LauncherDevice
from .tracer import DragTracer
from .ui_inspector import UIInspector
from .test_case import TestCase, PreconditionError

class TestRunner:
    def __init__(self, serial="emulator-5554", output_dir="test_results"):
        self.serial = serial
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)
        
        self.device = LauncherDevice(serial)
        self.tracer = DragTracer(serial)
        self.inspector = UIInspector(self.device)

    def run_tests(self, test_cases: List[TestCase], filter_ids: Optional[List[str]] = None) -> List[dict]:
        payload_file = os.path.join(self.output_dir, "test_execution_payload.json")
        payloads_map = {}

        # Load existing payloads to preserve all test results across filtered runs
        if os.path.exists(payload_file):
            try:
                with open(payload_file, "r", encoding="utf-8") as f:
                    for p in json.load(f):
                        if isinstance(p, dict) and "id" in p:
                            payloads_map[p["id"]] = p
            except Exception:
                payloads_map = {}

        def _save_payloads():
            def _sort_key(p):
                tid = p.get("id", "")
                if "-" in tid:
                    parts = tid.split("-")
                    if parts[1].isdigit():
                        return int(parts[1])
                return 999
            sorted_list = sorted(payloads_map.values(), key=_sort_key)
            with open(payload_file, "w", encoding="utf-8") as f:
                json.dump(sorted_list, f, indent=2)

        print(f"\n==========================================", flush=True)
        print(f"STARTING TEST RUN: {len(test_cases)} Test Cases on {self.serial}", flush=True)
        print(f"==========================================\n", flush=True)

        for i, tc in enumerate(test_cases):
            if filter_ids and tc.id not in filter_ids:
                continue

            print(f"[{i+1}/{len(test_cases)}] Executing {tc.id}: {tc.name}...", flush=True)
            
            # 1. Setup & Precondition Check
            precondition_ok = True
            try:
                if tc.setup:
                    tc.setup(self.device, self.inspector)
                else:
                    self.device.ensure_launcher_ready()
                    if len(self.inspector.get_workspace_icons()) < 3:
                        self.device.restore_clean_fixture()
            except PreconditionError as pe:
                tc.status = "SKIP"
                tc.status_reason = str(pe)
                precondition_ok = False
                print(f"  [SKIP] Precondition not met: {pe}", flush=True)
            except Exception as e:
                tc.status = "ERROR"
                tc.status_reason = f"Setup threw {type(e).__name__}: {str(e)}"
                tc.error_trace = traceback.format_exc()
                precondition_ok = False
                print(f"  [ERROR] Setup failed: {e}", flush=True)

            if not precondition_ok:
                payloads_map[tc.id] = tc.to_evaluation_payload()
                _save_payloads()
                continue

            # 2. Pre-state capture
            tc.pre_elements = self.inspector.get_screen_elements()

            # 3. Clear logcat and record start time
            self.tracer.clear()
            start_time = time.time()

            # 4. Execute Action & Assertions
            action_success = False
            try:
                tc.action(self.device, self.inspector)
                action_success = True
            except PreconditionError as pe:
                tc.status = "SKIP"
                tc.status_reason = f"Precondition not met: {str(pe)}"
                print(f"  [SKIP] Precondition not met: {pe}", flush=True)
            except AssertionError as ae:
                tc.status = "FAIL"
                tc.status_reason = f"Assertion failed: {str(ae)}"
                tc.error_trace = traceback.format_exc()
                print(f"  [FAIL] Assertion failed: {ae}", flush=True)
            except Exception as e:
                tc.status = "ERROR"
                tc.status_reason = f"Action threw {type(e).__name__}: {str(e)}"
                tc.error_trace = traceback.format_exc()
                print(f"  [ERROR] Action error: {e}", flush=True)
            finally:
                tc.duration_sec = time.time() - start_time
                time.sleep(0.3)

            # 5. Capture post-state and log proof
            tc.post_elements = self.inspector.get_screen_elements()
            log_str, truncated, crash = self.tracer.capture()
            tc.log_proof = log_str
            tc.truncated_logs = truncated

            # 6. Evaluate crash & final status
            if crash and tc.id != "TC-60":
                tc.status = "FAIL"
                tc.status_reason = f"Process crash detected: {crash}"
                print(f"  [CRASH] {crash}", flush=True)
            elif action_success:
                tc.status = "PASS"
                tc.status_reason = "Assertions satisfied cleanly."
                print(f"  [PASS] Completed in {tc.duration_sec:.2f}s", flush=True)

            # 7. Teardown
            if tc.teardown:
                try:
                    tc.teardown(self.device, self.inspector)
                except Exception as e:
                    print(f"  Warning in teardown: {e}", flush=True)

            payloads_map[tc.id] = tc.to_evaluation_payload()
            _save_payloads()

        print(f"\n==========================================", flush=True)
        print(f"COMPLETED TEST RUN: Results saved in {payload_file}", flush=True)
        print(f"==========================================\n", flush=True)

        return list(payloads_map.values())
