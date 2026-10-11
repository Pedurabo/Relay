from __future__ import annotations

import os
from pathlib import Path
from dataclasses import dataclass


VALID_ENVIRONMENTS = {
    "development",
    "test",
    "production",
}


def _positive_int(
    name: str,
    default: int,
) -> int:

    raw = os.getenv(
        name,
        str(default),
    ).strip()

    try:
        value = int(raw)
    except ValueError as error:
        raise ValueError(
            f"{name} must be an integer."
        ) from error

    if value <= 0:
        raise ValueError(
            f"{name} must be greater than zero."
        )

    return value


@dataclass(frozen=True)
class RelaySettings:
    environment: str
    host: str
    port: int
    access_token_ttl_seconds: int
    refresh_token_ttl_seconds: int
    database_path: str

    @property
    def development_routes_enabled(
        self,
    ) -> bool:

        return (
            self.environment
            == "development"
        )


def load_settings() -> RelaySettings:

    environment = (
        os.getenv(
            "RELAY_ENVIRONMENT",
            "development",
        )
        .strip()
        .lower()
    )

    if environment not in VALID_ENVIRONMENTS:
        raise ValueError(
            "RELAY_ENVIRONMENT must be one of: "
            "development, test, production."
        )

    host = (
        os.getenv(
            "RELAY_BACKEND_HOST",
            "127.0.0.1",
        )
        .strip()
    )

    if not host:
        raise ValueError(
            "RELAY_BACKEND_HOST must not be blank."
        )

    return RelaySettings(
        environment=environment,
        host=host,
        port=_positive_int(
            "RELAY_BACKEND_PORT",
            9000,
        ),
        access_token_ttl_seconds=
            _positive_int(
                "RELAY_ACCESS_TOKEN_TTL_SECONDS",

                3600,
            ),
        refresh_token_ttl_seconds=
            _positive_int(
                "RELAY_REFRESH_TOKEN_TTL_SECONDS",

                2_592_000,
            ),
        database_path=(
            os.getenv(
                "RELAY_DATABASE_PATH",
                str(
                    Path(__file__)
                    .resolve()
                    .with_name(
                        "relay.sqlite"
                    )
                ),
            )
            .strip()
        ),
    )


settings = load_settings()
