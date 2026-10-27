import sqlite3

conn = sqlite3.connect('cur_launcher.db')
c = conn.cursor()

print("--- FOLDERS ---")
for fid in [112, 117, 118, 119, 120, 121, 122, 123, 129]:
    c.execute("SELECT title FROM favorites_all WHERE _id=?", (fid,))
    row = c.fetchone()
    title = row[0] if row else "Unknown"
    c.execute("SELECT _id, title, rank FROM favorites_all WHERE container=? ORDER BY rank", (fid,))
    items = c.fetchall()
    ranks = [it[2] for it in items]
    print(f"Folder '{title}' (id={fid}): count={len(items)}, ranks={ranks}")

print("\n--- WORKSPACE SCREENS ---")
c.execute("SELECT _id, title, screen, cellX, cellY, itemType FROM favorites_all WHERE container=-100 ORDER BY screen, cellY, cellX")
items_by_screen = {}
for row in c.fetchall():
    screen = row[2]
    items_by_screen.setdefault(screen, []).append((row[1], row[3], row[4]))

for s in sorted(items_by_screen.keys()):
    print(f"Screen {s}: {len(items_by_screen[s])} items: {[it[0] for it in items_by_screen[s]]}")
