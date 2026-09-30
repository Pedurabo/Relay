const fs = require("fs");
const path = require("path");
const { RelayStorage } = require("./storage");
const { buildIncidentPushPayload } = require("./push");

const databasePath =
    path.join(
        __dirname,
        "relay-push-test-" +
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

function user(
    userId,
    username,
    isAdmin =
        false
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

    const storage =
        new RelayStorage(
            databasePath
        );

    try {

        storage.insertUser(
            user(
                "admin",
                "admin",
                true
            )
        );

        storage.insertUser(
            user(
                "operator",
                "operator"
            )
        );

        storage.insertUser(
            user(
                "outsider",
                "outsider"
            )
        );

        storage.ensureIncident(
            "INC-PUSH",
            {
                title:
                    "Push incident",
                severity:
                    "HIGH",
                sequence:
                    7,
                serverOwned:
                    true
            }
        );

        storage.grantIncidentAccess(
            "operator",
            "INC-PUSH"
        );

        storage.upsertPushRegistration(
            "admin",
            "token-admin"
        );

        storage.upsertPushRegistration(
            "operator",
            "token-operator"
        );

        storage.upsertPushRegistration(
            "outsider",
            "token-outsider"
        );

        let targets =
            storage
                .getPushTargetsForIncident(
                    "INC-PUSH"
                );

        const targetTokens =
            targets
                .map(
                    target =>
                        target.token
                );

        assert(
            targetTokens.includes(
                "token-admin"
            ),
            "Admin token was not selected."
        );

        assert(
            targetTokens.includes(
                "token-operator"
            ),
            "Authorized operator token was not selected."
        );

        assert(
            !targetTokens.includes(
                "token-outsider"
            ),
            "Unauthorized user received incident push."
        );

        storage.upsertPushRegistration(
            "outsider",
            "token-operator"
        );

        targets =
            storage
                .getPushTargetsForIncident(
                    "INC-PUSH"
                );

        assert(
            !targets.some(
                target =>
                    target.token ===
                    "token-operator"
            ),
            "Reassigned token retained old account authorization."
        );

        storage.removePushRegistration(
            "admin",
            "token-admin"
        );

        targets =
            storage
                .getPushTargetsForIncident(
                    "INC-PUSH"
                );

        assert(
            !targets.some(
                target =>
                    target.token ===
                    "token-admin"
            ),
            "Unregistered token remained active."
        );

        const payload =
            buildIncidentPushPayload(
                {
                    eventId:
                        "EVT-PUSH-1",
                    incidentId:
                        "INC-PUSH",
                    severity:
                        "critical"
                },
                "CRITICAL: incident updated"
            );

        assert(
            payload.eventId ===
                "EVT-PUSH-1" &&
            payload.incidentId ===
                "INC-PUSH" &&
            payload.severity ===
                "CRITICAL" &&
            payload.content ===
                "CRITICAL: incident updated",
            "Push data payload was incorrect."
        );

        assert(
            Object
                .values(
                    payload
                )
                .every(
                    value =>
                        typeof value ===
                        "string"
                ),
            "FCM data payload values must all be strings."
        );

        console.log(
            "PUSH_POLICY_GREEN"
        );

    } finally {

        storage.close();
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
