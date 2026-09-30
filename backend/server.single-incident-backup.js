const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");

const PORT = 9000;

const stateFile =
    path.join(
        __dirname,
        "room-outbox-final-state.json"
    );

function save(state) {

    fs.writeFileSync(
        stateFile,
        JSON.stringify(
            state,
            null,
            2
        )
    );
}

function load() {

    if (
        fs.existsSync(stateFile)
    ) {

        return JSON.parse(
            fs.readFileSync(
                stateFile,
                "utf8"
            )
        );
    }

    const runId =
        Date.now();

    const fresh = {
        incidentId:
            "INC-ROOM-FINAL-" +
            runId,

        title:
            ">>> ROOM PROCESS DEATH TEST <<< " +
            runId,

        status:
            "Active",

        severity:
            "MEDIUM",

        sequence:
            1,

        processedCommandIds:
            []
    };

    save(
        fresh
    );

    return fresh;
}

const state =
    load();

const wss =
    new WebSocketServer({
        port: PORT,
        host: "0.0.0.0"
    });

console.log(
    "SERVER_READY|" +
    PORT
);

console.log(
    "TEST_INCIDENT_ID|" +
    state.incidentId
);

console.log(
    "INITIAL_STATE|" +
    state.severity +
    "|" +
    state.sequence
);

function send(
    socket,
    payload
) {

    if (
        socket.readyState === 1
    ) {

        socket.send(
            JSON.stringify(
                payload
            )
        );
    }
}

function broadcast(
    payload
) {

    const text =
        JSON.stringify(
            payload
        );

    for (
        const client of
        wss.clients
    ) {

        if (
            client.readyState === 1
        ) {

            client.send(
                text
            );
        }
    }
}

function sendCurrentState(
    socket
) {

    send(
        socket,
        {
            type:
                "incident.created",

            eventId:
                "EVT-ROOM-FINAL-STATE-" +
                state.sequence,

            incidentId:
                state.incidentId,

            occurredAt:
                Date.now(),

            title:
                state.title,

            status:
                state.status,

            severity:
                state.severity,

            sequence:
                state.sequence
        }
    );

    console.log(
        "SENT_STATE|" +
        state.incidentId +
        "|" +
        state.severity +
        "|" +
        state.sequence
    );
}

wss.on(
    "connection",
    socket => {

        console.log(
            "CLIENT_CONNECTED"
        );

        setTimeout(
            () => {

                sendCurrentState(
                    socket
                );
            },
            300
        );

        socket.on(
            "message",
            raw => {

                let message;

                try {

                    message =
                        JSON.parse(
                            raw.toString()
                        );

                } catch {

                    return;
                }

                if (
                    message.type !==
                    "incident.severity.update"
                ) {
                    return;
                }

                console.log(
                    "COMMAND_RECEIVED|" +
                    message.commandId +
                    "|" +
                    message.incidentId +
                    "|" +
                    message.severity
                );

                if (
                    message.incidentId !==
                    state.incidentId
                ) {

                    console.log(
                        "REJECT_WRONG_INCIDENT|" +
                        message.incidentId
                    );

                    send(
                        socket,
                        {
                            type:
                                "command.rejected",

                            command:
                                "incident.severity.update",

                            commandId:
                                message.commandId,

                            incidentId:
                                message.incidentId
                        }
                    );

                    return;
                }

                const duplicate =
                    state
                        .processedCommandIds
                        .includes(
                            message.commandId
                        );

                if (duplicate) {

                    console.log(
                        "DEDUPED_COMMAND|" +
                        message.commandId +
                        "|sequence=" +
                        state.sequence
                    );

                    send(
                        socket,
                        {
                            type:
                                "command.accepted",

                            command:
                                "incident.severity.update",

                            commandId:
                                message.commandId,

                            incidentId:
                                state.incidentId,

                            duplicate:
                                true
                        }
                    );

                    broadcast(
                        {
                            type:
                                "incident.updated",

                            eventId:
                                "EVT-ROOM-FINAL-CONFIRM-" +
                                message.commandId,

                            incidentId:
                                state.incidentId,

                            occurredAt:
                                Date.now(),

                            severity:
                                state.severity,

                            sequence:
                                state.sequence
                        }
                    );

                    console.log(
                        "AUTHORITATIVE_CONFIRMATION|" +
                        message.commandId +
                        "|" +
                        state.severity +
                        "|" +
                        state.sequence
                    );

                    return;
                }

                state.severity =
                    message.severity;

                state.sequence +=
                    1;

                state
                    .processedCommandIds
                    .push(
                        message.commandId
                    );

                save(
                    state
                );

                console.log(
                    "APPLY_COMMAND|" +
                    message.commandId +
                    "|" +
                    state.severity +
                    "|sequence=" +
                    state.sequence
                );

                console.log(
                    "DROP_FIRST_ACK_AND_EVENT|" +
                    message.commandId
                );

                // Intentionally send nothing.
            }
        );
    }
);
