const crypto = require("crypto");
const { RelayStorage } = require("./storage");
const { createUserRecord } = require("./credentials");

function required(
    name
) {

    const value =
        String(
            process.env[
                name
            ] ||
            ""
        )
            .trim();

    if (
        !value
    ) {
        throw new Error(
            name +
                " is required."
        );
    }

    return value;
}

function main() {

    const databasePath =
        required(
            "RELAY_DATABASE_PATH"
        );

    const username =
        required(
            "RELAY_PROVISION_USERNAME"
        )
            .toLowerCase();

    const password =
        required(
            "RELAY_PROVISION_PASSWORD"
        );

    const displayName =
        required(
            "RELAY_PROVISION_DISPLAY_NAME"
        );

    const userId =
        String(
            process.env
                .RELAY_PROVISION_USER_ID ||
            crypto.randomUUID()
        )
            .trim();

    const storage =
        new RelayStorage(
            databasePath
        );

    try {

        if (
            storage.getUserByUsername(
                username
            )
        ) {
            throw new Error(
                "User already exists: " +
                    username
            );
        }

        storage.insertUser({
            ...createUserRecord(
                userId,
                username,
                displayName,
                password
            ),
            isAdmin: true
        });

        console.log(
            "PROVISION_ADMIN_GREEN|" +
                userId
        );

    } finally {

        storage.close();
    }
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
