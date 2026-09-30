const path = require("path");
const crypto = require("crypto");
const { DatabaseSync } = require("node:sqlite");

const TOKEN_DIGEST_PREFIX =
    "sha256:";

function tokenDigest(
    token
) {

    return (
        TOKEN_DIGEST_PREFIX +
        crypto
            .createHash(
                "sha256"
            )
            .update(
                String(
                    token
                )
            )
            .digest(
                "hex"
            )
    );
}

function isTokenDigest(
    value
) {

    return String(
        value
    )
        .startsWith(
            TOKEN_DIGEST_PREFIX
        );
}

class RelayStorage {

    constructor(
        databasePath
    ) {

        this.database =
            new DatabaseSync(
                databasePath
            );

        this.database.exec(
            `
            PRAGMA journal_mode = WAL;
            PRAGMA foreign_keys = ON;

            CREATE TABLE IF NOT EXISTS users (
                user_id TEXT PRIMARY KEY,
                username TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                is_admin INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            ) STRICT;

            CREATE TABLE IF NOT EXISTS refresh_sessions (
                refresh_token TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id)
                    REFERENCES users(user_id)
                    ON DELETE CASCADE
            ) STRICT;

            CREATE TABLE IF NOT EXISTS access_sessions (
                access_token TEXT PRIMARY KEY,
                refresh_token TEXT NOT NULL,
                user_id TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(refresh_token)
                    REFERENCES refresh_sessions(refresh_token)
                    ON DELETE CASCADE,
                FOREIGN KEY(user_id)
                    REFERENCES users(user_id)
                    ON DELETE CASCADE
            ) STRICT;

            CREATE TABLE IF NOT EXISTS incidents (
                incident_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                severity TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                server_owned INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            ) STRICT;

            CREATE TABLE IF NOT EXISTS incident_events (
                event_id TEXT PRIMARY KEY,
                incident_id TEXT NOT NULL,
                event_type TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                occurred_at INTEGER NOT NULL,
                payload_json TEXT NOT NULL,
                FOREIGN KEY(incident_id)
                    REFERENCES incidents(incident_id)
                    ON DELETE CASCADE
            ) STRICT;

            CREATE TABLE IF NOT EXISTS processed_commands (
                command_id TEXT PRIMARY KEY,
                incident_id TEXT NOT NULL,
                command_type TEXT NOT NULL,
                severity TEXT,
                processed_at INTEGER NOT NULL,
                FOREIGN KEY(incident_id)
                    REFERENCES incidents(incident_id)
                    ON DELETE CASCADE
            ) STRICT;

            CREATE TABLE IF NOT EXISTS timeline_entries (
                entry_id TEXT PRIMARY KEY,
                incident_id TEXT NOT NULL,
                event_id TEXT NOT NULL UNIQUE,
                message TEXT NOT NULL,
                author TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                FOREIGN KEY(incident_id)
                    REFERENCES incidents(incident_id)
                    ON DELETE CASCADE
            ) STRICT;

            CREATE TABLE IF NOT EXISTS incident_access (
                user_id TEXT NOT NULL,
                incident_id TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(user_id, incident_id),
                FOREIGN KEY(user_id)
                    REFERENCES users(user_id)
                    ON DELETE CASCADE,
                FOREIGN KEY(incident_id)
                    REFERENCES incidents(incident_id)
                    ON DELETE CASCADE
            ) STRICT;

            CREATE TABLE IF NOT EXISTS push_registrations (
                token TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                platform TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(user_id)
                    REFERENCES users(user_id)
                    ON DELETE CASCADE
            ) STRICT;

            CREATE INDEX IF NOT EXISTS idx_access_sessions_user
                ON access_sessions(user_id);

            CREATE INDEX IF NOT EXISTS idx_access_sessions_refresh
                ON access_sessions(refresh_token);

            CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user
                ON refresh_sessions(user_id);

            CREATE INDEX IF NOT EXISTS idx_incident_events_replay
                ON incident_events(
                    incident_id,
                    sequence
                );

            CREATE INDEX IF NOT EXISTS idx_timeline_incident
                ON timeline_entries(
                    incident_id,
                    occurred_at
                );

            CREATE INDEX IF NOT EXISTS idx_push_registrations_user
                ON push_registrations(user_id);
            `
        );

        this.migrateSessionTokenDigests();
    }

