"""
Helper utility to establish, verify, and restore clean hermetic fixtures for Launcher3 testing.
Works on retail/emulator builds via `run-as com.ihomeos.launcher` without requiring root.
"""

import subprocess
import time
import re

PACKAGE = "com.ihomeos.launcher"
ACTIVITY = "com.android.launcher3.Launcher"

def run_adb(serial, *args):
    cmd = ["adb", "-s", serial] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip()

def prepare_fixtures(serial="emulator-5554"):
    """Prepares and saves launcher_fixture.db and prefs_fixture.xml."""
    # 1. Read current prefs
    prefs_xml = run_adb(serial, "shell", f"run-as {PACKAGE} cat shared_prefs/com.android.launcher3.prefs.xml")
    
    # Clean up malformed lines and ensure onboarding is marked done
    prefs_xml = re.sub(r'<boolean name=".*?onboarding.*?>', '', prefs_xml)
    if '<boolean name="launcher.first_run_onboarding_done"' not in prefs_xml:
        prefs_xml = prefs_xml.replace('</map>', '    <boolean name="launcher.first_run_onboarding_done" value="true" />\n</map>')

    # Push to tmp and copy via run-as
    run_adb(serial, "shell", f"run-as {PACKAGE} sh -c 'cat << \"EOF\" > shared_prefs/com.android.launcher3.prefs.xml\n{prefs_xml}\nEOF'")
    run_adb(serial, "shell", f"run-as {PACKAGE} cp shared_prefs/com.android.launcher3.prefs.xml shared_prefs/prefs_fixture.xml")

    # 2. Ensure db fixture exists
    db_list = run_adb(serial, "shell", f"run-as {PACKAGE} ls databases/")
    if "launcher_fixture.db" not in db_list and "launcher_4_by_6.db" in db_list:
        run_adb(serial, "shell", f"run-as {PACKAGE} cp databases/launcher_4_by_6.db databases/launcher_fixture.db")

    print("[FIXTURE] Fixtures successfully prepared and verified on device.")

def restore_fixtures(serial="emulator-5554"):
    """Instantly restores launcher DB and prefs fixtures."""
    run_adb(serial, "shell", "am", "force-stop", PACKAGE)
    run_adb(serial, "shell", f"run-as {PACKAGE} cp databases/launcher_fixture.db databases/launcher_4_by_6.db")
    run_adb(serial, "shell", f"run-as {PACKAGE} rm -f databases/launcher_4_by_6.db-wal databases/launcher_4_by_6.db-shm databases/launcher_4_by_6.db-journal")
    run_adb(serial, "shell", f"run-as {PACKAGE} cp shared_prefs/prefs_fixture.xml shared_prefs/com.android.launcher3.prefs.xml")
    time.sleep(0.3)
    run_adb(serial, "shell", "am", "start", "-n", f"{PACKAGE}/{ACTIVITY}")
    time.sleep(0.8)
    print("[FIXTURE] Restored clean fixture state successfully.")

if __name__ == "__main__":
    prepare_fixtures()
    restore_fixtures()
