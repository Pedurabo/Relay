from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from fastapi import HTTPException

import relay_dev_server
from relay_store import RelayStore


class RelayReadinessTest(
    unittest.IsolatedAsyncioTestCase
):

    def test_store_readiness_probe_accepts_healthy_sqlite(
        self,
    ) -> None:

        with tempfile.TemporaryDirectory() as directory:

            store = RelayStore(
                str(
                    Path(directory)
                    / "relay-ready.sqlite"
                )
            )

            store.check_ready()


    async def test_ready_endpoint_reports_ready(
        self,
    ) -> None:

        class ReadyStore:

            def check_ready(
                self,
            ) -> None:
                return None

        original_store = relay_dev_server.store

        try:
            relay_dev_server.store = ReadyStore()

            result = await relay_dev_server.ready()

            self.assertEqual(
                {
                    "status": "ready"
                },
                result,
            )

        finally:
            relay_dev_server.store = original_store


    async def test_ready_endpoint_reports_503_when_store_fails(
        self,
    ) -> None:

        class FailingStore:

            def check_ready(
                self,
            ) -> None:
                raise RuntimeError(
                    "database unavailable"
                )

        original_store = relay_dev_server.store

        try:
            relay_dev_server.store = FailingStore()

            with self.assertRaises(
                HTTPException
            ) as captured:

                await relay_dev_server.ready()

            self.assertEqual(
                503,
                captured.exception.status_code,
            )

            self.assertEqual(
                "backend_not_ready",
                captured.exception.detail,
            )

        finally:
            relay_dev_server.store = original_store


if __name__ == "__main__":
    unittest.main()