    close() {
        this.database.close();
    }

    migrateSessionTokenDigests() {

        const refreshRows =
            this.database
                .prepare(
                    `
                    SELECT
                        refresh_token AS refreshToken,
                        user_id AS userId,
                        expires_at AS expiresAt,
                        created_at AS createdAt
                    FROM refresh_sessions
                    `
                )
                .all();

        const accessRows =
            this.database
                .prepare(
                    `
                    SELECT
                        access_token AS accessToken
                    FROM access_sessions
                    `
                )
                .all();

        const needsMigration =
            refreshRows.some(
                row =>
                    !isTokenDigest(
                        row.refreshToken
                    )
            ) ||
            accessRows.some(
                row =>
                    !isTokenDigest(
                        row.accessToken
                    )
            );

        if (
            !needsMigration
        ) {
            return;
        }

        this.database.exec(
            "BEGIN IMMEDIATE"
        );

        try {

            for (
                const row of
                refreshRows
            ) {

                if (
                    isTokenDigest(
                        row.refreshToken
                    )
                ) {
                    continue;
                }

                const digest =
                    tokenDigest(
                        row.refreshToken
                    );

                this.database
                    .prepare(
                        `
                        INSERT OR IGNORE INTO refresh_sessions (
                            refresh_token,
                            user_id,
                            expires_at,
                            created_at
                        )
                        VALUES (?, ?, ?, ?)
                        `
                    )
                    .run(
                        digest,
                        row.userId,
                        row.expiresAt,
                        row.createdAt
                    );

                this.database
                    .prepare(
                        `
                        UPDATE access_sessions
                        SET refresh_token = ?
                        WHERE refresh_token = ?
                        `
                    )
                    .run(
                        digest,
                        row.refreshToken
                    );

                this.database
                    .prepare(
                        `
                        DELETE FROM refresh_sessions
                        WHERE refresh_token = ?
                        `
                    )
                    .run(
                        row.refreshToken
                    );
            }

            for (
                const row of
                accessRows
            ) {

                if (
                    isTokenDigest(
                        row.accessToken
                    )
                ) {
                    continue;
                }

                this.database
                    .prepare(
                        `
                        UPDATE access_sessions
                        SET access_token = ?
                        WHERE access_token = ?
                        `
                    )
                    .run(
                        tokenDigest(
                            row.accessToken
                        ),
                        row.accessToken
                    );
            }

            const foreignKeyViolation =
                this.database
                    .prepare(
                        "PRAGMA foreign_key_check"
                    )
                    .get();

            if (
                foreignKeyViolation
            ) {
                throw new Error(
                    "Session token digest migration violated foreign keys."
                );
            }

            this.database.exec(
                "COMMIT"
            );

        } catch (error) {

            this.database.exec(
                "ROLLBACK"
            );

            throw error;
        }
    }

    getUserByUsername(
        username
    ) {

        return this.database
            .prepare(
                `
                SELECT
                    user_id AS userId,
                    username,
                    display_name AS displayName,
                    password_salt AS passwordSalt,
                    password_hash AS passwordHash,
                    is_admin AS isAdmin
                FROM users
                WHERE username = ?
                `
            )
            .get(
                username.toLowerCase()
            ) || null;
    }

    getUserById(
        userId
    ) {

        return this.database
            .prepare(
                `
                SELECT
                    user_id AS userId,
                    username,
                    display_name AS displayName,
                    password_salt AS passwordSalt,
                    password_hash AS passwordHash,
                    is_admin AS isAdmin
                FROM users
                WHERE user_id = ?
                `
            )
            .get(
                userId
            ) || null;
    }

    insertUser(
        user
    ) {

        this.database
            .prepare(
                `
                INSERT INTO users (
                    user_id,
                    username,
                    display_name,
                    password_salt,
                    password_hash,
                    is_admin,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                `
            )
            .run(
                user.userId,
                user.username.toLowerCase(),
                user.displayName,
                user.passwordSalt,
                user.passwordHash,
                user.isAdmin ? 1 : 0,
                Date.now()
            );
    }

