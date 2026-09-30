const fs = require("fs");
const path = require("path");
const { RelayStorage } = require("./storage");

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

function main() {

    const source =
        path.join(
            __dirname,
            "relay-backup-source-" +
                process.pid +
                ".sqlite"
        );

    const backup =
        path.join(
            __dirname,
            "relay-backup-copy-" +
                process.pid +
                ".sqlite"
        );

    cleanup(
        source
    );

    cleanup(
        backup
    );

    let storage =
        new RelayStorage(
            source
        );

    storage.insertUser({
        userId:
            "backup-user",
        username:
            "backup.user",
        displayName:
            "Backup User",
        passwordSalt:
            "salt",
        passwordHash:
            "hash",
        isAdmin:
            true
    });

    storage.ensureIncident(
        "INC-BACKUP",
        {
            title:
                "Backup incident",
            status:
                "Active",
            severity:
                "MEDIUM",
            sequence:
                5,
            serverOwned:
                true
        }
    );

    storage.applySeverityCommand(
        "CMD-BACKUP-1",
        "INC-BACKUP",
        "HIGH",
        1000
    );

    storage.backupTo(
        backup
    );

    storage.applySeverityCommand(
        "CMD-BACKUP-2",
        "INC-BACKUP",
        "CRITICAL",
        2000
    );

    storage.close();

    storage =
        new RelayStorage(
            backup
        );

    assert(
        storage.integrityCheck(),
        "Backup integrity check failed."
    );

    assert(
        storage
            .getUserByUsername(
                "backup.user"
            )
            ?.userId ===
            "backup-user",
        "Backup user was missing."
    );

    assert(
        storage
            .getIncident(
                "INC-BACKUP"
            )
            ?.severity ===
            "HIGH",
        "Backup was not a point-in-time snapshot."
    );

    assert(
        storage
            .getProcessedCommand(
                "CMD-BACKUP-1"
            ) !=
            null,
        "Committed dedupe record missing from backup."
    );

    assert(
        storage
            .getProcessedCommand(
                "CMD-BACKUP-2"
            ) ==
            null,
        "Post-backup mutation leaked into snapshot."
    );

    storage.close();

    cleanup(
        source
    );

    cleanup(
        backup
    );

    console.log(
        "BACKUP_RESTORE_GREEN"
    );
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
