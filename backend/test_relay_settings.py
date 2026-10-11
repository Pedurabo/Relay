from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from relay_settings import load_settings


class RelaySettingsTest(
    unittest.TestCase
):

    def test_defaults_are_local_development(
        self,
    ) -> None:

        with patch.dict(
            os.environ,
            {},
            clear=True,
        ):
            settings = load_settings()

        self.assertEqual(
            "development",
            settings.environment,
        )

        self.assertEqual(
            "127.0.0.1",
            settings.host,
        )

        self.assertEqual(
            9000,
            settings.port,
        )

        self.assertEqual(
            3600,
            settings.access_token_ttl_seconds,
        )

        self.assertTrue(
            settings.development_routes_enabled
        )

    def test_environment_overrides_are_loaded(
        self,
    ) -> None:

        with patch.dict(
            os.environ,
            {
                "RELAY_ENVIRONMENT":
                    "production",
                "RELAY_BACKEND_HOST":
                    "0.0.0.0",
                "RELAY_BACKEND_PORT":
                    "8080",
                "RELAY_ACCESS_TOKEN_TTL_SECONDS":
                    "900",
            },
            clear=True,
        ):
            settings = load_settings()

        self.assertEqual(
            "production",
            settings.environment,
        )

        self.assertEqual(
            "0.0.0.0",
            settings.host,
        )

        self.assertEqual(
            8080,
            settings.port,
        )

        self.assertEqual(
            900,
            settings.access_token_ttl_seconds,
        )

        self.assertFalse(
            settings.development_routes_enabled
        )

    def test_invalid_environment_fails(
        self,
    ) -> None:

        with patch.dict(
            os.environ,
            {
                "RELAY_ENVIRONMENT":
                    "staging-ish",
            },
            clear=True,
        ):
            with self.assertRaises(
                ValueError
            ):
                load_settings()

    def test_invalid_port_fails(
        self,
    ) -> None:

        with patch.dict(
            os.environ,
            {
                "RELAY_BACKEND_PORT":
                    "zero",
            },
            clear=True,
        ):
            with self.assertRaises(
                ValueError
            ):
                load_settings()


if __name__ == "__main__":
    unittest.main()
