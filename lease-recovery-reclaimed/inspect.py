import sqlite3
import sys
from pathlib import Path

db = Path(__file__).with_name(
    "relay.db"
)

command_id = sys.argv[1]

con = sqlite3.connect(str(db))
cur = con.cursor()

row = cur.execute("""
    SELECT
        commandId,
        incidentId,
        severity,
        baseSeverity,
        deliveryState
    FROM pending_severity_commands
    WHERE commandId = ?
""", (command_id,)).fetchone()

if row:
    print(
        "FOUND|" +
        "|".join(
            str(v)
            for v in row
        )
    )
else:
    print("MISSING")

con.close()
