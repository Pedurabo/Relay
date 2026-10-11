from __future__ import annotations

import sqlite3
import tempfile
import unittest
from pathlib import Path

from relay_store import RelayStore


class RelayStoreTest(
    unittest.TestCase
):

    def test_state_survives_new_store_instance(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            first = RelayStore(
                database_path
            )

            incident = {
                "id": "INC-TEST-1",
                "title": "Persistence test",
                "status": "Investigating",
                "severity": "HIGH",
                "sequence": 1,
            }

            event = {
                "type": "incident.created",
                "eventId": "EVT-TEST-1",
                "incidentId": "INC-TEST-1",
                "occurredAt": 123,
                "title": "Persistence test",
                "status": "Investigating",
                "severity": "HIGH",
                "sequence": 1,
            }

            ack = {
                "type": "command.accepted",
                "command": "incident.create",
                "commandId": "CMD-TEST-1",
                "incidentId": "INC-TEST-1",
            }

            timeline = {
                "type": "timeline.entry.added",
                "eventId": "EVT-TIMELINE-1",
                "incidentId": "INC-TEST-1",
                "occurredAt": 124,
                "entryId": "ENTRY-TEST-1",
                "message": "Persist me",
                "author": "Tester",
            }

            first.upsert_incident(
                incident
            )

            first.append_incident_event(
                "INC-TEST-1",
                event,
            )

            first.put_processed_command(
                "CMD-TEST-1",
                ack,
            )

            first.put_timeline_event(
                "ENTRY-TEST-1",
                timeline,
            )

            second = RelayStore(
                database_path
            )

            self.assertEqual(
                incident,
                second.load_incidents()[
                    "INC-TEST-1"
                ],
            )

            self.assertEqual(
                [event],
                second.load_incident_event_history()[
                    "INC-TEST-1"
                ],
            )

            self.assertEqual(
                ack,
                second.load_processed_commands()[
                    "CMD-TEST-1"
                ],
            )

            self.assertEqual(
                timeline,
                second.load_timeline_events()[
                    "ENTRY-TEST-1"
                ],
            )

    def test_duplicate_event_id_is_idempotent(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            store = RelayStore(
                database_path
            )

            event = {
                "type": "incident.updated",
                "eventId": "EVT-SAME",
                "incidentId": "INC-1",
                "occurredAt": 1,
                "status": "Monitoring",
                "sequence": 2,
            }

            store.append_incident_event(
                "INC-1",
                event,
            )

            store.append_incident_event(
                "INC-1",
                event,
            )

            self.assertEqual(
                [event],
                store.load_incident_event_history()[
                    "INC-1"
                ],
            )

    def test_incident_upsert_replaces_authoritative_snapshot(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            store = RelayStore(
                database_path
            )

            store.upsert_incident(
                {
                    "id": "INC-1",
                    "title": "Test",
                    "status": "Investigating",
                    "severity": "MEDIUM",
                    "sequence": 1,
                }
            )

            updated = {
                "id": "INC-1",
                "title": "Test",
                "status": "Monitoring",
                "severity": "HIGH",
                "sequence": 3,
            }

            store.upsert_incident(
                updated
            )

            self.assertEqual(
                updated,
                store.load_incidents()[
                    "INC-1"
                ],
            )


    def test_accepted_incident_command_is_atomic(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            store = RelayStore(
                database_path
            )

            incident = {
                "id": "INC-ATOMIC-1",
                "title": "Atomic persistence",
                "status": "Monitoring",
                "severity": "HIGH",
                "sequence": 4,
            }

            event = {
                "type": "incident.updated",
                "eventId": "EVT-ATOMIC-1",
                "incidentId": "INC-ATOMIC-1",
                "occurredAt": 456,
                "status": "Monitoring",
                "sequence": 4,
            }

            ack = {
                "type": "command.accepted",
                "command": "incident.status.update",
                "commandId": "CMD-ATOMIC-1",
                "incidentId": "INC-ATOMIC-1",
            }

            store.persist_accepted_incident_command(
                incident=incident,
                event=event,
                command_id="CMD-ATOMIC-1",
                ack=ack,
            )

            restarted = RelayStore(
                database_path
            )

            self.assertEqual(
                incident,
                restarted.load_incidents()[
                    "INC-ATOMIC-1"
                ],
            )

            self.assertEqual(
                [event],
                restarted.load_incident_event_history()[
                    "INC-ATOMIC-1"
                ],
            )

            self.assertEqual(
                ack,
                restarted.load_processed_commands()[
                    "CMD-ATOMIC-1"
                ],
            )


    def test_accepted_incident_command_rolls_back_all_rows(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            store = RelayStore(
                database_path
            )

            connection = sqlite3.connect(
                database_path
            )

            try:
                connection.execute(
                    """
                    CREATE TRIGGER fail_atomic_command_ack
                    BEFORE INSERT ON processed_commands
                    BEGIN
                        SELECT RAISE(
                            ABORT,
                            'forced atomic rollback'
                        );
                    END
                    """
                )

                connection.commit()

            finally:
                connection.close()

            incident = {
                "id": "INC-ROLLBACK-1",
                "title": "Rollback",
                "status": "Investigating",
                "severity": "MEDIUM",
                "sequence": 1,
            }

            event = {
                "type": "incident.created",
                "eventId": "EVT-ROLLBACK-1",
                "incidentId": "INC-ROLLBACK-1",
                "occurredAt": 789,
                "title": "Rollback",
                "status": "Investigating",
                "severity": "MEDIUM",
                "sequence": 1,
            }

            ack = {
                "type": "command.accepted",
                "command": "incident.create",
                "commandId": "CMD-ROLLBACK-1",
                "incidentId": "INC-ROLLBACK-1",
            }

            with self.assertRaises(
                sqlite3.DatabaseError
            ):
                store.persist_accepted_incident_command(
                    incident=incident,
                    event=event,
                    command_id="CMD-ROLLBACK-1",
                    ack=ack,
                )

            restarted = RelayStore(
                database_path
            )

            self.assertNotIn(
                "INC-ROLLBACK-1",
                restarted.load_incidents(),
            )

            self.assertNotIn(
                "INC-ROLLBACK-1",
                restarted.load_incident_event_history(),
            )

            self.assertNotIn(
                "CMD-ROLLBACK-1",
                restarted.load_processed_commands(),
            )


    def test_refresh_session_hash_survives_restart(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            first = RelayStore(
                database_path
            )

            token_hash = (
                "a" * 64
            )

            principal = {
                "userId": "USER-HASH",
                "userName": "hash-user",
            }

            first.put_refresh_session(
                token_hash,
                principal,
            )

            second = RelayStore(
                database_path
            )

            self.assertEqual(
                principal,
                second.load_refresh_sessions()[
                    token_hash
                ],
            )


    def test_refresh_session_rotation_is_atomic(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            store = RelayStore(
                database_path
            )

            old_hash = (
                "b" * 64
            )

            new_hash = (
                "c" * 64
            )

            principal = {
                "userId": "USER-ROTATE",
                "userName": "rotate-user",
            }

            store.put_refresh_session(
                old_hash,
                principal,
            )

            store.rotate_refresh_session(
                old_token_hash=old_hash,
                new_token_hash=new_hash,
                principal=principal,
            )

            sessions = (
                RelayStore(
                    database_path
                )
                .load_refresh_sessions()
            )

            self.assertNotIn(
                old_hash,
                sessions,
            )

            self.assertEqual(
                principal,
                sessions[
                    new_hash
                ],
            )


    def test_refresh_rotation_missing_old_hash_rolls_back(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            store = RelayStore(
                database_path
            )

            with self.assertRaises(
                ValueError
            ):
                store.rotate_refresh_session(
                    old_token_hash=("d" * 64),
                    new_token_hash=("e" * 64),
                    principal={
                        "userId": "USER-MISSING",
                        "userName": "missing-user",
                    },
                )

            self.assertEqual(
                {},
                store.load_refresh_sessions(),
            )


    def test_refresh_session_delete_is_durable(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            database_path = str(
                Path(directory)
                / "relay-test.sqlite"
            )

            store = RelayStore(
                database_path
            )

            token_hash = (
                "f" * 64
            )

            store.put_refresh_session(
                token_hash,
                {
                    "userId": "USER-DELETE",
                    "userName": "delete-user",
                },
            )

            store.delete_refresh_session(
                token_hash
            )

            self.assertEqual(
                {},
                RelayStore(
                    database_path
                ).load_refresh_sessions(),
            )


if __name__ == "__main__":
    unittest.main()
