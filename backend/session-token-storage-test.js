const fs = require("fs");
const path = require("path");
const { DatabaseSync } = require("node:sqlite");
const { RelayStorage } = require("./storage");

const databasePath =
    path.join(
        __dirname,
        "relay-token-storage-" +
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

function createUser(
    storage
) {

    storage.insertUser({
        userId:
            "token-user",
        username:
            "token.user",
        displayName:
            "Token User",
        passwordSalt:
            "salt",
        passwordHash:
            "hash",
        isAdmin:
            false
    });
}

function inspectTokens() {

    const db =
        new DatabaseSync(
            databasePath
        );

    try {

        return {
            refresh:
                db.prepare(
                    `
                    SELECT
                        refresh_token AS token
                    FROM refresh_sessions
                    ORDER BY created_at
                    `
                )
                    .all(),

            access:
                db.prepare(
                    `
                    SELECT
                        access_token AS accessToken,
                        refresh_token AS refreshToken
                    FROM access_sessions
                    ORDER BY created_at
                    `
                )
                    .all()
        };

    } finally {

        db.close();
    }
}

function assertAllDigested(
    rows
) {

    for (
        const row of
        rows.refresh
    ) {

        assert(
            String(
                row.token
            )
                .startsWith(
                    "sha256:"
                ),
            "Refresh token was stored in plaintext."
        );
    }

    for (
        const row of
        rows.access
    ) {

        assert(
            String(
                row.accessToken
            )
                .startsWith(
                    "sha256:"
                ),
            "Access token was stored in plaintext."
        );

        assert(
            String(
                row.refreshToken
            )
                .startsWith(
                    "sha256:"
                ),
            "Access-session refresh reference was stored in plaintext."
        );
    }
}

function main() {

    cleanup();

    let storage =
        new RelayStorage(
            databasePath
        );

    createUser(
        storage
    );

    const session = {
        userId:
            "token-user",
        userName:
            "Token User",
        accessToken:
            "plaintext-access-token",
        refreshToken:
            "plaintext-refresh-token",
        accessTokenExpiresAt:
            Date.now() +
            60_000,
        refreshTokenExpiresAt:
            Date.now() +
            120_000
    };

    storage.saveSession(
        session
    );

    assert(
        storage
            .getAccessSession(
                session.accessToken
            )
            ?.userId ===
            session.userId,
        "Digested access token lookup failed."
    );

    assert(
        storage
            .getRefreshSession(
                session.refreshToken
            )
            ?.userId ===
            session.userId,
        "Digested refresh token lookup failed."
    );

    storage.close();

    let rows =
        inspectTokens();

    assertAllDigested(
        rows
    );

    assert(
        JSON.stringify(
            rows
        )
            .includes(
                session.accessToken
            ) ===
            false,
        "Plaintext access token was visible in SQLite."
    );

    assert(
        JSON.stringify(
            rows
        )
            .includes(
                session.refreshToken
            ) ===
            false,
        "Plaintext refresh token was visible in SQLite."
    );

    storage =
        new RelayStorage(
            databasePath
        );

    const rotatedSession = {
        userId:
            session.userId,
        userName:
            session.userName,
        accessToken:
            "rotated-access-token",
        refreshToken:
            "rotated-refresh-token",
        accessTokenExpiresAt:
            Date.now() +
            60_000,
        refreshTokenExpiresAt:
            Date.now() +
            120_000
    };

    assert(
        storage.rotateSession(
            session.refreshToken,
            rotatedSession
        ) ===
        true,
        "Digested refresh token rotation failed."
    );

    assert(
        storage.getAccessSession(
            session.accessToken
        ) ===
        null,
        "Old access token survived refresh-family rotation."
    );

    assert(
        storage.getRefreshSession(
            session.refreshToken
        ) ===
        null,
        "Old refresh token survived rotation."
    );

    assert(
        storage
            .getAccessSession(
                rotatedSession.accessToken
            )
            ?.userId ===
            session.userId,
        "Rotated access token lookup failed."
    );

    storage.close();

    rows =
        inspectTokens();

    assertAllDigested(
        rows
    );

    assert(
        !JSON.stringify(
            rows
        )
            .includes(
                rotatedSession.accessToken
            ),
        "Rotated access token leaked into SQLite."
    );

    assert(
        !JSON.stringify(
            rows
        )
            .includes(
                rotatedSession.refreshToken
            ),
        "Rotated refresh token leaked into SQLite."
    );

    cleanup();

    storage =
        new RelayStorage(
            databasePath
        );

    createUser(
        storage
    );

    storage.close();

    const legacyAccessToken =
        "legacy-plaintext-access";

    const legacyRefreshToken =
        "legacy-plaintext-refresh";

    const db =
        new DatabaseSync(
            databasePath
        );

    try {

        db.exec(
            "PRAGMA foreign_keys = ON"
        );

        db.prepare(
            `
            INSERT INTO refresh_sessions (
                refresh_token,
                user_id,
                expires_at,
                created_at
            )
            VALUES (?, ?, ?, ?)
            `
        )
            .run(
                legacyRefreshToken,
                "token-user",
                Date.now() +
                    120_000,
                Date.now()
            );

        db.prepare(
            `
            INSERT INTO access_sessions (
                access_token,
                refresh_token,
                user_id,
                expires_at,
                created_at
            )
            VALUES (?, ?, ?, ?, ?)
            `
        )
            .run(
                legacyAccessToken,
                legacyRefreshToken,
                "token-user",
                Date.now() +
                    60_000,
                Date.now()
            );

    } finally {

        db.close();
    }

    storage =
        new RelayStorage(
            databasePath
        );

    assert(
        storage
            .getAccessSession(
                legacyAccessToken
            )
            ?.userId ===
            "token-user",
        "Migrated legacy access token stopped working."
    );

    assert(
        storage
            .getRefreshSession(
                legacyRefreshToken
            )
            ?.userId ===
            "token-user",
        "Migrated legacy refresh token stopped working."
    );

    storage.close();

    rows =
        inspectTokens();

    assertAllDigested(
        rows
    );

    assert(
        !JSON.stringify(
            rows
        )
            .includes(
                legacyAccessToken
            ),
        "Legacy plaintext access token remained after migration."
    );

    assert(
        !JSON.stringify(
            rows
        )
            .includes(
                legacyRefreshToken
            ),
        "Legacy plaintext refresh token remained after migration."
    );

    cleanup();

    console.log(
        "SESSION_TOKEN_STORAGE_GREEN"
    );
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
