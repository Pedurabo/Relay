const path = require("path");
const WebSocket = require("ws");
const {
    RelayStorage
} = require("./storage");

const BASE_URL =
    process.env.RELAY_SMOKE_BASE_URL ||
    "http://127.0.0.1:9000";

const WS_URL =
    process.env.RELAY_SMOKE_WS_URL ||
    "ws://127.0.0.1:9000";

const USERNAME =
    process.env.RELAY_SMOKE_USERNAME ||
    "relay.operator";

const PASSWORD =
    process.env.RELAY_SMOKE_PASSWORD ||
    "RelayDemo123!";

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

async function login() {

    const response =
        await fetch(
            BASE_URL +
                "/auth/login",
            {
                method:
                    "POST",
                headers: {
                    "Content-Type":
                        "application/json"
                },
                body:
                    JSON.stringify({
                        username:
                            USERNAME,
                        password:
                            PASSWORD
                    })
            }
        );

    assert(
        response.status ===
            200,
        "Smoke login failed: HTTP " +
            response.status
    );

    return response.json();
}

async function verifyPushRegistration(
    accessToken
) {

    const response =
        await fetch(
            BASE_URL +
                "/push/status",
            {
                headers: {
                    Authorization:
                        "Bearer " +
                        accessToken
                }
            }
        );

    assert(
        response.status ===
            200,
        "Push status failed: HTTP " +
            response.status
    );

    const payload =
        await response.json();

    assert(
        Number(
            payload.registeredDevices ||
            0
        ) >
            0,
        "No registered FCM device for this user."
    );
}

function sendCriticalEvent(
    accessToken,
    incidentId,
    commandId
) {

    return new Promise(
        (
            resolve,
            reject
        ) => {

            const socket =
                new WebSocket(
                    WS_URL,
                    {
                        headers: {
                            Authorization:
                                "Bearer " +
                                accessToken
                        }
                    }
                );

            const timer =
                setTimeout(
                    () => {

                        socket.terminate();

                        reject(
                            new Error(
                                "Timed out waiting for severity acknowledgement."
                            )
                        );
                    },
                    10_000
                );

            socket.once(
                "open",
                () => {

                    socket.send(
                        JSON.stringify({
                            type:
                                "incident.severity.update",
                            commandId,
                            incidentId,
                            severity:
                                "CRITICAL"
                        })
                    );
                }
            );

            socket.on(
                "message",
                raw => {

                    const message =
                        JSON.parse(
                            raw.toString()
                        );

                    if (
                        message.type ===
                            "command.accepted" &&
                        message.commandId ===
                            commandId
                    ) {

                        clearTimeout(
                            timer
                        );

                        socket.close();

                        resolve(
                            message
                        );
                    }
                }
            );

            socket.once(
                "error",
                error => {

                    clearTimeout(
                        timer
                    );

                    reject(
                        error
                    );
                }
            );
        }
    );
}

async function main() {

    const stamp =
        Date.now();

    const incidentId =
        "INC-FCM-PHYSICAL-" +
        stamp;

    const commandId =
        "CMD-FCM-PHYSICAL-" +
        stamp;

    const databasePath =
        process.env.RELAY_DATABASE_PATH ||
        path.join(
            __dirname,
            "relay.sqlite"
        );

    const storage =
        new RelayStorage(
            databasePath
        );

    try {

        storage.ensureIncident(
            incidentId,
            {
                title:
                    "FCM killed-process proof",
                status:
                    "Active",
                severity:
                    "MEDIUM",
                sequence:
                    0,
                serverOwned:
                    true
            }
        );

    } finally {

        storage.close();
    }

    const session =
        await login();

    await verifyPushRegistration(
        session.accessToken
    );

    const acknowledgement =
        await sendCriticalEvent(
            session.accessToken,
            incidentId,
            commandId
        );

    assert(
        acknowledgement.duplicate ===
            false,
        "Smoke command was unexpectedly deduped."
    );

    console.log(
        "FCM_BACKEND_TRIGGER_GREEN|" +
        incidentId
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
