const {
    buildIncidentPushPayload,
    createPushSender
} = require("./push");

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

async function main() {

    required(
        "RELAY_FIREBASE_PROJECT_ID"
    );

    required(
        "GOOGLE_APPLICATION_CREDENTIALS"
    );

    const token =
        required(
            "RELAY_FIREBASE_TEST_TOKEN"
        );

    const sender =
        createPushSender(
            process.env
        );

    const eventId =
        "EVT-FCM-SMOKE-" +
        Date.now();

    await sender
        .sendToTokens(
            [
                token
            ],
            buildIncidentPushPayload(
                {
                    eventId,
                    incidentId:
                        "INC-FCM-SMOKE",
                    severity:
                        "HIGH"
                },
                "HIGH: Relay FCM transport smoke test"
            )
        );

    console.log(
        "FCM_TRANSPORT_GREEN|" +
        eventId
    );
}

main()
    .catch(
        error => {

            console.error(
                error.message
            );

            process.exitCode =
                1;
        }
    );
