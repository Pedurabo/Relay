import sqlite3
import sys
from pathlib import Path

db = Path(__file__).with_name(
    "relay.db"
)

incident_a = sys.argv[1]
target_a = sys.argv[2]
incident_b = sys.argv[3]
target_b = sys.argv[4]

con = sqlite3.connect(
    str(db)
)

cur = con.cursor()

pending = cur.execute("""
    SELECT COUNT(*)
    FROM pending_severity_commands
""").fetchone()[0]

print(
    "PENDING_COUNT=" +
    str(pending)
)

for incident_id, expected in (
    (incident_a, target_a),
    (incident_b, target_b),
):

    row = cur.execute("""
        SELECT
            severity,
            latestSequence
        FROM incidents
        WHERE id = ?
    """, (incident_id,)).fetchone()

    if row:
        print(
            "INCIDENT|" +
            incident_id +
            "|" +
            str(row[0]) +
            "|" +
            str(row[1]) +
            "|EXPECTED=" +
            expected
        )

con.close()
