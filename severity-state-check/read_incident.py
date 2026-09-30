import sqlite3
import sys
from pathlib import Path

db = Path(__file__).with_name("relay.db")
incident_id = sys.argv[1]

con = sqlite3.connect(str(db))
cur = con.cursor()

row = cur.execute("""
    SELECT
        id,
        title,
        status,
        severity,
        latestSequence
    FROM incidents
    WHERE id = ?
""", (incident_id,)).fetchone()

if row:
    print("|".join(str(v) for v in row))
else:
    print("NOT_FOUND")

con.close()