    saveSession(
        session
    ) {

        this.database.exec(
            "BEGIN IMMEDIATE"
        );

        try {

            this.database
                .prepare(
                    `
                    INSERT INTO refresh_sessions (
                        refresh_token,
                        user_id,
                        expires_at,
                        created_at
                    )
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(refresh_token)
                    DO UPDATE SET
                        user_id = excluded.user_id,
                        expires_at = excluded.expires_at
                    `
                )
                .run(
                    tokenDigest(
                        session.refreshToken
                    ),
                    session.userId,
                    session.refreshTokenExpiresAt,
                    Date.now()
                );

            this.database
                .prepare(
                    `
                    INSERT INTO access_sessions (
                        access_token,
                        refresh_token,
                        user_id,
                        expires_at,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, ?)
                    `
                )
                .run(
                    tokenDigest(
                        session.accessToken
                    ),
                    tokenDigest(
                        session.refreshToken
                    ),
                    session.userId,
                    session.accessTokenExpiresAt,
                    Date.now()
                );

            this.database.exec(
                "COMMIT"
            );

        } catch (error) {

            this.database.exec(
                "ROLLBACK"
            );

            throw error;
        }
    }

    rotateSession(
        oldRefreshToken,
        session
    ) {

        this.database.exec(
            "BEGIN IMMEDIATE"
        );

        try {

            const consumed =
                this.database
                    .prepare(
                        `
                        DELETE FROM refresh_sessions
                        WHERE refresh_token = ?
                          AND user_id = ?
                          AND expires_at > ?
                        `
                    )
                    .run(
                        tokenDigest(
                            oldRefreshToken
                        ),
                        session.userId,
                        Date.now()
                    );

            if (
                Number(
                    consumed.changes
                ) !==
                1
            ) {

                this.database.exec(
                    "ROLLBACK"
                );

                return false;
            }

            this.database
                .prepare(
                    `
                    INSERT INTO refresh_sessions (
                        refresh_token,
                        user_id,
                        expires_at,
                        created_at
                    )
                    VALUES (?, ?, ?, ?)
                    `
                )
                .run(
                    tokenDigest(
                        session.refreshToken
                    ),
                    session.userId,
                    session.refreshTokenExpiresAt,
                    Date.now()
                );

            this.database
                .prepare(
                    `
                    INSERT INTO access_sessions (
                        access_token,
                        refresh_token,
                        user_id,
                        expires_at,
                        created_at
                    )
                    VALUES (?, ?, ?, ?, ?)
                    `
                )
                .run(
                    tokenDigest(
                        session.accessToken
                    ),
                    tokenDigest(
                        session.refreshToken
                    ),
                    session.userId,
                    session.accessTokenExpiresAt,
                    Date.now()
                );

            this.database.exec(
                "COMMIT"
            );

            return true;

        } catch (error) {

            this.database.exec(
                "ROLLBACK"
            );

            throw error;
        }
    }

    getAccessSession(
        accessToken
    ) {

        const session =
            this.database
                .prepare(
                    `
                    SELECT
                        a.access_token AS storedAccessToken,
                        a.refresh_token AS storedRefreshToken,
                        a.user_id AS userId,
                        a.expires_at AS expiresAt,
                        u.display_name AS userName
                    FROM access_sessions a
                    JOIN users u
                        ON u.user_id = a.user_id
                    WHERE a.access_token = ?
                    `
                )
                .get(
                    tokenDigest(
                        accessToken
                    )
                );

        if (!session) {
            return null;
        }

        if (
            Number(
                session.expiresAt
            ) <=
            Date.now()
        ) {

            this.database
                .prepare(
                    "DELETE FROM access_sessions WHERE access_token = ?"
                )
                .run(
                    session.storedAccessToken
                );

            return null;
        }

        return session;
    }

    getRefreshSession(
        refreshToken
    ) {

        const session =
            this.database
                .prepare(
                    `
                    SELECT
                        r.refresh_token AS storedRefreshToken,
                        r.user_id AS userId,
                        r.expires_at AS expiresAt,
                        u.display_name AS userName
                    FROM refresh_sessions r
                    JOIN users u
                        ON u.user_id = r.user_id
                    WHERE r.refresh_token = ?
                    `
                )
                .get(
                    tokenDigest(
                        refreshToken
                    )
                );

        if (!session) {
            return null;
        }

        if (
            Number(
                session.expiresAt
            ) <=
            Date.now()
        ) {

            this.revokeRefreshSession(
                refreshToken
            );

            return null;
        }

        return session;
    }

