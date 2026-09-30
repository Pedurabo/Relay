import sqlite3
import sys
from pathlib import Path

db = Path(__file__).with_name("relay.db")

incident_a = sys.argv[1]
target_a = sys.argv[2]
incident_b = sys.argv[3]
target_b = sys.argv[4]

con = sqlite3.connect(str(db))
cur = con.cursor()

pending = cur.execute("""
    SELECT
        commandId,
        incidentId,
        severity
    FROM pending_severity_commands
    ORDER BY createdAt ASC
""").fetchall()

print("PENDING_COUNT=" + str(len(pending)))

for row in pending:
    print(
        "PENDING|" +
        "|".join(str(v) for v in row)
    )

for incident_id, target in (
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
            target
        )
    else:
        print(
            "INCIDENT|" +
            incident_id +
            "|NOT_FOUND"
        )

con.close()
