const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");
const WebSocket = require("ws");
const { RelayStorage } = require("./storage");
const { createUserRecord } = require("./credentials");

const TEST_PORT = 9300;
const ADMIN_USERNAME = "relay.admin";
const ADMIN_PASSWORD = "AdminRelay123!";
const OPERATOR_USERNAME = "relay.operator.scoped";
const OPERATOR_PASSWORD = "OperatorRelay123!";
const INCIDENT_A = "INC-AUTH-A";
const INCIDENT_B = "INC-AUTH-B";

const databasePath =
    path.join(
        __dirname,
        "relay-authorization-" +
            process.pid +
            ".sqlite"
    );

function cleanupDatabase() {

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

function startServer() {

    return spawn(
        process.execPath,
        [
            path.join(
                __dirname,
                "server.js"
            )
        ],
        {
            cwd:
                __dirname,
            env: {
                ...process.env,
                RELAY_PORT:
                    String(
                        TEST_PORT
                    ),
                RELAY_DATABASE_PATH:
                    databasePath,
                RELAY_BOOTSTRAP_USERNAME:
                    ADMIN_USERNAME,
                RELAY_BOOTSTRAP_PASSWORD:
                    ADMIN_PASSWORD,
                RELAY_ENABLE_TEST_SHUTDOWN:
                    "1"
            },
            stdio: [
                "ignore",
                "pipe",
                "pipe"
            ]
        }
    );
}

function waitForOutput(
    child,
    marker,
    timeoutMs =
        10_000
) {

    return new Promise(
        (
            resolve,
            reject
        ) => {

            let output =
                "";

            const timer =
                setTimeout(
                    () => {

                        child.stdout.off(
                            "data",
                            onData
                        );

                        reject(
                            new Error(
                                "Timed out waiting for " +
                                    marker +
                                    "\n" +
                                    output
                            )
                        );
                    },
                    timeoutMs
                );

            const onData =
                data => {

                    const text =
                        data.toString();

                    output +=
                        text;

                    process.stdout.write(
                        text
                    );

                    if (
                        output.includes(
                            marker
                        )
                    ) {

                        clearTimeout(
                            timer
                        );

                        child.stdout.off(
                            "data",
                            onData
                        );

                        resolve();
                    }
                };

            child.stdout.on(
                "data",
                onData
            );

            child.stderr.on(
                "data",
                data => {
                    process.stderr.write(
                        data.toString()
                    );
                }
            );
        }
    );
}

async function login(
    username,
    password
) {

    const response =
        await fetch(
            "http://127.0.0.1:" +
                TEST_PORT +
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
                        username,
                        password
                    })
            }
        );

    assert(
        response.status ===
            200,
        "Login failed for " +
            username +
            ": " +
            response.status
    );

    return response.json();
}

function openSocket(
    accessToken
) {

    return new Promise(
        (
            resolve,
            reject
        ) => {

            const socket =
                new WebSocket(
                    "ws://127.0.0.1:" +
                        TEST_PORT,
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
                                "WebSocket open timeout."
                            )
                        );
                    },
                    5_000
                );

            socket.once(
                "open",
                () => {

                    clearTimeout(
                        timer
                    );

                    resolve(
                        socket
                    );
                }
            );

            socket.once(
                "error",
                reject
            );
        }
    );
}

function waitForMessage(
    socket,
    predicate,
    timeoutMs =
        4_000
) {

    return new Promise(
        (
            resolve,
            reject
        ) => {

            const timer =
                setTimeout(
                    () => {

                        socket.off(
                            "message",
                            onMessage
                        );

                        reject(
                            new Error(
                                "Expected WebSocket message timed out."
                            )
                        );
                    },
                    timeoutMs
                );

            const onMessage =
                raw => {

                    const message =
                        JSON.parse(
                            raw.toString()
                        );

                    if (
                        predicate(
                            message
                        )
                    ) {

                        clearTimeout(
                            timer
                        );

                        socket.off(
                            "message",
                            onMessage
                        );

                        resolve(
                            message
                        );
                    }
                };

            socket.on(
                "message",
                onMessage
            );
        }
    );
}

