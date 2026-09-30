const fs = require("fs");
const path = require("path");
const {
    RelayStorage
} = require("./storage");

const databasePath =
    path.join(
        __dirname,
        "relay-storage-test-" +
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
        null;

    try {

        storage =
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

    const incident =
        storage.ensureIncident(
            "INC-DB",
            {
                title:
                    "Database incident",
                status:
                    "Active",
                severity:
                    "MEDIUM",
                sequence:
                    10,
                serverOwned:
                    true
            }
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

    assert(
        incident.sequence ===
            10,
        "Incident seed sequence was not stored."
    );

    const applied =
        storage.applySeverityCommand(
            "CMD-DB-1",
            "INC-DB",
            "HIGH",
            1000
        );

    assert(
        applied.duplicate ===
            false,
        "First severity command was incorrectly deduped."
    );

    assert(
        applied.incident.severity ===
            "HIGH",
        "Severity update was not persisted."
    );

    assert(
        Number(
            applied.incident.sequence
        ) ===
            11,
        "Server-owned sequence did not advance."
    );

    const duplicate =
        storage.applySeverityCommand(
            "CMD-DB-1",
            "INC-DB",
            "CRITICAL",
            2000
        );

    assert(
        duplicate.duplicate ===
            true,
        "Duplicate severity command was re-applied."
    );

    assert(
        duplicate.incident.severity ===
            "HIGH",
        "Duplicate command changed authoritative severity."
    );

    const timeline =
        storage.saveTimelineEntry({
            type:
                "timeline.entry.added",
            eventId:
                "EVT-TIMELINE-DB-1",
            incidentId:
                "INC-DB",
            occurredAt:
                1500,
            entryId:
                "ENTRY-DB-1",
            message:
                "Persist me",
            author:
                "Operator"
        });

    assert(
        timeline.entryId ===
            "ENTRY-DB-1",
        "Timeline entry was not stored."
    );

    storage.close();

    storage =
        new RelayStorage(
            databasePath
        );

    assert(
        storage
            .getProcessedCommand(
                "CMD-DB-1"
            )
            ?.incidentId ===
            "INC-DB",
        "Processed command dedupe did not survive reopen."
    );

    assert(
        storage
            .getTimelineEntry(
                "ENTRY-DB-1"
            )
            ?.message ===
            "Persist me",
        "Timeline entry did not survive reopen."
    );

    const replay =
        storage.getReplay(
            "INC-DB",
            11,
            11
        );

    assert(
        replay.length ===
            1,
        "Ordered incident history did not survive reopen."
    );

    assert(
        replay[0].severity ===
            "HIGH",
        "Replay event payload was incorrect."
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
        storage =
            null;

        console.log(
            "SQLITE_AUTHORITATIVE_STATE_GREEN"
        );

    } finally {

        if (
            storage != null
        ) {

            storage.close();
        }

        cleanup();
    }
}

try {

    main();

} catch (error) {

    console.error(
        error
    );

    process.exitCode =
        1;
}
