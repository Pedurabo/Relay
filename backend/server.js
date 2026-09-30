const fs = require("fs");
const path = require("path");
const http = require("http");
const crypto = require("crypto");
const { WebSocketServer } = require("ws");

const PORT =
    Number(
        process.env.RELAY_PORT ||
        9000
    );

const persistentStateFile =
    path.join(
        __dirname,
        "relay-dev-state.json"
    );

const BOOTSTRAP_USERNAME =
    process.env.RELAY_BOOTSTRAP_USERNAME ||
    "relay.operator";

const BOOTSTRAP_PASSWORD =
    process.env.RELAY_BOOTSTRAP_PASSWORD ||
    "RelayDemo123!";

const BOOTSTRAP_USER_ID =
    "dev-relay-operator";

const BOOTSTRAP_DISPLAY_NAME =
    "Relay Operator";

const previousRoomTestStateFile =
    path.join(
        __dirname,
        "room-outbox-final-state.json"
    );


const SESSION_TTL_MS =
    Number(
        process.env.RELAY_ACCESS_TTL_MS ||
        60 * 60 * 1000
    );

const REFRESH_TTL_MS =
    Number(
        process.env.RELAY_REFRESH_TTL_MS ||
        7 * 24 * 60 * 60 * 1000
    );

const sessions =
    new Map();

const refreshSessions =
    new Map();

function issueSession(
    userId,
    userName,
    refreshToken =
        null
) {

    const accessToken =
        crypto
            .randomBytes(32)
            .toString("hex");

    const resolvedRefreshToken =
        refreshToken ||
        crypto
            .randomBytes(48)
            .toString("hex");

    const accessTokenExpiresAt =
        Date.now() +
        SESSION_TTL_MS;

    const refreshTokenExpiresAt =
        Date.now() +
        REFRESH_TTL_MS;

    sessions.set(
        accessToken,
        {
            userId,
            userName,
            refreshToken:
                resolvedRefreshToken,
            expiresAt:
                accessTokenExpiresAt
        }
    );

    refreshSessions.set(
        resolvedRefreshToken,
        {
            userId,
            userName,
            expiresAt:
                refreshTokenExpiresAt
        }
    );

    return {
        userId,
        userName,
        accessToken,
        refreshToken:
            resolvedRefreshToken,
        accessTokenExpiresAt,
        refreshTokenExpiresAt
    };
}

function resolveBearerSession(
    authorization
) {

    const prefix =
        "Bearer ";

    if (
        typeof authorization !==
            "string" ||
        !authorization.startsWith(
            prefix
        )
    ) {
        return null;
    }

    const token =
        authorization
            .slice(prefix.length)
            .trim();

    const session =
        sessions.get(
            token
        );

    if (!session) {
        return null;
    }

    if (
        session.expiresAt <=
        Date.now()
    ) {
        sessions.delete(
            token
        );

        return null;
    }

    return {
        token,
        ...session
    };
}

function now() {
    return Date.now();
}

function randomId(prefix) {

    return (
        prefix +
        "-" +
        Date.now() +
        "-" +
        Math.random()
            .toString(16)
            .slice(2)
    );
}

function hashPassword(
    password,
    salt
) {

    return crypto
        .scryptSync(
            password,
            salt,
            64
        )
        .toString("hex");
}

function createUserRecord(
    userId,
    username,
    displayName,
    password
) {

    const passwordSalt =
        crypto
            .randomBytes(16)
            .toString("hex");

    return {
        userId,
        username:
            username.toLowerCase(),
        displayName,
        passwordSalt,
        passwordHash:
            hashPassword(
                password,
                passwordSalt
            )
    };
}

function verifyPassword(
    password,
    user
) {

    const actual =
        Buffer.from(
            hashPassword(
                password,
                user.passwordSalt
            ),
            "hex"
        );

    const expected =
        Buffer.from(
            user.passwordHash,
            "hex"
        );

    return (
        actual.length ===
            expected.length &&
        crypto.timingSafeEqual(
            actual,
            expected
        )
    );
}

function freshState() {

    return {
        users: {},
        incidents: {},
        processedCommands: {},
        timelineEntries: {}
    };
}

