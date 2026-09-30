const fs = require("fs");
const path = require("path");
const { RelayStorage } = require("./storage");

function main() {

    const sourcePath =
        String(
            process.env
                .RELAY_DATABASE_PATH ||
            ""
        )
            .trim();

    const destinationPath =
        path.resolve(
            process.argv[2] ||
            ""
        );

    if (
        !sourcePath
    ) {
        throw new Error(
            "RELAY_DATABASE_PATH is required."
        );
    }

    if (
        !process.argv[2]
    ) {
        throw new Error(
            "Backup destination path is required."
        );
    }

    if (
        fs.existsSync(
            destinationPath
        )
    ) {
        fs.unlinkSync(
            destinationPath
        );
    }

    const storage =
        new RelayStorage(
            sourcePath
        );

    try {

        if (
            !storage.integrityCheck()
        ) {
            throw new Error(
                "Source database failed integrity check."
            );
        }

        storage.backupTo(
            destinationPath
        );

    } finally {

        storage.close();
    }

    const backupStorage =
        new RelayStorage(
            destinationPath
        );

    try {

        if (
            !backupStorage
                .integrityCheck()
        ) {
            throw new Error(
                "Backup database failed integrity check."
            );
        }

    } finally {

        backupStorage.close();
    }

    console.log(
        "BACKUP_GREEN|" +
        destinationPath
    );
}

try {

    main();

} catch (error) {

    console.error(
        error.message
    );

    process.exitCode =
        1;
}