    revokeRefreshSession(
        refreshToken
    ) {

        this.database
            .prepare(
                "DELETE FROM refresh_sessions WHERE refresh_token = ?"
            )
            .run(
                tokenDigest(
                    refreshToken
                )
            );
    }

    importLegacyState(
        legacyState
    ) {

        if (
            !legacyState ||
            this.countIncidents() >
                0
        ) {
            return;
        }

        this.database.exec(
            "BEGIN IMMEDIATE"
        );

        try {

            for (
                const incident of
                Object.values(
                    legacyState.incidents ||
                    {}
                )
            ) {

                const timestamp =
                    Date.now();

                this.database
                    .prepare(
                        `
                        INSERT OR IGNORE INTO incidents (
                            incident_id,
                            title,
                            status,
                            severity,
                            sequence,
                            server_owned,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        `
                    )
                    .run(
                        incident.id,
                        incident.title ||
                            "Existing Relay incident",
                        incident.status ||
                            "Active",
                        incident.severity ||
                            "MEDIUM",
                        Number(
                            incident.sequence ||
                            0
                        ),
                        incident.serverOwned
                            ? 1
                            : 0,
                        timestamp,
                        timestamp
                    );

                for (
                    const event of
                    incident.history ||
                    []
                ) {

                    this.database
                        .prepare(
                            `
                            INSERT OR IGNORE INTO incident_events (
                                event_id,
                                incident_id,
                                event_type,
                                sequence,
                                occurred_at,
                                payload_json
                            )
                            VALUES (?, ?, ?, ?, ?, ?)
                            `
                        )
                        .run(
                            event.eventId,
                            event.incidentId,
                            event.type,
                            Number(
                                event.sequence ||
                                0
                            ),
                            Number(
                                event.occurredAt ||
                                timestamp
                            ),
                            JSON.stringify(
                                event
                            )
                        );
                }
            }

            for (
                const command of
                Object.values(
                    legacyState.processedCommands ||
                    {}
                )
            ) {

                if (
                    this.getIncident(
                        command.incidentId
                    )
                ) {

                    this.database
                        .prepare(
                            `
                            INSERT OR IGNORE INTO processed_commands (
                                command_id,
                                incident_id,
                                command_type,
                                severity,
                                processed_at
                            )
                            VALUES (?, ?, ?, ?, ?)
                            `
                        )
                        .run(
                            command.commandId,
                            command.incidentId,
                            command.type ||
                                "incident.severity.update",
                            command.severity ||
                                null,
                            Number(
                                command.processedAt ||
                                Date.now()
                            )
                        );
                }
            }

            for (
                const entry of
                Object.values(
                    legacyState.timelineEntries ||
                    {}
                )
            ) {

                if (
                    this.getIncident(
                        entry.incidentId
                    )
                ) {

                    this.database
                        .prepare(
                            `
                            INSERT OR IGNORE INTO timeline_entries (
                                entry_id,
                                incident_id,
                                event_id,
                                message,
                                author,
                                occurred_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?)
                            `
                        )
                        .run(
                            entry.entryId,
                            entry.incidentId,
                            entry.eventId,
                            entry.message,
                            entry.author,
                            Number(
                                entry.occurredAt ||
                                Date.now()
                            )
                        );
                }
            }

            this.database.exec(
                "COMMIT"
            );

        } catch (error) {

            this.database.exec(
                "ROLLBACK"
            );

            throw error;
        }
    }

    getIncident(
        incidentId
    ) {

        return this.database
            .prepare(
                `
                SELECT
                    incident_id AS id,
                    title,
                    status,
                    severity,
                    sequence,
                    server_owned AS serverOwned
                FROM incidents
                WHERE incident_id = ?
                `
            )
            .get(
                incidentId
            ) || null;
    }

    ensureIncident(
        incidentId,
        defaults = {}
    ) {

        const existing =
            this.getIncident(
                incidentId
            );

        if (existing) {
            return existing;
        }

        const incident = {
            id:
                incidentId,
            title:
                defaults.title ||
                "Existing Relay incident",
            status:
                defaults.status ||
                "Active",
            severity:
                defaults.severity ||
                "MEDIUM",
            sequence:
                Number(
                    defaults.sequence ||
                    0
                ),
            serverOwned:
                defaults.serverOwned ===
                true
        };

        const timestamp =
            Date.now();

        this.database
            .prepare(
                `
                INSERT INTO incidents (
                    incident_id,
                    title,
                    status,
                    severity,
                    sequence,
                    server_owned,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                `
            )
            .run(
                incident.id,
                incident.title,
                incident.status,
                incident.severity,
                incident.sequence,
                incident.serverOwned
                    ? 1
                    : 0,
                timestamp,
                timestamp
            );

        return incident;
    }

