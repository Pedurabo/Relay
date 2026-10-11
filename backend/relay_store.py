from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1


class RelayStore:

    def __init__(
        self,
        database_path: str,
    ) -> None:

        self.database_path = (
            str(
                Path(database_path)
                .expanduser()
                .resolve()
            )
        )

        Path(
            self.database_path
        ).parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        self._initialize()

    def _connect(
        self,
    ) -> sqlite3.Connection:

        connection = sqlite3.connect(
            self.database_path,
            timeout=30.0,
        )

        connection.row_factory = (
            sqlite3.Row
        )

        connection.execute(
            "PRAGMA foreign_keys = ON"
        )

        connection.execute(
            "PRAGMA journal_mode = WAL"
        )

        return connection

    @contextmanager
    def _connection(
        self,
    ):

        connection = self._connect()

        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def _initialize(
        self,
    ) -> None:

        with self._connection() as connection:

            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS relay_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """
            )

            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS incidents (
                    incident_id TEXT PRIMARY KEY,
                    payload TEXT NOT NULL
                )
                """
            )

            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS incident_events (
                    event_id TEXT PRIMARY KEY,
                    incident_id TEXT NOT NULL,
                    sequence INTEGER NOT NULL,
                    payload TEXT NOT NULL
                )
                """
            )

            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS
                    index_incident_events_incident_sequence
                ON incident_events(
                    incident_id,
                    sequence
                )
                """
            )

            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS processed_commands (
                    command_id TEXT PRIMARY KEY,
                    payload TEXT NOT NULL
                )
                """
            )

            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS timeline_events (
                    entry_id TEXT PRIMARY KEY,
                    payload TEXT NOT NULL
                )
                """
            )

            connection.execute(
                """
                INSERT INTO relay_metadata(
                    key,
                    value
                )
                VALUES(
                    'schema_version',
                    ?
                )
                ON CONFLICT(key)
                DO UPDATE SET
                    value = excluded.value
                """,
                (
                    str(
                        SCHEMA_VERSION
                    ),
                ),
            )

    @staticmethod
    def _encode(
        payload: dict[str, Any],
    ) -> str:

        return json.dumps(
            payload,
            separators=(
                ",",
                ":",
            ),
            sort_keys=True,
        )

    @staticmethod
    def _decode(
        payload: str,
    ) -> dict[str, Any]:

        value = json.loads(
            payload
        )

        if not isinstance(
            value,
            dict,
        ):
            raise ValueError(
                "Stored Relay payload must be an object."
            )

        return value

    def load_incidents(
        self,
    ) -> dict[str, dict[str, Any]]:

        with self._connection() as connection:

            rows = connection.execute(
                """
                SELECT
                    incident_id,
                    payload
                FROM incidents
                """
            ).fetchall()

        return {
            str(
                row["incident_id"]
            ):
                self._decode(
                    str(
                        row["payload"]
                    )
                )
            for row in rows
        }

    def load_incident_event_history(
        self,
    ) -> dict[str, list[dict[str, Any]]]:

        with self._connection() as connection:

            rows = connection.execute(
                """
                SELECT
                    incident_id,
                    payload
                FROM incident_events
                ORDER BY
                    incident_id ASC,
                    sequence ASC
                """
            ).fetchall()

        history: dict[str, list[dict[str, Any]]] = {}

        for row in rows:

            incident_id = str(
                row["incident_id"]
            )

            history.setdefault(
                incident_id,
                [],
            ).append(
                self._decode(
                    str(
                        row["payload"]
                    )
                )
            )

        return history

    def load_processed_commands(
        self,
    ) -> dict[str, dict[str, Any]]:

        with self._connection() as connection:

            rows = connection.execute(
                """
                SELECT
                    command_id,
                    payload
                FROM processed_commands
                """
            ).fetchall()

        return {
            str(
                row["command_id"]
            ):
                self._decode(
                    str(
                        row["payload"]
                    )
                )
            for row in rows
        }

    def load_timeline_events(
        self,
    ) -> dict[str, dict[str, Any]]:

        with self._connection() as connection:

            rows = connection.execute(
                """
                SELECT
                    entry_id,
                    payload
                FROM timeline_events
                """
            ).fetchall()

        return {
            str(
                row["entry_id"]
            ):
                self._decode(
                    str(
                        row["payload"]
                    )
                )
            for row in rows
        }

    def persist_accepted_incident_command(
        self,
        incident: dict[str, Any],
        event: dict[str, Any],
        command_id: str,
        ack: dict[str, Any],
    ) -> None:

        incident_id = str(
            incident["id"]
        )

        event_id = str(
            event["eventId"]
        )

        sequence = int(
            event["sequence"]
        )

        with self._connection() as connection:

            try:
                connection.execute(
                    "BEGIN"
                )

                connection.execute(
                    """
                    INSERT INTO incidents(
                        incident_id,
                        payload
                    )
                    VALUES (?, ?)
                    ON CONFLICT(incident_id)
                    DO UPDATE SET
                        payload = excluded.payload
                    """,
                    (
                        incident_id,
                        self._encode(
                            incident
                        ),
                    ),
                )

                connection.execute(
                    """
                    INSERT INTO incident_events(
                        event_id,
                        incident_id,
                        sequence,
                        payload
                    )
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(event_id)
                    DO NOTHING
                    """,
                    (
                        event_id,
                        incident_id,
                        sequence,
                        self._encode(
                            event
                        ),
                    ),
                )

                connection.execute(
                    """
                    INSERT INTO processed_commands(
                        command_id,
                        payload
                    )
                    VALUES (?, ?)
                    ON CONFLICT(command_id)
                    DO UPDATE SET
                        payload = excluded.payload
                    """,
                    (
                        command_id,
                        self._encode(
                            ack
                        ),
                    ),
                )

                connection.commit()

            except Exception:

                connection.rollback()

                raise

    def upsert_incident(
        self,
        incident: dict[str, Any],
    ) -> None:

        incident_id = str(
            incident["id"]
        )

        with self._connection() as connection:

            connection.execute(
                """
                INSERT INTO incidents(
                    incident_id,
                    payload
                )
                VALUES(
                    ?,
                    ?
                )
                ON CONFLICT(incident_id)
                DO UPDATE SET
                    payload = excluded.payload
                """,
                (
                    incident_id,
                    self._encode(
                        incident
                    ),
                ),
            )

    def append_incident_event(
        self,
        incident_id: str,
        event: dict[str, Any],
    ) -> None:

        event_id = str(
            event["eventId"]
        )

        sequence = int(
            event.get(
                "sequence",
                0,
            )
        )

        with self._connection() as connection:

            connection.execute(
                """
                INSERT OR IGNORE INTO incident_events(
                    event_id,
                    incident_id,
                    sequence,
                    payload
                )
                VALUES(
                    ?,
                    ?,
                    ?,
                    ?
                )
                """,
                (
                    event_id,
                    incident_id,
                    sequence,
                    self._encode(
                        event
                    ),
                ),
            )

    def put_processed_command(
        self,
        command_id: str,
        payload: dict[str, Any],
    ) -> None:

        with self._connection() as connection:

            connection.execute(
                """
                INSERT INTO processed_commands(
                    command_id,
                    payload
                )
                VALUES(
                    ?,
                    ?
                )
                ON CONFLICT(command_id)
                DO UPDATE SET
                    payload = excluded.payload
                """,
                (
                    command_id,
                    self._encode(
                        payload
                    ),
                ),
            )

    def check_ready(
        self,
    ) -> None:

        with self._connection() as connection:

            row = connection.execute(
                "PRAGMA quick_check(1)"
            ).fetchone()

            if (
                row is None
                or str(
                    row[0]
                ).strip().lower()
                != "ok"
            ):
                raise RuntimeError(
                    "sqlite_not_ready"
                )

    def _ensure_refresh_sessions_table(
        self,
        connection: sqlite3.Connection,
    ) -> None:

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS refresh_sessions(
                token_hash TEXT PRIMARY KEY,
                payload TEXT NOT NULL
            )
            """
        )


    def load_refresh_sessions(
        self,
    ) -> dict[str, dict[str, Any]]:

        with self._connection() as connection:

            self._ensure_refresh_sessions_table(
                connection
            )

            rows = connection.execute(
                """
                SELECT
                    token_hash,
                    payload
                FROM refresh_sessions
                """
            ).fetchall()

        return {
            str(row["token_hash"]):
                self._decode(
                    str(row["payload"])
                )
            for row in rows
        }


    def put_refresh_session(
        self,
        token_hash: str,
        principal: dict[str, Any],
    ) -> None:

        with self._connection() as connection:

            self._ensure_refresh_sessions_table(
                connection
            )

            connection.execute(
                """
                INSERT INTO refresh_sessions(
                    token_hash,
                    payload
                )
                VALUES (?, ?)
                ON CONFLICT(token_hash)
                DO UPDATE SET
                    payload = excluded.payload
                """,
                (
                    token_hash,
                    self._encode(
                        principal
                    ),
                ),
            )


    def delete_refresh_session(
        self,
        token_hash: str,
    ) -> None:

        with self._connection() as connection:

            self._ensure_refresh_sessions_table(
                connection
            )

            connection.execute(
                """
                DELETE FROM refresh_sessions
                WHERE token_hash = ?
                """,
                (
                    token_hash,
                ),
            )


    def rotate_refresh_session(
        self,
        old_token_hash: str,
        new_token_hash: str,
        principal: dict[str, Any],
    ) -> None:

        with self._connection() as connection:

            self._ensure_refresh_sessions_table(
                connection
            )

            try:
                connection.execute(
                    "BEGIN"
                )

                deleted = connection.execute(
                    """
                    DELETE FROM refresh_sessions
                    WHERE token_hash = ?
                    """,
                    (
                        old_token_hash,
                    ),
                )

                if deleted.rowcount != 1:
                    raise ValueError(
                        "refresh_session_not_found"
                    )

                connection.execute(
                    """
                    INSERT INTO refresh_sessions(
                        token_hash,
                        payload
                    )
                    VALUES (?, ?)
                    """,
                    (
                        new_token_hash,
                        self._encode(
                            principal
                        ),
                    ),
                )

                connection.commit()

            except Exception:

                connection.rollback()

                raise

    def put_timeline_event(
        self,
        entry_id: str,
        payload: dict[str, Any],
    ) -> None:

        with self._connection() as connection:

            connection.execute(
                """
                INSERT INTO timeline_events(
                    entry_id,
                    payload
                )
                VALUES(
                    ?,
                    ?
                )
                ON CONFLICT(entry_id)
                DO UPDATE SET
                    payload = excluded.payload
                """,
                (
                    entry_id,
                    self._encode(
                        payload
                    ),
                ),
            )
