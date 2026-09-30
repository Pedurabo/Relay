import sqlite3
import sys
from pathlib import Path

db = Path(sys.argv[1])

con = sqlite3.connect(str(db))
cur = con.cursor()

rows = cur.execute("""
    SELECT
        commandId,
        incidentId,
        severity,
        baseSeverity,
        deliveryState,
        createdAt
    FROM pending_severity_commands
    ORDER BY createdAt DESC
""").fetchall()

for row in rows:
    print(
        "|".join(
            str(v)
            for v in row
        )
    )

con.close()