    getProcessedCommand(
        commandId
    ) {

        return this.database
            .prepare(
                `
                SELECT
                    command_id AS commandId,
                    incident_id AS incidentId,
                    command_type AS type,
                    severity,
                    processed_at AS processedAt
                FROM processed_commands
                WHERE command_id = ?
                `
            )
            .get(
                commandId
            ) || null;
    }

    applySeverityCommand(
        commandId,
        incidentId,
        severity,
        occurredAt
    ) {

        const existing =
            this.getProcessedCommand(
                commandId
            );

        if (existing) {

            return {
                duplicate: true,
                incident:
                    this.getIncident(
                        existing.incidentId
                    ),
                event:
                    null
            };
        }

        this.database.exec(
            "BEGIN IMMEDIATE"
        );

        try {

            const incident =
                this.ensureIncident(
                    incidentId
                );

            const nextSequence =
                Number(
                    incident.serverOwned
                ) ===
                1 ||
                incident.serverOwned ===
                true
                    ? Number(
                        incident.sequence
                    ) + 1
                    : 0;

            this.database
                .prepare(
                    `
                    UPDATE incidents
                    SET severity = ?,
                        sequence = ?,
                        updated_at = ?
                    WHERE incident_id = ?
                    `
                )
                .run(
                    severity,
                    nextSequence,
                    occurredAt,
                    incidentId
                );

            const event = {
                type:
                    "incident.updated",
                eventId:
                    "EVT-SEVERITY-" +
                    commandId,
                incidentId,
                occurredAt,
                severity,
                sequence:
                    nextSequence
            };

            if (
                Number(
                    incident.serverOwned
                ) ===
                    1 ||
                incident.serverOwned ===
                    true
            ) {

                this.database
                    .prepare(
                        `
                        INSERT INTO incident_events (
                            event_id,
                            incident_id,
                            event_type,
                            sequence,
                            occurred_at,
                            payload_json
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        `
                    )
                    .run(
                        event.eventId,
                        incidentId,
                        event.type,
                        nextSequence,
                        occurredAt,
                        JSON.stringify(
                            event
                        )
                    );
            }

            this.database
                .prepare(
                    `
                    INSERT INTO processed_commands (
                        command_id,
                        incident_id,
                        command_type,
                        severity,
                        processed_at
                    )
                    VALUES (?, ?, ?, ?, ?)
                    `
                )
                .run(
                    commandId,
                    incidentId,
                    "incident.severity.update",
                    severity,
                    occurredAt
                );

            this.database.exec(
                "COMMIT"
            );

            return {
                duplicate: false,
                incident:
                    this.getIncident(
                        incidentId
                    ),
                event
            };

        } catch (error) {

            this.database.exec(
                "ROLLBACK"
            );

            throw error;
        }
    }

    saveTimelineEntry(
        entry
    ) {

        const existing =
            this.getTimelineEntry(
                entry.entryId
            );

        if (existing) {
            return existing;
        }

        this.ensureIncident(
            entry.incidentId
        );

        this.database
            .prepare(
                `
                INSERT INTO timeline_entries (
                    entry_id,
                    incident_id,
                    event_id,
                    message,
                    author,
                    occurred_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                `
            )
            .run(
                entry.entryId,
                entry.incidentId,
                entry.eventId,
                entry.message,
                entry.author,
                entry.occurredAt
            );

        return entry;
    }

    getTimelineEntry(
        entryId
    ) {

        const row =
            this.database
                .prepare(
                    `
                    SELECT
                        entry_id AS entryId,
                        incident_id AS incidentId,
                        event_id AS eventId,
                        message,
                        author,
                        occurred_at AS occurredAt
                    FROM timeline_entries
                    WHERE entry_id = ?
                    `
                )
                .get(
                    entryId
                );

        if (!row) {
            return null;
        }

        return {
            type:
                "timeline.entry.added",
            ...row
        };
    }

