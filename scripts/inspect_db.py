import sqlite3

conn = sqlite3.connect("test_results/launcher_check.db")
c = conn.cursor()
c.execute("SELECT name, sql FROM sqlite_master WHERE type='table'")
for r in c.fetchall():
    print("TABLE:", r[0])

print("=== Screen 1 items ===")
c.execute("SELECT _id, title, itemType, container, screen, cellX, cellY, spanX, spanY FROM favorites WHERE container=-100 AND screen=1 ORDER BY cellY, cellX")
for r in c.fetchall():
    print(r)

print("=== Screen 2 items ===")
c.execute("SELECT _id, title, itemType, container, screen, cellX, cellY, spanX, spanY FROM favorites WHERE container=-100 AND screen=2 ORDER BY cellY, cellX")
for r in c.fetchall():
    print(r)

print("=== All distinct itemTypes ===")
c.execute("SELECT DISTINCT itemType FROM favorites")
print(c.fetchall())