function loadState() {

    if (
        fs.existsSync(
            persistentStateFile
        )
    ) {

        try {

            return JSON.parse(
                fs.readFileSync(
                    persistentStateFile,
                    "utf8"
                )
            );

        } catch (error) {

            console.error(
                "STATE_LOAD_FAILED|" +
                error.message
            );
        }
    }

    return freshState();
}

function saveState() {

    fs.writeFileSync(
        persistentStateFile,
        JSON.stringify(
            state,
            null,
            2
        )
    );
}

function normalizeState() {

    if (
        !state.users
    ) {
        state.users = {};
    }

    if (
        !state.users[
            BOOTSTRAP_USERNAME
                .toLowerCase()
        ]
    ) {

        state.users[
            BOOTSTRAP_USERNAME
                .toLowerCase()
        ] =
            createUserRecord(
                BOOTSTRAP_USER_ID,
                BOOTSTRAP_USERNAME,
                BOOTSTRAP_DISPLAY_NAME,
                BOOTSTRAP_PASSWORD
            );

        console.log(
            "AUTH_BOOTSTRAP_USER_CREATED|" +
            BOOTSTRAP_USER_ID
        );
    }

    if (
        !state.incidents
    ) {
        state.incidents = {};
    }

    if (
        !state.processedCommands
    ) {
        state.processedCommands = {};
    }

    if (
        !state.timelineEntries
    ) {
        state.timelineEntries = {};
    }

    for (
        const incident of
        Object.values(
            state.incidents
        )
    ) {

        if (
            !Array.isArray(
                incident.history
            )
        ) {
            incident.history = [];
        }

        if (
            typeof incident.sequence !==
            "number"
        ) {
            incident.sequence = 0;
        }

        if (
            !incident.severity
        ) {
            incident.severity =
                "MEDIUM";
        }

        if (
            !incident.status
        ) {
            incident.status =
                "Active";
        }
    }
}

let state =
    loadState();

normalizeState();




//
// ------------------------------------------------------------
// Import the previously proven Room idempotency incident.
// ------------------------------------------------------------
//

if (
    fs.existsSync(
        previousRoomTestStateFile
    )
) {

    try {

        const previous =
            JSON.parse(
                fs.readFileSync(
                    previousRoomTestStateFile,
                    "utf8"
                )
            );

        if (
            previous.incidentId &&
            !state.incidents[
                previous.incidentId
            ]
        ) {

            state.incidents[
                previous.incidentId
            ] = {

                id:
                    previous.incidentId,

                title:
                    previous.title ||
                    "Room process-death test",

                status:
                    previous.status ||
                    "Active",

                severity:
                    previous.severity ||
                    "MEDIUM",

                sequence:
                    Number(
                        previous.sequence || 1
                    ),

                serverOwned:
                    true,

                history:
                    []
            };

            if (
                Array.isArray(
                    previous.processedCommandIds
                )
            ) {

                for (
                    const commandId of
                    previous.processedCommandIds
                ) {

                    state.processedCommands[
                        commandId
                    ] = {
                        commandId,
                        incidentId:
                            previous.incidentId,
                        type:
                            "incident.severity.update"
                    };
                }
            }

            console.log(
                "IMPORTED_ROOM_TEST|" +
                previous.incidentId +
                "|" +
                previous.severity +
                "|" +
                previous.sequence
            );
        }

    } catch (error) {

        console.error(
            "ROOM_TEST_IMPORT_FAILED|" +
            error.message
        );
    }
}

saveState();

//
// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------
//

function send(
    socket,
    message
) {

    if (
        socket.readyState === 1
    ) {

        socket.send(
            JSON.stringify(
                message
            )
        );
    }
}

function broadcast(
    message
) {

    const payload =
        JSON.stringify(
            message
        );

    for (
        const client of
        wss.clients
    ) {

        if (
            client.readyState === 1
        ) {

            client.send(
                payload
            );
        }
    }
}

