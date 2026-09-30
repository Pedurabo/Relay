const fs = require("fs");
const path = require("path");
const { RelayStorage } = require("./storage");

const databasePath =
    path.join(
        __dirname,
        "relay-maintenance-" +
            process.pid +
            ".sqlite"
    );

function cleanup() {

    for (
        const suffix of
        [
            "",
            "-wal",
            "-shm"
        ]
    ) {

        const file =
            databasePath +
            suffix;

        if (
            fs.existsSync(
                file
            )
        ) {
            fs.unlinkSync(
                file
            );
        }
    }
}

function assert(
    condition,
    message
) {

    if (!condition) {
        throw new Error(
            message
        );
    }
}

function main() {

    cleanup();

    const storage =
        new RelayStorage(
            databasePath
        );

    try {

        storage.insertUser({
            userId:
                "maintenance-user",
            username:
                "maintenance.user",
            displayName:
                "Maintenance User",
            passwordSalt:
                "salt",
            passwordHash:
                "hash",
            isAdmin:
                true
        });

        const now =
            Date.now();

        storage.saveSession({
            userId:
                "maintenance-user",
            userName:
                "Maintenance User",
            accessToken:
                "expired-access",
            refreshToken:
                "expired-refresh",
            accessTokenExpiresAt:
                now - 10_000,
            refreshTokenExpiresAt:
                now - 5_000
        });

        storage.saveSession({
            userId:
                "maintenance-user",
            userName:
                "Maintenance User",
            accessToken:
                "valid-access",
            refreshToken:
                "valid-refresh",
            accessTokenExpiresAt:
                now + 60_000,
            refreshTokenExpiresAt:
                now + 120_000
        });

        storage.upsertPushRegistration(
            "maintenance-user",
            "stale-push-token"
        );

        storage.upsertPushRegistration(
            "maintenance-user",
            "fresh-push-token"
        );

        storage.database
            .prepare(
                `
                UPDATE push_registrations
                SET updated_at = ?
                WHERE token = ?
                `
            )
            .run(
                now -
                    31 * 24 * 60 * 60 * 1000,
                "stale-push-token"
            );

        const sessionResult =
            storage
                .pruneExpiredSessions(
                    now
                );

        const pushResult =
            storage
                .pruneStalePushRegistrations(
                    now -
                        30 * 24 * 60 * 60 * 1000
                );

        assert(
            sessionResult.refreshSessions ===
                1,
            "Expected one expired refresh session to be pruned."
        );

        assert(
            storage.getAccessSession(
                "expired-access"
            ) ===
                null,
            "Expired access token survived pruning."
        );

        assert(
            storage.getRefreshSession(
                "expired-refresh"
            ) ===
                null,
            "Expired refresh token survived pruning."
        );

        assert(
            storage
                .getAccessSession(
                    "valid-access"
                )
                ?.userId ===
                "maintenance-user",
            "Valid access session was pruned."
        );

        assert(
            storage
                .getRefreshSession(
                    "valid-refresh"
                )
                ?.userId ===
                "maintenance-user",
            "Valid refresh session was pruned."
        );

        assert(
            pushResult ===
                1,
            "Expected one stale push registration to be pruned."
        );

        assert(
            storage
                .countPushRegistrationsForUser(
                    "maintenance-user"
                ) ===
                1,
            "Fresh push registration did not survive pruning."
        );

        const remainingPush =
            storage.database
                .prepare(
                    `
                    SELECT token
                    FROM push_registrations
                    WHERE user_id = ?
                    `
                )
                .all(
                    "maintenance-user"
                );

        assert(
            remainingPush.length ===
                1 &&
            remainingPush[0].token ===
                "fresh-push-token",
            "Wrong push registration survived pruning."
        );

        console.log(
            "SESSION_PRUNING_GREEN"
        );

    } finally {

        storage.close();
        cleanup();
    }
}

try {

    main();

} catch (error) {

    cleanup();

    console.error(
        error
    );

    process.exitCode =
        1;
}
