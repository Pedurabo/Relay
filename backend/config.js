const path = require("path");

function parsePositiveInteger(
    value,
    fallback,
    name
) {

    const resolved =
        value == null ||
        value === ""
            ? fallback
            : Number(
                value
            );

    if (
        !Number.isInteger(
            resolved
        ) ||
        resolved <=
            0
    ) {
        throw new Error(
            "Invalid " +
                name +
                ": expected a positive integer."
        );
    }

    return resolved;
}

function loadConfig(
    env =
        process.env,
    backendDir =
        __dirname
) {

    const environment =
        String(
            env.RELAY_ENV ||
            "development"
        )
            .trim()
            .toLowerCase();

    if (
        ![
            "development",
            "test",
            "production"
        ].includes(
            environment
        )
    ) {
        throw new Error(
            "Invalid RELAY_ENV: " +
                environment
        );
    }

    const port =
        parsePositiveInteger(
            env.RELAY_PORT,
            9000,
            "RELAY_PORT"
        );

    if (
        port >
        65535
    ) {
        throw new Error(
            "Invalid RELAY_PORT: must be <= 65535."
        );
    }

    const accessTtlMs =
        parsePositiveInteger(
            env.RELAY_ACCESS_TTL_MS,
            60 * 60 * 1000,
            "RELAY_ACCESS_TTL_MS"
        );

    const refreshTtlMs =
        parsePositiveInteger(
            env.RELAY_REFRESH_TTL_MS,
            7 * 24 * 60 * 60 * 1000,
            "RELAY_REFRESH_TTL_MS"
        );

    if (
        refreshTtlMs <=
        accessTtlMs
    ) {
        throw new Error(
            "RELAY_REFRESH_TTL_MS must be greater than RELAY_ACCESS_TTL_MS."
        );
    }

    const explicitDatabasePath =
        String(
            env.RELAY_DATABASE_PATH ||
            ""
        )
            .trim();

    const databasePath =
        explicitDatabasePath ||
        path.join(
            backendDir,
            "relay.sqlite"
        );

    const bootstrapUsername =
        String(
            env.RELAY_BOOTSTRAP_USERNAME ||
            "relay.operator"
        )
            .trim()
            .toLowerCase();

    const bootstrapPassword =
        String(
            env.RELAY_BOOTSTRAP_PASSWORD ||
            "RelayDemo123!"
        );

    const testShutdownEnabled =
        env.RELAY_ENABLE_TEST_SHUTDOWN ===
        "1";

    if (
        environment ===
        "production"
    ) {

        if (
            !explicitDatabasePath
        ) {
            throw new Error(
                "RELAY_DATABASE_PATH is required in production."
            );
        }

        if (
            databasePath ===
            ":memory:"
        ) {
            throw new Error(
                "In-memory SQLite is not allowed in production."
            );
        }

        if (
            env.RELAY_BOOTSTRAP_USERNAME ||
            env.RELAY_BOOTSTRAP_PASSWORD
        ) {
            throw new Error(
                "Development bootstrap credentials are forbidden in production."
            );
        }

        if (
            testShutdownEnabled
        ) {
            throw new Error(
                "RELAY_ENABLE_TEST_SHUTDOWN is forbidden in production."
            );
        }
    }

    return {
        environment,
        isProduction:
            environment ===
            "production",
        port,
        databasePath,
        accessTtlMs,
        refreshTtlMs,
        testShutdownEnabled,
        allowDevelopmentBootstrap:
            environment !==
            "production",
        bootstrapUsername,
        bootstrapPassword,
        bootstrapUserId:
            "dev-relay-operator",
        bootstrapDisplayName:
            "Relay Operator"
    };
}

module.exports = {
    loadConfig
};