    getReplay(
        incidentId,
        fromSequence,
        throughSequence
    ) {

        return this.database
            .prepare(
                `
                SELECT payload_json AS payloadJson
                FROM incident_events
                WHERE incident_id = ?
                  AND sequence >= ?
                  AND sequence <= ?
                ORDER BY sequence ASC
                `
            )
            .all(
                incidentId,
                fromSequence,
                throughSequence
            )
            .map(
                row =>
                    JSON.parse(
                        row.payloadJson
                    )
            );
    }

    countIncidents() {

        const row =
            this.database
                .prepare(
                    "SELECT COUNT(*) AS count FROM incidents"
                )
                .get();

        return Number(
            row.count
        );
    }

    grantIncidentAccess(
        userId,
        incidentId,
        role =
            "operator"
    ) {

        if (
            !this.getIncident(
                incidentId
            )
        ) {
            throw new Error(
                "Cannot grant access to unknown incident: " +
                    incidentId
            );
        }

        this.database
            .prepare(
                `
                INSERT INTO incident_access (
                    user_id,
                    incident_id,
                    role,
                    created_at
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT(user_id, incident_id)
                DO UPDATE SET
                    role = excluded.role
                `
            )
            .run(
                userId,
                incidentId,
                role,
                Date.now()
            );
    }

    upsertPushRegistration(
        userId,
        token
    ) {

        this.database
            .prepare(
                `
                INSERT INTO push_registrations (
                    token,
                    user_id,
                    platform,
                    updated_at
                )
                VALUES (?, ?, 'android', ?)
                ON CONFLICT(token)
                DO UPDATE SET
                    user_id = excluded.user_id,
                    platform = excluded.platform,
                    updated_at = excluded.updated_at
                `
            )
            .run(
                token,
                userId,
                Date.now()
            );
    }

    removePushRegistration(
        userId,
        token
    ) {

        this.database
            .prepare(
                `
                DELETE FROM push_registrations
                WHERE user_id = ?
                  AND token = ?
                `
            )
            .run(
                userId,
                token
            );
    }

    countPushRegistrationsForUser(
        userId
    ) {

        const row =
            this.database
                .prepare(
                    `
                    SELECT COUNT(*) AS count
                    FROM push_registrations
                    WHERE user_id = ?
                    `
                )
                .get(
                    userId
                );

        return Number(
            row.count
        );
    }

    getPushTargetsForIncident(
        incidentId
    ) {

        return this.database
            .prepare(
                `
                SELECT DISTINCT
                    p.token,
                    p.user_id AS userId
                FROM push_registrations p
                JOIN users u
                    ON u.user_id = p.user_id
                LEFT JOIN incident_access ia
                    ON ia.user_id = p.user_id
                   AND ia.incident_id = ?
                WHERE u.is_admin = 1
                   OR ia.user_id IS NOT NULL
                ORDER BY p.user_id, p.token
                `
            )
            .all(
                incidentId
            );
    }

    canAccessIncident(
        userId,
        incidentId
    ) {

        const user =
            this.getUserById(
                userId
            );

        if (!user) {
            return false;
        }

        if (
            Number(
                user.isAdmin
            ) ===
            1
        ) {
            return true;
        }

        const access =
            this.database
                .prepare(
                    `
                    SELECT 1 AS allowed
                    FROM incident_access
                    WHERE user_id = ?
                      AND incident_id = ?
                    `
                )
                .get(
                    userId,
                    incidentId
                );

        return Boolean(
            access
        );
    }

    countUsers() {

        const row =
            this.database
                .prepare(
                    "SELECT COUNT(*) AS count FROM users"
                )
                .get();

        return Number(
            row.count
        );
    }

    backupTo(
        destinationPath
    ) {

        const escaped =
            String(
                destinationPath
            )
                .replaceAll(
                    "'",
                    "''"
                );

        this.database.exec(
            "VACUUM INTO '" +
                escaped +
                "'"
        );
    }

    integrityCheck() {

        const row =
            this.database
                .prepare(
                    "PRAGMA integrity_check"
                )
                .get();

        return row
            ?.integrity_check ===
            "ok";
    }
}

function createStorage(
    backendDir,
    explicitDatabasePath =
        null
) {

    const databasePath =
        explicitDatabasePath ||
        process.env.RELAY_DATABASE_PATH ||
        path.join(
            backendDir,
            "relay.sqlite"
        );

    return new RelayStorage(
        databasePath
    );
}

module.exports = {
    RelayStorage,
    createStorage
};
