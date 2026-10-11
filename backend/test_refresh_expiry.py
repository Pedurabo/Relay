import os
import unittest
from unittest.mock import patch

import relay_dev_server
import relay_settings


class RefreshExpiryTest(
    unittest.TestCase
):

    def test_default_refresh_ttl_is_thirty_days(
        self,
    ) -> None:

        with patch.dict(
            os.environ,
            {},
            clear=True,
        ):
            settings = (
                relay_settings.load_settings()
            )

        self.assertEqual(
            2_592_000,
            settings.refresh_token_ttl_seconds,
        )


    def test_refresh_ttl_environment_override(
        self,
    ) -> None:

        with patch.dict(
            os.environ,
            {
                "RELAY_REFRESH_TOKEN_TTL_SECONDS":
                    "7200",
            },
            clear=True,
        ):
            settings = (
                relay_settings.load_settings()
            )

        self.assertEqual(
            7200,
            settings.refresh_token_ttl_seconds,
        )


    def test_invalid_refresh_ttl_is_rejected(
        self,
    ) -> None:

        with patch.dict(
            os.environ,
            {
                "RELAY_REFRESH_TOKEN_TTL_SECONDS":
                    "0",
            },
            clear=True,
        ):
            with self.assertRaises(
                ValueError
            ):
                relay_settings.load_settings()


    def test_expired_refresh_principal_is_expired(
        self,
    ) -> None:

        with patch.object(
            relay_dev_server,
            "now_ms",
            return_value=10_000,
        ):
            self.assertTrue(
                relay_dev_server
                .refresh_session_is_expired(
                    {
                        "expiresAt": 9_999,
                    }
                )
            )


    def test_future_refresh_principal_is_valid(
        self,
    ) -> None:

        with patch.object(
            relay_dev_server,
            "now_ms",
            return_value=10_000,
        ):
            self.assertFalse(
                relay_dev_server
                .refresh_session_is_expired(
                    {
                        "expiresAt": 10_001,
                    }
                )
            )


    def test_missing_refresh_expiry_is_rejected(
        self,
    ) -> None:

        self.assertTrue(
            relay_dev_server
            .refresh_session_is_expired(
                {
                    "userId": "USER-1",
                    "userName": "user",
                }
            )
        )


    def test_issue_session_persists_refresh_expiry(
        self,
    ) -> None:

        import tempfile
        from pathlib import Path

        from relay_store import RelayStore

        original_store = (
            relay_dev_server.store
        )

        original_refresh_tokens = (
            relay_dev_server.refresh_tokens
        )

        try:
            with tempfile.TemporaryDirectory() as directory:

                store = RelayStore(
                    str(
                        Path(directory)
                        / "relay-test.sqlite"
                    )
                )

                relay_dev_server.store = (
                    store
                )

                relay_dev_server.refresh_tokens = {}

                with patch.object(
                    relay_dev_server,
                    "now_ms",
                    return_value=1_000_000,
                ):
                    session = (
                        relay_dev_server.issue_session(
                            username="expiry-user",
                            user_id="USER-EXPIRY",
                        )
                    )

                refresh_token = session[
                    "refreshToken"
                ]

                token_hash = (
                    relay_dev_server
                    .refresh_token_hash(
                        refresh_token
                    )
                )

                persisted = (
                    store
                    .load_refresh_sessions()[
                        token_hash
                    ]
                )

                self.assertEqual(
                    1_000_000
                    + (
                        relay_dev_server
                        .settings
                        .refresh_token_ttl_seconds
                        * 1000
                    ),
                    persisted[
                        "expiresAt"
                    ],
                )

                self.assertEqual(
                    persisted,
                    relay_dev_server
                    .refresh_tokens[
                        token_hash
                    ],
                )

        finally:
            relay_dev_server.store = (
                original_store
            )

            relay_dev_server.refresh_tokens = (
                original_refresh_tokens
            )


if __name__ == "__main__":
    unittest.main()
