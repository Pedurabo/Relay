const fs = require("fs");
const path = require("path");
const http = require("http");
const crypto = require("crypto");
const { WebSocketServer } = require("ws");
const { createStorage } = require("./storage");
const { loadConfig } = require("./config");
const { createUserRecord, verifyPassword } = require("./credentials");
const { buildIncidentPushPayload, createPushSender } = require("./push");
const { LoginRateLimiter } = require("./login-rate-limiter");

const config =
    loadConfig(
        process.env,
        __dirname
    );

const PORT =
    config.port;

const persistentStateFile =
    path.join(
        __dirname,
        "relay-dev-state.json"
    );

const BOOTSTRAP_USERNAME =
    config.bootstrapUsername;

const BOOTSTRAP_PASSWORD =
    config.bootstrapPassword;

const BOOTSTRAP_USER_ID =
    config.bootstrapUserId;

const BOOTSTRAP_DISPLAY_NAME =
    config.bootstrapDisplayName;

const previousRoomTestStateFile =
    path.join(
        __dirname,
        "room-outbox-final-state.json"
    );

const SESSION_TTL_MS =
    config.accessTtlMs;

const REFRESH_TTL_MS =
    config.refreshTtlMs;

const loginRateLimiter =
    new LoginRateLimiter({
        usernameLimit:
            config.loginRateLimitUsernameFailures,
        clientLimit:
            config.loginRateLimitClientFailures,
        windowMs:
            config.loginRateLimitWindowMs
    });

const storage =
    createStorage(
        __dirname,
        config.databasePath
    );

const pushSender =
    createPushSender(
        process.env
    );

const SESSION_MAINTENANCE_INTERVAL_MS =
    60 * 60 * 1000;

const PUSH_REGISTRATION_STALE_MS =
    30 * 24 * 60 * 60 * 1000;

function runStorageMaintenance() {

    const nowMillis =
        Date.now();

    const sessions =
        storage
            .pruneExpiredSessions(
                nowMillis
            );

    const pushRegistrations =
        storage
            .pruneStalePushRegistrations(
                nowMillis -
                    PUSH_REGISTRATION_STALE_MS
            );

    if (
        sessions.refreshSessions > 0 ||
        sessions.accessSessions > 0 ||
        pushRegistrations > 0
    ) {

        console.log(
            "STORAGE_PRUNED|" +
            "refresh=" +
            sessions.refreshSessions +
            "|access=" +
            sessions.accessSessions +
            "|push=" +
            pushRegistrations
        );
    }
}

runStorageMaintenance();

const storageMaintenanceTimer =
    setInterval(
        runStorageMaintenance,
        SESSION_MAINTENANCE_INTERVAL_MS
    );

storageMaintenanceTimer.unref();

function createSessionPayload(
    userId,
    userName
) {

    const accessToken =
        crypto
            .randomBytes(32)
            .toString("hex");

    const refreshToken =
        crypto
            .randomBytes(48)
            .toString("hex");

    return {
        userId,
        userName,
        accessToken,
        refreshToken,
        accessTokenExpiresAt:
            Date.now() +
            SESSION_TTL_MS,
        refreshTokenExpiresAt:
            Date.now() +
            REFRESH_TTL_MS
    };
}