function ensureIncident(
    incidentId
) {

    let incident =
        state.incidents[
            incidentId
        ];

    if (
        !incident
    ) {

        //
        // Compatibility mode for incidents that already exist
        // in Relay's Room database but were created by older
        // deterministic backends.
        //
        // sequence = 0 means Relay handles the authoritative
        // update as unsequenced rather than incorrectly
        // rejecting it as stale against an old local sequence.
        //

        incident = {

            id:
                incidentId,

            title:
                "Existing Relay incident",

            status:
                "Active",

            severity:
                "MEDIUM",

            sequence:
                0,

            serverOwned:
                false,

            history:
                []
        };

        state.incidents[
            incidentId
        ] =
            incident;

        saveState();

        console.log(
            "DISCOVERED_LEGACY_INCIDENT|" +
            incidentId
        );
    }

    return incident;
}

function buildSeverityEvent(
    incident,
    commandId
) {

    return {

        type:
            "incident.updated",

        eventId:
            "EVT-SEVERITY-" +
            commandId,

        incidentId:
            incident.id,

        occurredAt:
            now(),

        severity:
            incident.severity,

        sequence:
            incident.serverOwned
                ? incident.sequence
                : 0
    };
}

function acknowledgeSeverity(
    socket,
    commandId,
    incidentId,
    duplicate
) {

    send(
        socket,
        {
            type:
                "command.accepted",

            command:
                "incident.severity.update",

            commandId,

            incidentId,

            duplicate:
                duplicate === true
        }
    );
}

//
// ------------------------------------------------------------
// Server
// ------------------------------------------------------------
//

const server =
    http.createServer(
        (request, response) => {

            if (
                request.method ===
                    "POST" &&
                request.url ===
                    "/auth/login"
            ) {

                let rawBody =
                    "";

                request.on(
                    "data",
                    chunk => {
                        rawBody +=
                            chunk.toString();
                    }
                );

                request.on(
                    "end",
                    () => {

                        let body;

                        try {

                            body =
                                JSON.parse(
                                    rawBody || "{}"
                                );

                        } catch (error) {

                            response.writeHead(
                                400,
                                {
                                    "Content-Type":
                                        "application/json"
                                }
                            );

                            response.end(
                                JSON.stringify({
                                    error:
                                        "invalid_json"
                                })
                            );

                            return;
                        }

                        const username =
                            String(
                                body.username ||
                                ""
                            )
                                .trim()
                                .toLowerCase();

                        const password =
                            String(
                                body.password ||
                                ""
                            );

                        const user =
                            state.users[
                                username
                            ];

                        if (
                            !user ||
                            !password ||
                            !verifyPassword(
                                password,
                                user
                            )
                        ) {

                            console.log(
                                "AUTH_LOGIN_REJECTED|" +
                                username
                            );

                            response.writeHead(
                                401,
                                {
                                    "Content-Type":
                                        "application/json"
                                }
                            );

                            response.end(
                                JSON.stringify({
                                    error:
                                        "invalid_credentials"
                                })
                            );

                            return;
                        }

                        const session =
                            issueSession(
                                user.userId,
                                user.displayName
                            );

                        console.log(
                            "AUTH_LOGIN_ACCEPTED|" +
                            session.userId +
                            "|expiresAt=" +
                            session.accessTokenExpiresAt
                        );

                        response.writeHead(
                            200,
                            {
                                "Content-Type":
                                    "application/json"
                            }
                        );

                        response.end(
                            JSON.stringify(
                                session
                            )
                        );
                    }
                );

                return;
            }

            if (
                request.method ===
                    "POST" &&
                request.url ===
                    "/auth/refresh"
            ) {

                let rawBody =
                    "";

                request.on(
                    "data",
                    chunk => {
                        rawBody +=
                            chunk.toString();
                    }
                );

                request.on(
                    "end",
                    () => {

                        let body;

                        try {
                            body =
                                JSON.parse(
                                    rawBody || "{}"
                                );
                        } catch (error) {

                            response.writeHead(
                                400,
                                {
                                    "Content-Type":
                                        "application/json"
                                }
                            );

                            response.end(
                                JSON.stringify({
                                    error:
                                        "invalid_json"
                                })
                            );

                            return;
                        }

                        const refreshToken =
                            String(
                                body.refreshToken ||
                                ""
                            )
                                .trim();

                        const refreshSession =
                            refreshSessions.get(
                                refreshToken
                            );

                        if (
                            !refreshSession ||
                            refreshSession.expiresAt <=
                                Date.now()
                        ) {

                            refreshSessions.delete(
                                refreshToken
                            );

                            response.writeHead(
                                401,
                                {
                                    "Content-Type":
                                        "application/json"
                                }
                            );

                            response.end(
                                JSON.stringify({
                                    error:
                                        "invalid_refresh_token"
                                })
                            );

                            return;
                        }

                        const session =
                            issueSession(
                                refreshSession.userId,
                                refreshSession.userName,
                                refreshToken
                            );

                        console.log(
                            "AUTH_SESSION_REFRESHED|" +
                            session.userId +
                            "|expiresAt=" +
                            session.accessTokenExpiresAt
                        );

                        response.writeHead(
                            200,
                            {
                                "Content-Type":
                                    "application/json"
                            }
                        );

                        response.end(
                            JSON.stringify(
                                session
                            )
                        );
                    }
                );

                return;
            }

            if (
                request.method ===
                    "POST" &&
                request.url ===
                    "/auth/revoke"
            ) {

                let rawBody =
                    "";

                request.on(
                    "data",
                    chunk => {
                        rawBody +=
                            chunk.toString();
                    }
                );

                request.on(
                    "end",
                    () => {

                        let body;

                        try {
                            body =
                                JSON.parse(
                                    rawBody || "{}"
                                );
                        } catch (error) {
                            response.writeHead(400);
                            response.end();
                            return;
                        }

                        const refreshToken =
                            String(
                                body.refreshToken ||
                                ""
                            )
                                .trim();

                        if (
                            refreshToken
                        ) {

                            refreshSessions.delete(
                                refreshToken
                            );

                            for (
                                const [
                                    accessToken,
                                    accessSession
                                ] of
                                sessions.entries()
                            ) {

                                if (
                                    accessSession.refreshToken ===
                                    refreshToken
                                ) {

                                    sessions.delete(
                                        accessToken
                                    );
                                }
                            }

                            console.log(
                                "AUTH_SESSION_REVOKED"
                            );
                        }

                        response.writeHead(
                            204
                        );

                        response.end();
                    }
                );

                return;
            }

            response.writeHead(
                404,
                {
                    "Content-Type":
                        "application/json"
                }
            );

            response.end(
                JSON.stringify({
                    error:
                        "not_found"
                })
            );
        }
    );

