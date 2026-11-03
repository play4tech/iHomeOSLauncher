"""
Tracer module for capturing DragTrace, process lifecycle, and crash events.
"""

import subprocess
from typing import Tuple, Optional

class DragTracer:
    def __init__(self, serial="emulator-5554"):
        self.serial = serial
        self.pid_before: Optional[str] = None
        self.pid_after: Optional[str] = None

    def get_launcher_pid(self) -> Optional[str]:
        """Queries the current PID of com.ihomeos.launcher."""
        try:
            res = subprocess.run(
                ["adb", "-s", self.serial, "shell", "pidof", "com.ihomeos.launcher"],
                capture_output=True, text=True, errors="replace"
            )
            pid = res.stdout.strip()
            return pid if pid else None
        except Exception:
            return None

    def clear(self):
        """Records PID and clears logcat buffer prior to executing a test action."""
        self.pid_before = self.get_launcher_pid()
        subprocess.run(["adb", "-s", self.serial, "logcat", "-c"], capture_output=True)

    def capture(self, max_lines=500) -> Tuple[str, bool, Optional[str]]:
        """
        Captures the unedited logcat buffer via threadtime format.
        Returns:
            log_str: formatted log string
            truncated: boolean flag if lines exceeded max_lines
            crash_detected: error string if process died or crashed, else None
        """
        self.pid_after = self.get_launcher_pid()
        crash_detected = None

        if self.pid_before and self.pid_after:
            before_pids = set(self.pid_before.split())
            after_pids = set(self.pid_after.split())
            if not before_pids.intersection(after_pids):
                crash_detected = f"Process restart detected: PID changed from {self.pid_before} to {self.pid_after}"
        elif self.pid_before and not self.pid_after:
            crash_detected = f"Process termination detected: PID {self.pid_before} died"

        cmd = [
            "adb", "-s", self.serial, "logcat", "-d", "-v", "threadtime",
            "DragTrace:V", "ICON_TRACE:V", "AndroidRuntime:E", "ActivityTaskManager:I", "*:S"
        ]
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
            lines = [line.strip() for line in res.stdout.splitlines() if line.strip()]
        except Exception as e:
            lines = [f"Logcat capture failed: {e}"]

        for line in lines:
            if "FATAL EXCEPTION" in line or "AndroidRuntime: FATAL" in line:
                if not crash_detected:
                    crash_detected = f"Crash detected in logcat: {line}"

        truncated = False
        if len(lines) > max_lines:
            truncated = True
            lines = lines[-max_lines:]

        return "\n".join(lines), truncated, crash_detected
