"""
Dedicated runner for the remaining incomplete tests:
- Group 2: In-Folder Multi-Row Reorder (TC-22, TC-23, TC-24, TC-25, TC-26, TC-27, TC-28, TC-30, TC-32)
- Group 3: Multi-Page Folder (TC-36, TC-37, TC-38)
- Group 4: Lifecycle & App Launch & Popup (TC-60, TC-61, TC-63, TC-71, TC-72, TC-73, TC-74, TC-82)
- Group 5: Pagination (TC-76)
- Group 6: Theme & Density (TC-84, TC-85)
"""

import sys
import os
import time

sys.path.insert(0, os.path.abspath("."))

from tests.framework.runner import TestRunner
from tests.suite_85 import build_suite

REMAINING_TEST_IDS = [
    # Group 2: In-Folder Multi-Row Reorder (9)
    "TC-22", "TC-23", "TC-24", "TC-25", "TC-26", "TC-27", "TC-28", "TC-30", "TC-32",
    
    # Group 3: Multi-Page Folder (3)
    "TC-36", "TC-37", "TC-38",
    
    # Group 4: Lifecycle & App Launch & Popup (8)
    "TC-60", "TC-61", "TC-63", "TC-71", "TC-72", "TC-73", "TC-74", "TC-82",
    
    # Group 5: Pagination (1)
    "TC-76",
    
    # Group 6: Theme & Density (2)
    "TC-84", "TC-85"
]

def main():
    print(f"=== RUNNING {len(REMAINING_TEST_IDS)} REMAINING INCOMPLETE TESTS ===")
    runner = TestRunner(serial="emulator-5554")
    suite = build_suite()

    # Pre-restore folder fixture for folder & multi-page tests
    print("[SETUP] Restoring multi-row, multi-page folder fixture...")
    runner.device.restore_folder_fixture()
    time.sleep(1.0)

    results = runner.run_tests(suite, filter_ids=REMAINING_TEST_IDS)
    print(f"\nExecution completed! Captured results for {len(results)} tests.")

if __name__ == "__main__":
    main()