const wss =
    new WebSocketServer({
        noServer: true
    });

server.on(
    "upgrade",
    (
        request,
        socket,
        head
    ) => {

        const session =
            resolveBearerSession(
                request.headers[
                    "authorization"
                ]
            );

        if (!session) {

            console.log(
                "AUTH_WEBSOCKET_REJECTED"
            );

            socket.write(
                "HTTP/1.1 401 Unauthorized\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            );

            socket.destroy();

            return;
        }

        request.relaySession =
            session;

        wss.handleUpgrade(
            request,
            socket,
            head,
            webSocket => {

                wss.emit(
                    "connection",
                    webSocket,
                    request
                );
            }
        );
    }
);

server.listen(
    PORT,
    "0.0.0.0",
    () => {

        console.log(
            "RELAY_DEV_SERVER_READY|" +
            PORT
        );

        console.log(
            "MODE|MULTI_INCIDENT"
        );

        console.log(
            "INCIDENT_COUNT|" +
            Object.keys(
                state.incidents
            ).length
        );
    }
);


wss.on(
    "connection",
    (socket, request) => {

        const authenticatedSession =
            request.relaySession;

        console.log(
            "CLIENT_CONNECTED|" +
            authenticatedSession.userId
        );

        socket.on(
            "close",
            () => {

                console.log(
                    "CLIENT_DISCONNECTED"
                );
            }
        );

        socket.on(
            "error",
            error => {

                console.error(
                    "CLIENT_SOCKET_ERROR|" +
                    error.message
                );
            }
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

                } catch (error) {

                    console.error(
                        "INVALID_JSON|" +
                        error.message
                    );

                    return;
                }

                //
                // =================================================
                // Severity command
                // =================================================
                //

                if (
                    message.type ===
                    "incident.severity.update"
                ) {

                    const commandId =
                        message.commandId;

                    const incidentId =
                        message.incidentId;

                    const severity =
                        String(
                            message.severity || ""
                        ).toUpperCase();

                    console.log(
                        "SEVERITY_COMMAND|" +
                        commandId +
                        "|" +
                        incidentId +
                        "|" +
                        severity
                    );

                    if (
                        !commandId ||
                        !incidentId ||
                        ![
                            "LOW",
                            "MEDIUM",
                            "HIGH",
                            "CRITICAL"
                        ].includes(
                            severity
                        )
                    ) {

                        send(
                            socket,
                            {
                                type:
                                    "command.rejected",

                                command:
                                    "incident.severity.update",

                                commandId:
                                    commandId || "",

                                incidentId:
                                    incidentId || "",

                                reason:
                                    "invalid_payload"
                            }
                        );

                        console.log(
                            "SEVERITY_REJECTED_INVALID|" +
                            incidentId
                        );

                        return;
                    }

                    const existingCommand =
                        state.processedCommands[
                            commandId
                        ];

                    if (
                        existingCommand
                    ) {

                        const duplicateIncident =
                            ensureIncident(
                                existingCommand
                                    .incidentId
                            );

                        console.log(
                            "SEVERITY_DEDUPED|" +
                            commandId +
                            "|" +
                            duplicateIncident.id +
                            "|" +
                            duplicateIncident.severity +
                            "|" +
                            duplicateIncident.sequence
                        );

                        acknowledgeSeverity(
                            socket,
                            commandId,
                            duplicateIncident.id,
                            true
                        );

                        broadcast(
                            buildSeverityEvent(
                                duplicateIncident,
                                commandId
                            )
                        );

                        return;
                    }

                    const incident =
                        ensureIncident(
                            incidentId
                        );

                    incident.severity =
                        severity;

                    //
                    // Ordered server-owned incidents advance their
                    // sequence normally.
                    //
                    // Older Room-only incidents stay sequence 0 so
                    // Relay does not discard them as stale.
                    //

                    if (
                        incident.serverOwned
                    ) {

                        incident.sequence +=
                            1;
                    }

                    const event =
                        buildSeverityEvent(
                            incident,
                            commandId
                        );

                    if (
                        incident.serverOwned
                    ) {

                        incident.history.push(
                            event
                        );
                    }

                    state.processedCommands[
                        commandId
                    ] = {

                        commandId,

                        incidentId,

                        severity,

                        type:
                            "incident.severity.update",

                        processedAt:
                            now()
                    };

                    saveState();

                    console.log(
                        "SEVERITY_APPLIED|" +
                        commandId +
                        "|" +
                        incidentId +
                        "|" +
                        severity +
                        "|sequence=" +
                        event.sequence
                    );

                    //
                    // -------------------------------------------------
                    // One-shot process-death test failpoint.
                    //
                    // The command has ALREADY been persisted and its
                    // commandId recorded above. When armed for this
                    // incident/severity, deliberately lose the ACK and
                    // authoritative event exactly once.
                    // -------------------------------------------------
                    //

                    const processDeathFailpointFile =
                        path.join(
                            __dirname,
                            "room-process-death-failpoint.json"
                        );

                    if (
                        fs.existsSync(
                            processDeathFailpointFile
                        )
                    ) {

                        try {

                            const failpoint =
                                JSON.parse(
                                    fs.readFileSync(
                                        processDeathFailpointFile,
                                        "utf8"
                                    )
                                );

                            if (
                                failpoint.enabled === true &&
                                failpoint.used !== true &&
                                failpoint.incidentId ===
                                    incidentId &&
                                failpoint.severity ===
                                    severity
                            ) {

                                failpoint.used =
                                    true;

                                failpoint.commandId =
                                    commandId;

                                failpoint.sequence =
                                    event.sequence;

                                failpoint.committedAt =
                                    Date.now();

                                fs.writeFileSync(
                                    processDeathFailpointFile,
                                    JSON.stringify(
                                        failpoint,
                                        null,
                                        2
                                    )
                                );

                                console.log(
                                    "PROCESS_DEATH_FAILPOINT_DROP|" +
                                    commandId +
                                    "|" +
                                    incidentId +
                                    "|" +
                                    severity +
                                    "|sequence=" +
                                    event.sequence
                                );

                                return;
                            }

                        } catch (error) {

                            console.error(
                                "PROCESS_DEATH_FAILPOINT_ERROR|" +
                                error.message
                            );
                        }
                    }

                    acknowledgeSeverity(
                        socket,
                        commandId,
                        incidentId,
                        false
                    );

                    broadcast(
                        event
                    );

                    return;
                }

                //
                // =================================================
                // Timeline entry creation
                // =================================================
                //

                if (
                    message.type ===
                    "timeline.entry.create"
                ) {

                    const entryId =
                        message.entryId;

                    const incidentId =
                        message.incidentId;

                    const text =
                        String(
                            message.message || ""
                        );

                    const author =
                        String(
                            message.author ||
                            "You"
                        );

                    console.log(
                        "TIMELINE_COMMAND|" +
                        entryId +
                        "|" +
                        incidentId
                    );

                    if (
                        !entryId ||
                        !incidentId ||
                        !text.trim()
                    ) {

                        console.log(
                            "TIMELINE_REJECTED_INVALID|" +
                            incidentId
                        );

                        return;
                    }

                    ensureIncident(
                        incidentId
                    );

                    let event =
                        state.timelineEntries[
                            entryId
                        ];

                    if (
                        !event
                    ) {

                        event = {

                            type:
                                "timeline.entry.added",

                            eventId:
                                randomId(
                                    "EVT-TIMELINE"
                                ),

                            incidentId,

                            occurredAt:
                                now(),

                            entryId,

                            message:
                                text,

                            author
                        };

                        state.timelineEntries[
                            entryId
                        ] =
                            event;

                        saveState();

                        console.log(
                            "TIMELINE_APPLIED|" +
                            entryId +
                            "|" +
                            incidentId
                        );
                    }

                    if (
                        state.timelineEntries[
                            entryId
                        ]
                    ) {

                        console.log(
                            "TIMELINE_CONFIRM|" +
                            entryId +
                            "|" +
                            incidentId
                        );

                        broadcast(
                            state.timelineEntries[
                                entryId
                            ]
                        );
                    }

                    return;
                }

                //
                // =================================================
                // Ordered replay request
                // =================================================
                //

                if (
                    message.type ===
                    "replay.request"
                ) {

                    const incidentId =
                        message.incidentId;

                    const fromSequence =
                        Number(
                            message.fromSequence
                        );

                    const throughSequence =
                        Number(
                            message.throughSequence
                        );

                    const incident =
                        state.incidents[
                            incidentId
                        ];

                    console.log(
                        "REPLAY_REQUEST|" +
                        incidentId +
                        "|" +
                        fromSequence +
                        "|" +
                        throughSequence
                    );

                    if (
                        !incident
                    ) {

                        console.log(
                            "REPLAY_UNKNOWN_INCIDENT|" +
                            incidentId
                        );

                        return;
                    }

                    if (
                        !incident.serverOwned
                    ) {

                        console.log(
                            "REPLAY_LEGACY_UNSEQUENCED|" +
                            incidentId
                        );

                        return;
                    }

                    const history =
                        Array.isArray(
                            incident.history
                        )
                            ? incident.history
                            : [];

                    const replay =
                        history.filter(
                            event => {

                                return (
                                    Number(
                                        event.sequence
                                    ) >=
                                        fromSequence &&
                                    Number(
                                        event.sequence
                                    ) <=
                                        throughSequence
                                );
                            }
                        );

                    for (
                        const event of
                        replay
                    ) {

                        send(
                            socket,
                            event
                        );
                    }

                    console.log(
                        "REPLAY_SENT|" +
                        incidentId +
                        "|" +
                        replay.length
                    );

                    return;
                }

                console.log(
                    "UNHANDLED_MESSAGE|" +
                    String(
                        message.type
                    )
                );
            }
        );
    }
);

process.on(
    "SIGINT",
    () => {

        saveState();
        process.exit(0);
    }
);

process.on(
    "SIGTERM",
    () => {

        saveState();
        process.exit(0);
    }
);

