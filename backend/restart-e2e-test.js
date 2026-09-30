const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");
const WebSocket = require("ws");
const { RelayStorage } = require("./storage");

const TEST_PORT = 9200;
const USERNAME = "relay.operator";
const PASSWORD = "RelayDemo123!";
const INCIDENT_ID = "INC-E2E-RESTART";
const COMMAND_ID = "CMD-E2E-RESTART-1";
const ENTRY_ID = "ENTRY-E2E-RESTART-1";

const databasePath =
    path.join(
        __dirname,
        "relay-e2e-" +
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
                        reject(
                            new Error(
                                "Timed out waiting for: " +
                                    marker +
                                    "\nOutput:\n" +
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

                        resolve(
                            output
                        );
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

            child.once(
                "exit",
                code => {

                    if (
                        !output.includes(
                            marker
                        )
                    ) {

                        clearTimeout(
                            timer
                        );

                        reject(
                            new Error(
                                "Server exited before marker " +
                                    marker +
                                    ": " +
                                    code
                            )
                        );
                    }
                }
            );
        }
    );
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
                    USERNAME,
                RELAY_BOOTSTRAP_PASSWORD:
                    PASSWORD,
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

async function postJson(
    route,
    body
) {

    return fetch(
        "http://127.0.0.1:" +
            TEST_PORT +
            route,
        {
            method:
                "POST",
            headers: {
                "Content-Type":
                    "application/json"
            },
            body:
                JSON.stringify(
                    body
                )
        }
    );
}

async function login() {

    const response =
        await postJson(
            "/auth/login",
            {
                username:
                    USERNAME,
                password:
                    PASSWORD
            }
        );

    assert(
        response.status ===
            200,
        "Login failed: " +
            response.status
    );

    return response.json();
}

async function getStatus(
    route
) {

    const response =
        await fetch(
            "http://127.0.0.1:" +
                TEST_PORT +
                route
        );

    return {
        status:
            response.status,
        body:
            await response.json()
    };
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
        5_000
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
                                "WebSocket message timeout."
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

async function stopServer(
    child
) {

    const complete =
        waitForOutput(
            child,
            "SERVER_SHUTDOWN_COMPLETE|TEST"
        );

    const response =
        await postJson(
            "/__test/shutdown",
            {}
        );

    assert(
        response.status ===
            202,
        "Graceful shutdown request failed: " +
            response.status
    );

    await complete;
}

async function main() {

    cleanupDatabase();

    let storage =
        new RelayStorage(
            databasePath
        );

    storage.ensureIncident(
        INCIDENT_ID,
        {
            title:
                "Restart persistence proof",
            status:
                "Active",
            severity:
                "MEDIUM",
            sequence:
                40,
            serverOwned:
                true
        }
    );

    storage.close();

    let server =
        startServer();

    await waitForOutput(
        server,
        "RELAY_DEV_SERVER_READY|" +
            TEST_PORT
    );

    const health =
        await getStatus(
            "/healthz"
        );

    assert(
        health.status ===
            200 &&
        health.body.status ===
            "ok",
        "Health endpoint failed."
    );

    const ready =
        await getStatus(
            "/readyz"
        );

    assert(
        ready.status ===
            200 &&
        ready.body.status ===
            "ready",
        "Readiness endpoint failed."
    );

    const session =
        await login();

    const socket =
        await openSocket(
            session.accessToken
        );

    const severityAck =
        waitForMessage(
            socket,
            message =>
                message.type ===
                    "command.accepted" &&
                message.commandId ===
                    COMMAND_ID
        );

    const severityEvent =
        waitForMessage(
            socket,
            message =>
                message.type ===
                    "incident.updated" &&
                message.eventId ===
                    "EVT-SEVERITY-" +
                    COMMAND_ID
        );

    socket.send(
        JSON.stringify({
            type:
                "incident.severity.update",
            commandId:
                COMMAND_ID,
            incidentId:
                INCIDENT_ID,
            severity:
                "HIGH"
        })
    );

    const firstAck =
        await severityAck;

    const firstEvent =
        await severityEvent;

    assert(
        firstAck.duplicate ===
            false,
        "First command was incorrectly deduped."
    );

    assert(
        firstEvent.severity ===
            "HIGH" &&
        Number(
            firstEvent.sequence
        ) ===
            41,
        "First authoritative event was incorrect."
    );

    const timelineEventPromise =
        waitForMessage(
            socket,
            message =>
                message.type ===
                    "timeline.entry.added" &&
                message.entryId ===
                    ENTRY_ID
        );

    socket.send(
        JSON.stringify({
            type:
                "timeline.entry.create",
            entryId:
                ENTRY_ID,
            incidentId:
                INCIDENT_ID,
            message:
                "Restart-safe timeline entry",
            author:
                "Spoofed Client Author"
        })
    );

    const timelineEvent =
        await timelineEventPromise;

    assert(
        timelineEvent.author ===
            "Relay Operator",
        "Timeline author was not derived from authenticated identity."
    );

    socket.close();

    await stopServer(
        server
    );

    server =
        startServer();

    await waitForOutput(
        server,
        "RELAY_DEV_SERVER_READY|" +
            TEST_PORT
    );

    const socketAfterRestart =
        await openSocket(
            session.accessToken
        );

    const duplicateAckPromise =
        waitForMessage(
            socketAfterRestart,
            message =>
                message.type ===
                    "command.accepted" &&
                message.commandId ===
                    COMMAND_ID
        );

    const duplicateEventPromise =
        waitForMessage(
            socketAfterRestart,
            message =>
                message.type ===
                    "incident.updated" &&
                message.eventId ===
                    "EVT-SEVERITY-" +
                    COMMAND_ID
        );

    socketAfterRestart.send(
        JSON.stringify({
            type:
                "incident.severity.update",
            commandId:
                COMMAND_ID,
            incidentId:
                INCIDENT_ID,
            severity:
                "CRITICAL"
        })
    );

    const duplicateAck =
        await duplicateAckPromise;

    const duplicateEvent =
        await duplicateEventPromise;

    assert(
        duplicateAck.duplicate ===
            true,
        "Duplicate command was not recognized after restart."
    );

    assert(
        duplicateEvent.severity ===
            "HIGH" &&
        Number(
            duplicateEvent.sequence
        ) ===
            41,
        "Duplicate command changed authoritative state after restart."
    );

    const replayPromise =
        waitForMessage(
            socketAfterRestart,
            message =>
                message.type ===
                    "incident.updated" &&
                message.eventId ===
                    "EVT-SEVERITY-" +
                    COMMAND_ID &&
                Number(
                    message.sequence
                ) ===
                    41
        );

    socketAfterRestart.send(
        JSON.stringify({
            type:
                "replay.request",
            incidentId:
                INCIDENT_ID,
            fromSequence:
                41,
            throughSequence:
                41
        })
    );

    const replay =
        await replayPromise;

    assert(
        replay.severity ===
            "HIGH",
        "Replay state was not preserved across restart."
    );

    socketAfterRestart.close();

    await stopServer(
        server
    );

    storage =
        new RelayStorage(
            databasePath
        );

    assert(
        storage
            .getTimelineEntry(
                ENTRY_ID
            )
            ?.author ===
            "Relay Operator",
        "Timeline entry did not persist across restart."
    );

    storage.close();

    cleanupDatabase();

    console.log(
        "BACKEND_RESTART_E2E_GREEN"
    );
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