function expectNoMessage(
    socket,
    predicate,
    timeoutMs =
        700
) {

    return new Promise(
        (
            resolve,
            reject
        ) => {

            const timer =
                setTimeout(
                    () => {

                        socket.off(
                            "message",
                            onMessage
                        );

                        resolve();
                    },
                    timeoutMs
                );

            const onMessage =
                raw => {

                    const message =
                        JSON.parse(
                            raw.toString()
                        );

                    if (
                        predicate(
                            message
                        )
                    ) {

                        clearTimeout(
                            timer
                        );

                        socket.off(
                            "message",
                            onMessage
                        );

                        reject(
                            new Error(
                                "Unauthorized event leaked: " +
                                    JSON.stringify(
                                        message
                                    )
                            )
                        );
                    }
                };

            socket.on(
                "message",
                onMessage
            );
        }
    );
}

async function stopServer(
    child
) {

    const complete =
        waitForOutput(
            child,
            "SERVER_SHUTDOWN_COMPLETE|TEST"
        );

    const response =
        await fetch(
            "http://127.0.0.1:" +
                TEST_PORT +
                "/__test/shutdown",
            {
                method:
                    "POST"
            }
        );

    assert(
        response.status ===
            202,
        "Shutdown request failed."
    );

    await complete;
}