function issueSession(
    userId,
    userName
) {

    const session =
        createSessionPayload(
            userId,
            userName
        );

    storage.saveSession(
        session
    );

    return session;
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
        storage.getAccessSession(
            token
        );

    if (!session) {
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

function loadLegacyState() {

    if (
        !fs.existsSync(
            persistentStateFile
        )
    ) {
        return null;
    }

    try {

        return JSON.parse(
            fs.readFileSync(
                persistentStateFile,
                "utf8"
            )
        );

    } catch (error) {

        console.error(
            "LEGACY_STATE_LOAD_FAILED|" +
            error.message
        );

        return null;
    }
}

const legacyState =
    loadLegacyState();

if (
    legacyState &&
    storage.countIncidents() ===
        0
) {

    storage.importLegacyState(
        legacyState
    );

    console.log(
        "LEGACY_STATE_MIGRATED_TO_SQLITE"
    );
}

if (
    config.allowDevelopmentBootstrap &&
    !storage.getUserByUsername(
        BOOTSTRAP_USERNAME
    )
) {

    const user =
        createUserRecord(
            BOOTSTRAP_USER_ID,
            BOOTSTRAP_USERNAME,
            BOOTSTRAP_DISPLAY_NAME,
            BOOTSTRAP_PASSWORD
        );

    storage.insertUser({
        ...user,
        isAdmin: true
    });

    console.log(
        "AUTH_BOOTSTRAP_USER_CREATED|" +
        BOOTSTRAP_USER_ID
    );
}

if (
    config.isProduction &&
    storage.countUsers() ===
        0
) {

    storage.close();

    throw new Error(
        "Production user store is empty. Provision users before starting Relay."
    );
}




//
// ------------------------------------------------------------
// Import the previously proven Room idempotency incident.
// ------------------------------------------------------------

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
            !storage.getIncident(
                previous.incidentId
            )
        ) {

            storage.ensureIncident(
                previous.incidentId,
                {
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
                            previous.sequence ||
                            1
                        ),
                    serverOwned:
                        true
                }
            );

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

function broadcastIncident(
    message
) {

    const incidentId =
        message &&
        message.incidentId;

    if (
        !incidentId
    ) {
        return;
    }

    const payload =
        JSON.stringify(
            message
        );

    for (
        const client of
        wss.clients
    ) {

        const session =
            client.relaySession;

        if (
            client.readyState === 1 &&
            session &&
            canAccessIncident(
                session,
                incidentId
            )
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

    return storage.ensureIncident(
        incidentId
    );
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


function canAccessIncident(
    authenticatedSession,
    incidentId
) {

    return storage
        .canAccessIncident(
            authenticatedSession.userId,
            incidentId
        );
}


function sendPushForIncidentEvent(
    event,
    content
) {

    const severity =
        String(
            event.severity ||
            ""
        )
            .toUpperCase();

    if (
        severity !==
            "HIGH" &&
        severity !==
            "CRITICAL"
    ) {
        return;
    }

    const targets =
        storage
            .getPushTargetsForIncident(
                event.incidentId
            );

    if (
        targets.length ===
        0
    ) {
        return;
    }

    pushSender
        .sendToTokens(
            targets.map(
                target =>
                    target.token
            ),
            buildIncidentPushPayload(
                event,
                content,
                storage
                    .getIncident(
                        event.incidentId
                    ) ||
                    {}
            )
        )
        .then(
            () => {

                console.log(
                    "PUSH_SENT|" +
                    event.eventId +
                    "|targets=" +
                    targets.length
                );
            }
        )
        .catch(
            error => {

                console.error(
                    "PUSH_SEND_FAILED|" +
                    event.eventId +
                    "|" +
                    error.message
                );
            }
        );
}

//
// ------------------------------------------------------------
// Server
// ------------------------------------------------------------
//

let shuttingDown =
    false;

const server =
    http.createServer(
        (request, response) => {

            if (
                request.method ===
                    "GET" &&
                request.url ===
                    "/healthz"
            ) {

                response.writeHead(
                    200,
                    {
                        "Content-Type":
                            "application/json"
                    }
                );

                response.end(
                    JSON.stringify({
                        status:
                            "ok"
                    })
                );

                return;
            }

            if (
                request.method ===
                    "GET" &&
                request.url ===
                    "/readyz"
            ) {

                response.writeHead(
                    shuttingDown
                        ? 503
                        : 200,
                    {
                        "Content-Type":
                            "application/json"
                    }
                );

                response.end(
                    JSON.stringify({
                        status:
                            shuttingDown
                                ? "shutting_down"
                                : "ready"
                    })
                );

                return;
            }

            if (
                config.testShutdownEnabled &&
                request.method ===
                    "POST" &&
                request.url ===
                    "/__test/shutdown"
            ) {

                response.writeHead(
                    202,
                    {
                        "Content-Type":
                            "application/json"
                    }
                );

                response.end(
                    JSON.stringify({
                        status:
                            "shutting_down"
                    })
                );

                setImmediate(
                    () => {
                        gracefulShutdown(
                            "TEST"
                        );
                    }
                );

                return;
            }

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

                        const clientKey =
                            request.socket
                                ?.remoteAddress ||
                            "unknown";

                        const rateLimit =
                            loginRateLimiter
                                .check(
                                    username,
                                    clientKey
                                );

                        if (
                            !rateLimit.allowed
                        ) {

                            console.log(
                                "AUTH_LOGIN_RATE_LIMITED|" +
                                username
                            );

                            response.writeHead(
                                429,
                                {
                                    "Content-Type":
                                        "application/json",
                                    "Retry-After":
                                        String(
                                            rateLimit
                                                .retryAfterSeconds
                                        )
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

                        const user =
                            storage.getUserByUsername(
                                username
                            );

                        if (
                            !user ||
                            !password ||
                            !verifyPassword(
                                password,
                                user
                            )
                        ) {

                            loginRateLimiter
                                .recordFailure(
                                    username,
                                    clientKey
                                );

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

                        loginRateLimiter
                            .recordSuccess(
                                username
                            );

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
                            storage.getRefreshSession(
                                refreshToken
                            );

                        if (
                            !refreshSession
                        ) {

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
                            createSessionPayload(
                                refreshSession.userId,
                                refreshSession.userName
                            );

                        const rotated =
                            storage
                                .rotateSession(
                                    refreshToken,
                                    session
                                );

                        if (
                            !rotated
                        ) {

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

                            storage
                                .revokeRefreshSession(
                                    refreshToken
                                );

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

            if (
                request.method ===
                    "GET" &&
                request.url ===
                    "/push/status"
            ) {

                const session =
                    resolveBearerSession(
                        request.headers[
                            "authorization"
                        ]
                    );

                if (
                    !session
                ) {

                    response.writeHead(
                        401
                    );

                    response.end();

                    return;
                }

                response.writeHead(
                    200,
                    {
                        "Content-Type":
                            "application/json"
                    }
                );

                response.end(
                    JSON.stringify({
                        registeredDevices:
                            storage
                                .countPushRegistrationsForUser(
                                    session.userId
                                )
                    })
                );

                return;
            }

            if (
                request.method ===
                    "POST" &&
                (
                    request.url ===
                        "/push/register" ||
                    request.url ===
                        "/push/unregister"
                )
            ) {

                const session =
                    resolveBearerSession(
                        request.headers[
                            "authorization"
                        ]
                    );

                if (
                    !session
                ) {

                    response.writeHead(
                        401
                    );

                    response.end();

                    return;
                }

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
                                    rawBody ||
                                    "{}"
                                );

                        } catch (error) {

                            response.writeHead(
                                400
                            );

                            response.end();

                            return;
                        }

                        const token =
                            String(
                                body.token ||
                                ""
                            )
                                .trim();

                        if (
                            !token
                        ) {

                            response.writeHead(
                                400
                            );

                            response.end();

                            return;
                        }

                        if (
                            request.url ===
                            "/push/register"
                        ) {

                            storage
                                .upsertPushRegistration(
                                    session.userId,
                                    token
                                );

                            console.log(
                                "PUSH_REGISTERED|" +
                                session.userId
                            );

                        } else {

                            storage
                                .removePushRegistration(
                                    session.userId,
                                    token
                                );

                            console.log(
                                "PUSH_UNREGISTERED|" +
                                session.userId
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
            storage.countIncidents()
        );
    }
);


wss.on(
    "connection",
    (socket, request) => {

        const authenticatedSession =
            request.relaySession;

        socket.relaySession =
            authenticatedSession;

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

                    if (
                        !canAccessIncident(
                            authenticatedSession,
                            incidentId
                        )
                    ) {

                        send(
                            socket,
                            {
                                type:
                                    "command.rejected",
                                command:
                                    "incident.severity.update",
                                commandId,
                                incidentId,
                                reason:
                                    "forbidden"
                            }
                        );

                        console.log(
                            "AUTH_INCIDENT_FORBIDDEN|" +
                            authenticatedSession.userId +
                            "|" +
                            incidentId
                        );

                        return;
                    }

                    const existingCommand =
                        storage
                            .getProcessedCommand(
                                commandId
                            );

                    if (
                        existingCommand
                    ) {

                        if (
                            existingCommand.incidentId !==
                            incidentId ||
                            !canAccessIncident(
                                authenticatedSession,
                                existingCommand.incidentId
                            )
                        ) {

                            send(
                                socket,
                                {
                                    type:
                                        "command.rejected",
                                    command:
                                        "incident.severity.update",
                                    commandId,
                                    incidentId,
                                    reason:
                                        "command_id_conflict"
                                }
                            );

                            console.log(
                                "SEVERITY_COMMAND_ID_CONFLICT|" +
                                authenticatedSession.userId +
                                "|" +
                                commandId +
                                "|" +
                                incidentId
                            );

                            return;
                        }

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

                        broadcastIncident(
                            buildSeverityEvent(
                                duplicateIncident,
                                commandId
                            )
                        );

                        return;
                    }

                    const applied =
                        storage
                            .applySeverityCommand(
                                commandId,
                                incidentId,
                                severity,
                                now()
                            );

                    const incident =
                        applied.incident;

                    const event =
                        applied.event ||
                        buildSeverityEvent(
                            incident,
                            commandId
                        );

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

                    broadcastIncident(
                        event
                    );

                    sendPushForIncidentEvent(
                        event,
                        severity +
                            ": " +
                            incidentId +
                            " · severity changed to " +
                            severity
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
                        authenticatedSession
                            .userName;

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

                        send(
                            socket,
                            {
                                type:
                                    "timeline.entry.rejected",
                                entryId:
                                    entryId || "",
                                incidentId:
                                    incidentId || "",
                                reason:
                                    "invalid_payload"
                            }
                        );

                        console.log(
                            "TIMELINE_REJECTED_INVALID|" +
                            incidentId
                        );

                        return;
                    }

                    if (
                        !canAccessIncident(
                            authenticatedSession,
                            incidentId
                        )
                    ) {

                        console.log(
                            "AUTH_INCIDENT_FORBIDDEN|" +
                            authenticatedSession.userId +
                            "|" +
                            incidentId
                        );

                        socket.close(
                            4003,
                            "Forbidden"
                        );

                        return;
                    }

                    ensureIncident(
                        incidentId
                    );

                    let event =
                        storage
                            .getTimelineEntry(
                                entryId
                            );

                    if (
                        event &&
                        event.incidentId !==
                            incidentId
                    ) {

                        send(
                            socket,
                            {
                                type:
                                    "timeline.entry.rejected",
                                entryId,
                                incidentId,
                                reason:
                                    "entry_id_conflict"
                            }
                        );

                        console.log(
                            "TIMELINE_ENTRY_ID_CONFLICT|" +
                            authenticatedSession.userId +
                            "|" +
                            entryId +
                            "|" +
                            incidentId
                        );

                        return;
                    }

                    var created =
                        false;

                    if (
                        !event
                    ) {

                        event =
                            storage
                                .saveTimelineEntry({
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
                                });

                        created =
                            true;

                        console.log(
                            "TIMELINE_APPLIED|" +
                            entryId +
                            "|" +
                            incidentId
                        );
                    }

                    console.log(
                        "TIMELINE_CONFIRM|" +
                        entryId +
                        "|" +
                        incidentId
                    );

                    broadcastIncident(
                        event
                    );

                    if (
                        created
                    ) {

                        const incident =
                            storage
                                .getIncident(
                                    incidentId
                                );

                        if (
                            incident
                        ) {

                            sendPushForIncidentEvent(
                                {
                                    ...event,
                                    severity:
                                        incident.severity
                                },
                                incident.severity +
                                    ": " +
                                    author +
                                    ": " +
                                    text
                            );
                        }
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

                    if (
                        !canAccessIncident(
                            authenticatedSession,
                            incidentId
                        )
                    ) {

                        console.log(
                            "AUTH_INCIDENT_FORBIDDEN|" +
                            authenticatedSession.userId +
                            "|" +
                            incidentId
                        );

                        return;
                    }

                    const incident =
                        storage
                            .getIncident(
                                incidentId
                            );

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

                    const replay =
                        storage
                            .getReplay(
                                incidentId,
                                fromSequence,
                                throughSequence
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

function gracefulShutdown(
    signal
) {

    if (
        shuttingDown
    ) {
        return;
    }

    shuttingDown =
        true;

    clearInterval(
        storageMaintenanceTimer
    );

    console.log(
        "SERVER_SHUTDOWN_BEGIN|" +
        signal
    );

    for (
        const client of
        wss.clients
    ) {

        try {
            client.close(
                1001,
                "Server shutting down"
            );
        } catch (error) {

            console.error(
                "CLIENT_SHUTDOWN_CLOSE_FAILED|" +
                error.message
            );
        }
    }

    server.close(
        () => {

            try {

                storage.close();

                console.log(
                    "SERVER_SHUTDOWN_COMPLETE|" +
                    signal
                );

                process.exit(
                    0
                );

            } catch (error) {

                console.error(
                    "SERVER_SHUTDOWN_STORAGE_FAILED|" +
                    error.message
                );

                process.exit(
                    1
                );
            }
        }
    );

    setTimeout(
        () => {

            console.error(
                "SERVER_SHUTDOWN_FORCED|" +
                signal
            );

            process.exit(
                1
            );
        },
        10_000
    ).unref();
}

process.on(
    "SIGINT",
    () => {
        gracefulShutdown(
            "SIGINT"
        );
    }
);

process.on(
    "SIGTERM",
    () => {
        gracefulShutdown(
            "SIGTERM"
        );
    }
);

