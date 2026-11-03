"""
Creates a multi-row, multi-page folder fixture database on the device.
Enables immediate deterministic testing for TC-22 through TC-28, TC-30, TC-32, TC-36, TC-37, TC-38.
"""

import sqlite3
import subprocess
import time

PACKAGE = "com.ihomeos.launcher"

def run_adb(*args):
    cmd = ["adb", "-s", "emulator-5554"] + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True, errors="replace")
    return res.stdout.strip()

def create_folder_fixture():
    # 1. Pull clean launcher_fixture.db
    p = subprocess.Popen(
        ["adb", "-s", "emulator-5554", "exec-out", "run-as", PACKAGE, "cat", "databases/launcher_fixture.db"],
        stdout=subprocess.PIPE
    )
    raw_data = p.stdout.read()
    local_db = "test_results/launcher_folder_fixture.db"
    with open(local_db, "wb") as f:
        f.write(raw_data)

    # 2. Modify database using sqlite3
    conn = sqlite3.connect(local_db)
    cursor = conn.cursor()

    for table in ["favorites_all", "favorites"]:
        # Insert Folder entry at (0, 0)
        cursor.execute(f"""
            INSERT OR REPLACE INTO {table} 
            (_id, title, intent, container, screen, cellX, cellY, spanX, spanY, itemType, appWidgetId, icon, appWidgetProvider, modified, restored, profileId, rank, options, appWidgetSource)
            VALUES 
            (999, 'TestFolder', NULL, -100, 0, 0, 0, 1, 1, 2, -1, NULL, NULL, {int(time.time()*1000)}, 1, 0, 0, 0, 0)
        """)

        # Move items 5 through 15 into folder 999
        # 9 items on page 0 (3x3 grid) and 2 items on page 1 (overflow)
        for rank in range(11):
            item_id = 5 + rank
            screen = 0 if rank < 9 else 1
            cell_x = (rank % 3) if rank < 9 else (rank - 9)
            cell_y = (rank // 3) if rank < 9 else 0

            cursor.execute(f"""
                UPDATE {table} 
                SET container = 999, screen = {screen}, rank = {rank}, cellX = {cell_x}, cellY = {cell_y}
                WHERE _id = {item_id}
            """)

        # Adjust remaining workspace items (16..21) so none overlap with folder at (0,0)
        # Place 16..20 on Page 0, and item 21 on Page 1 (for TC-76 horizontal pagination)
        remaining = [16, 17, 18, 19, 20]
        for idx, item_id in enumerate(remaining):
            gx = (idx % 3) + 1
            gy = idx // 3
            cursor.execute(f"""
                UPDATE {table}
                SET container = -100, screen = 0, cellX = {gx}, cellY = {gy}
                WHERE _id = {item_id}
            """)

        # Item 21 on workspace Page 1
        cursor.execute(f"""
            UPDATE {table}
            SET container = -100, screen = 1, cellX = 0, cellY = 0
            WHERE _id = 21
        """)

    conn.commit()
    conn.close()
    print("[FIXTURE] Local folder fixture DB generated with 10-item folder (9 on page 1, 1 on page 2).")

    # 3. Push to device via run-as
    run_adb("push", local_db, "/data/local/tmp/launcher_folder_fixture.db")
    run_adb("shell", f"run-as {PACKAGE} cp /data/local/tmp/launcher_folder_fixture.db databases/launcher_folder_fixture.db")
    run_adb("shell", "rm", "-f", "/data/local/tmp/launcher_folder_fixture.db")
    print("[FIXTURE] Successfully installed launcher_folder_fixture.db on device.")

if __name__ == "__main__":
    create_folder_fixture()
