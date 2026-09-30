const path = require("path");
const { DatabaseSync } = require("node:sqlite");

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

            CREATE TABLE IF NOT EXISTS incident_access (
                user_id TEXT NOT NULL,
                incident_id TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY(user_id, incident_id),
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
            `
        );
    }

    close() {
        this.database.close();
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
                    session.refreshToken,
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
                    session.accessToken,
                    session.refreshToken,
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

    getAccessSession(
        accessToken
    ) {

        const session =
            this.database
                .prepare(
                    `
                    SELECT
                        a.access_token AS accessToken,
                        a.refresh_token AS refreshToken,
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
                    accessToken
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
                    accessToken
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
                        r.refresh_token AS refreshToken,
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
                    refreshToken
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
                refreshToken
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
}

function createStorage(
    backendDir
) {

    const databasePath =
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
