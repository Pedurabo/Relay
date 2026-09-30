import json
import sqlite3
import sys
from pathlib import Path

db_path = Path(sys.argv[1])
state_path = Path(sys.argv[2])

con = sqlite3.connect(str(db_path))
cur = con.cursor()

room_rows = cur.execute("""
    SELECT
        id,
        severity,
        latestSequence
    FROM incidents
    ORDER BY id
""").fetchall()

pending_count = cur.execute("""
    SELECT COUNT(*)
    FROM pending_severity_commands
""").fetchone()[0]

con.close()

with state_path.open(
    "r",
    encoding="utf-8"
) as f:
    state = json.load(f)

server_incidents = state.get(
    "incidents",
    {}
)

print(
    "PENDING_COUNT=" +
    str(pending_count)
)

aligned = []

for incident_id, room_severity, room_sequence in room_rows:

    server = server_incidents.get(
        incident_id
    )

    if not server:
        continue

    server_severity = str(
        server.get(
            "severity",
            ""
        )
    )

    server_sequence = int(
        server.get(
            "sequence",
            0
        )
    )

    if (
        room_severity ==
        server_severity
    ):
        aligned.append(
            (
                incident_id,
                room_severity,
                int(room_sequence),
                server_sequence
            )
        )

for row in aligned:
    print(
        "ALIGNED|" +
        "|".join(
            str(v)
            for v in row
        )
    )
