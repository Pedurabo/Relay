const fs = require("fs");
const path = require("path");
const { DatabaseSync } = require("node:sqlite");
const { RelayStorage } = require("./storage");

const freshPath =
    path.join(
        __dirname,
        "relay-schema-fresh-" +
            process.pid +
            ".sqlite"
    );

const legacyPath =
    path.join(
        __dirname,
        "relay-schema-legacy-" +
            process.pid +
            ".sqlite"
    );

const futurePath =
    path.join(
        __dirname,
        "relay-schema-future-" +
            process.pid +
            ".sqlite"
    );

function cleanup(
    base
) {

    for (
        const suffix of
        [
            "",
            "-wal",
            "-shm"
        ]
    ) {

        const file =
            base +
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

function schemaVersion(
    databasePath
) {

    const db =
        new DatabaseSync(
            databasePath
        );

    try {

        return Number(
            db.prepare(
                "PRAGMA user_version"
            )
                .get()
                .user_version
        );

    } finally {

        db.close();
    }
}

function setSchemaVersion(
    databasePath,
    version
) {

    const db =
        new DatabaseSync(
            databasePath
        );

    try {

        db.exec(
            "PRAGMA user_version = " +
                Number(
                    version
                )
        );

    } finally {

        db.close();
    }
}

function main() {

    cleanup(
        freshPath
    );

    cleanup(
        legacyPath
    );

    cleanup(
        futurePath
    );

    let storage =
        new RelayStorage(
            freshPath
        );

    storage.insertUser({
        userId:
            "fresh-user",
        username:
            "fresh.user",
        displayName:
            "Fresh User",
        passwordSalt:
            "salt",
        passwordHash:
            "hash",
        isAdmin:
            true
    });

    storage.close();

    assert(
        schemaVersion(
            freshPath
        ) ===
            1,
        "Fresh database was not migrated to schema version 1."
    );

    fs.copyFileSync(
        freshPath,
        legacyPath
    );

    setSchemaVersion(
        legacyPath,
        0
    );

    storage =
        new RelayStorage(
            legacyPath
        );

    assert(
        storage
            .getUserByUsername(
                "fresh.user"
            )
            ?.userId ===
            "fresh-user",
        "Legacy unversioned database lost existing data during adoption."
    );

    storage.close();

    assert(
        schemaVersion(
            legacyPath
        ) ===
            1,
        "Legacy unversioned database was not adopted as schema version 1."
    );

    fs.copyFileSync(
        freshPath,
        futurePath
    );

    setSchemaVersion(
        futurePath,
        999
    );

    let rejectedFuture =
        false;

    try {

        storage =
            new RelayStorage(
                futurePath
            );

        storage.close();

    } catch (error) {

        rejectedFuture =
            String(
                error.message
            )
                .includes(
                    "newer than supported version"
                );
    }

    assert(
        rejectedFuture,
        "Future database schema version was not rejected."
    );

    cleanup(
        freshPath
    );

    cleanup(
        legacyPath
    );

    cleanup(
        futurePath
    );

    console.log(
        "SQLITE_SCHEMA_MIGRATIONS_GREEN"
    );
}

try {

    main();

} catch (error) {

    cleanup(
        freshPath
    );

    cleanup(
        legacyPath
    );

    cleanup(
        futurePath
    );

    console.error(
        error
    );

    process.exitCode =
        1;
}
