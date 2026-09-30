const fs = require("fs");
const path = require("path");
const {
    RelayStorage
} = require("./storage");

const databasePath =
    path.join(
        __dirname,
        "relay-storage-test.sqlite"
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

function userRecord(
    userId,
    username,
    isAdmin
) {

    return {
        userId,
        username,
        displayName:
            username,
        passwordSalt:
            "salt-" +
            userId,
        passwordHash:
            "hash-" +
            userId,
        isAdmin
    };
}

function main() {

    cleanup();

    let storage =
        new RelayStorage(
            databasePath
        );

    storage.insertUser(
        userRecord(
            "user-admin",
            "admin",
            true
        )
    );

    storage.insertUser(
        userRecord(
            "user-operator",
            "operator",
            false
        )
    );

    storage.saveSession({
        userId:
            "user-admin",
        userName:
            "admin",
        accessToken:
            "access-1",
        refreshToken:
            "refresh-1",
        accessTokenExpiresAt:
            Date.now() +
            60_000,
        refreshTokenExpiresAt:
            Date.now() +
            120_000
    });

    storage.close();

    storage =
        new RelayStorage(
            databasePath
        );

    assert(
        storage
            .getUserByUsername(
                "admin"
            )
            ?.userId ===
            "user-admin",
        "User did not survive database reopen."
    );

    assert(
        storage
            .getAccessSession(
                "access-1"
            )
            ?.userId ===
            "user-admin",
        "Access session did not survive database reopen."
    );

    assert(
        storage
            .getRefreshSession(
                "refresh-1"
            )
            ?.userId ===
            "user-admin",
        "Refresh session did not survive database reopen."
    );

    assert(
        storage.canAccessIncident(
            "user-admin",
            "INC-DB"
        ),
        "Admin should have incident access."
    );

    assert(
        !storage.canAccessIncident(
            "user-operator",
            "INC-DB"
        ),
        "Non-admin should not have implicit incident access."
    );

    storage.grantIncidentAccess(
        "user-operator",
        "INC-DB",
        "operator"
    );

    assert(
        storage.canAccessIncident(
            "user-operator",
            "INC-DB"
        ),
        "Explicit incident access grant was not honored."
    );

    storage.revokeRefreshSession(
        "refresh-1"
    );

    assert(
        storage.getRefreshSession(
            "refresh-1"
        ) ===
        null,
        "Refresh session survived revocation."
    );

    assert(
        storage.getAccessSession(
            "access-1"
        ) ===
        null,
        "Access session survived parent refresh revocation."
    );

    storage.close();

    cleanup();

    console.log(
        "SQLITE_STORAGE_GREEN"
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