async function main() {

    cleanupDatabase();

    let storage =
        new RelayStorage(
            databasePath
        );

    storage.insertUser({
        ...createUserRecord(
            "auth-admin",
            ADMIN_USERNAME,
            "Authorization Admin",
            ADMIN_PASSWORD
        ),
        isAdmin:
            true
    });

    storage.insertUser({
        ...createUserRecord(
            "auth-operator",
            OPERATOR_USERNAME,
            "Scoped Operator",
            OPERATOR_PASSWORD
        ),
        isAdmin:
            false
    });

    for (
        const incidentId of
        [
            INCIDENT_A,
            INCIDENT_B
        ]
    ) {

        storage.ensureIncident(
            incidentId,
            {
                title:
                    incidentId,
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
    }

    storage.grantIncidentAccess(
        "auth-operator",
        INCIDENT_A
    );

    storage.close();

    const server =
        startServer();

    let adminSocket;
    let operatorSocket;

    try {

        await waitForOutput(
            server,
            "RELAY_DEV_SERVER_READY|" +
                TEST_PORT
        );

        const adminSession =
            await login(
                ADMIN_USERNAME,
                ADMIN_PASSWORD
            );

        const operatorSession =
            await login(
                OPERATOR_USERNAME,
                OPERATOR_PASSWORD
            );

        adminSocket =
            await openSocket(
                adminSession.accessToken
            );

        operatorSocket =
            await openSocket(
                operatorSession.accessToken
            );

        const operatorAEvent =
            waitForMessage(
                operatorSocket,
                message =>
                    message.type ===
                        "incident.updated" &&
                    message.incidentId ===
                        INCIDENT_A
            );

        adminSocket.send(
            JSON.stringify({
                type:
                    "incident.severity.update",
                commandId:
                    "CMD-AUTH-A",
                incidentId:
                    INCIDENT_A,
                severity:
                    "HIGH"
            })
        );

        const receivedA =
            await operatorAEvent;

        assert(
            receivedA.severity ===
                "HIGH",
            "Authorized incident A event was incorrect."
        );

        const noBLeak =
            expectNoMessage(
                operatorSocket,
                message =>
                    message.incidentId ===
                        INCIDENT_B
            );

        const adminBEvent =
            waitForMessage(
                adminSocket,
                message =>
                    message.type ===
                        "incident.updated" &&
                    message.incidentId ===
                        INCIDENT_B
            );

        adminSocket.send(
            JSON.stringify({
                type:
                    "incident.severity.update",
                commandId:
                    "CMD-AUTH-B",
                incidentId:
                    INCIDENT_B,
                severity:
                    "CRITICAL"
            })
        );

        await adminBEvent;
        await noBLeak;

        const forbiddenWrite =
            waitForMessage(
                operatorSocket,
                message =>
                    message.type ===
                        "command.rejected" &&
                    message.commandId ===
                        "CMD-FORBIDDEN-B"
            );

        operatorSocket.send(
            JSON.stringify({
                type:
                    "incident.severity.update",
                commandId:
                    "CMD-FORBIDDEN-B",
                incidentId:
                    INCIDENT_B,
                severity:
                    "LOW"
            })
        );

        const forbidden =
            await forbiddenWrite;

        assert(
            forbidden.reason ===
                "forbidden",
            "Unauthorized incident write was not rejected."
        );

        const noReplayLeak =
            expectNoMessage(
                operatorSocket,
                message =>
                    message.incidentId ===
                        INCIDENT_B,
                900
            );

        operatorSocket.send(
            JSON.stringify({
                type:
                    "replay.request",
                incidentId:
                    INCIDENT_B,
                fromSequence:
                    1,
                throughSequence:
                    10
            })
        );

        await noReplayLeak;

        const adminCollisionAck =
            waitForMessage(
                adminSocket,
                message =>
                    message.type ===
                        "command.accepted" &&
                    message.commandId ===
                        "CMD-COLLISION"
            );

        adminSocket.send(
            JSON.stringify({
                type:
                    "incident.severity.update",
                commandId:
                    "CMD-COLLISION",
                incidentId:
                    INCIDENT_B,
                severity:
                    "HIGH"
            })
        );

        await adminCollisionAck;

        const collisionReject =
            waitForMessage(
                operatorSocket,
                message =>
                    message.type ===
                        "command.rejected" &&
                    message.commandId ===
                        "CMD-COLLISION"
            );

        const noCollisionLeak =
            expectNoMessage(
                operatorSocket,
                message =>
                    message.incidentId ===
                        INCIDENT_B
            );

        operatorSocket.send(
            JSON.stringify({
                type:
                    "incident.severity.update",
                commandId:
                    "CMD-COLLISION",
                incidentId:
                    INCIDENT_A,
                severity:
                    "LOW"
            })
        );

        const collision =
            await collisionReject;

        assert(
            collision.reason ===
                "command_id_conflict",
            "Cross-incident command ID collision was not rejected."
        );

        await noCollisionLeak;

        const adminTimeline =
            waitForMessage(
                adminSocket,
                message =>
                    message.type ===
                        "timeline.entry.added" &&
                    message.entryId ===
                        "ENTRY-COLLISION"
            );

        adminSocket.send(
            JSON.stringify({
                type:
                    "timeline.entry.create",
                entryId:
                    "ENTRY-COLLISION",
                incidentId:
                    INCIDENT_B,
                message:
                    "Private B entry"
            })
        );

        await adminTimeline;

        const timelineCollisionReject =
            waitForMessage(
                operatorSocket,
                message =>
                    message.type ===
                        "timeline.entry.rejected" &&
                    message.entryId ===
                        "ENTRY-COLLISION"
            );

        const noTimelineLeak =
            expectNoMessage(
                operatorSocket,
                message =>
                    message.incidentId ===
                        INCIDENT_B &&
                    message.type ===
                        "timeline.entry.added"
            );

        operatorSocket.send(
            JSON.stringify({
                type:
                    "timeline.entry.create",
                entryId:
                    "ENTRY-COLLISION",
                incidentId:
                    INCIDENT_A,
                message:
                    "Attempted cross-incident collision"
            })
        );

        const timelineCollision =
            await timelineCollisionReject;

        assert(
            timelineCollision.reason ===
                "entry_id_conflict",
            "Timeline entry-ID collision was not explicitly rejected."
        );

        await noTimelineLeak;

        const invalidTimelineReject =
            waitForMessage(
                operatorSocket,
                message =>
                    message.type ===
                        "timeline.entry.rejected" &&
                    message.entryId ===
                        "ENTRY-INVALID"
            );

        operatorSocket.send(
            JSON.stringify({
                type:
                    "timeline.entry.create",
                entryId:
                    "ENTRY-INVALID",
                incidentId:
                    INCIDENT_A,
                message:
                    "   "
            })
        );

        const invalidTimeline =
            await invalidTimelineReject;

        assert(
            invalidTimeline.reason ===
                "invalid_payload",
            "Invalid timeline payload was not explicitly rejected."
        );

        storage =
            new RelayStorage(
                databasePath
            );

        assert(
            storage
                .getTimelineEntry(
                    "ENTRY-COLLISION"
                )
                ?.incidentId ===
                INCIDENT_B,
            "Timeline collision changed incident ownership."
        );

        storage.close();

        console.log(
            "REALTIME_AUTHORIZATION_GREEN"
        );

    } finally {

        adminSocket
            ?.close();

        operatorSocket
            ?.close();

        if (
            server.exitCode ===
            null
        ) {
            await stopServer(
                server
            );
        }

        cleanupDatabase();
    }
}

main()
    .catch(
        error => {

            cleanupDatabase();

            console.error(
                error
            );

            process.exitCode =
                1;
        }
    );
