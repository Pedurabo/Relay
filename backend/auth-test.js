const { spawn } = require("child_process");
const path = require("path");
const WebSocket = require("ws");

const backendDir = __dirname;
const TEST_PORT = 9100;
const ACCESS_TTL_MS = 250;
const TEST_USERNAME =
    "relay.operator";
const TEST_PASSWORD =
    "RelayDemo123!";
const serverPath = path.join(
    backendDir,
    "server.js"
);

function waitForServer(
    child
) {

    return new Promise(
        (
            resolve,
            reject
        ) => {

            const timeout =
                setTimeout(
                    () => {
                        reject(
                            new Error(
                                "Server start timeout."
                            )
                        );
                    },
                    10_000
                );

            child.stdout.on(
                "data",
                data => {

                    const output =
                        data.toString();

                    process.stdout.write(
                        output
                    );

                    if (
                        output.includes(
                            "RELAY_DEV_SERVER_READY|" +
                                TEST_PORT
                        )
                    ) {

                        clearTimeout(
                            timeout
                        );

                        resolve();
                    }
                }
            );

            child.stderr.on(
                "data",
                data => {
                    process.stderr.write(
                        data.toString()
                    );
                }
            );

            child.on(
                "exit",
                code => {

                    clearTimeout(
                        timeout
                    );

                    reject(
                        new Error(
                            "Server exited early: " +
                                code
                        )
                    );
                }
            );
        }
    );
}

function delay(
    millis
) {
    return new Promise(
        resolve => {
            setTimeout(
                resolve,
                millis
            );
        }
    );
}

function openSocket(
    token
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
                                token
                        }
                    }
                );

            const timeout =
                setTimeout(
                    () => {

                        socket.terminate();

                        reject(
                            new Error(
                                "WebSocket timeout."
                            )
                        );
                    },
                    5_000
                );

            socket.once(
                "open",
                () => {

                    clearTimeout(
                        timeout
                    );

                    socket.close();

                    resolve(
                        "opened"
                    );
                }
            );

            socket.once(
                "unexpected-response",
                (
                    request,
                    response
                ) => {

                    clearTimeout(
                        timeout
                    );

                    resolve(
                        "rejected:" +
                            response.statusCode
                    );
                }
            );

            socket.once(
                "error",
                error => {

                    if (
                        String(
                            error.message
                        ).includes(
                            "Unexpected server response: 401"
                        )
                    ) {

                        clearTimeout(
                            timeout
                        );

                        resolve(
                            "rejected:401"
                        );

                        return;
                    }

                    clearTimeout(
                        timeout
                    );

                    reject(
                        error
                    );
                }
            );
        }
    );
}

async function postJson(
    pathName,
    body
) {

    return fetch(
        "http://127.0.0.1:" +
            TEST_PORT +
            pathName,
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

async function main() {

    const child =
        spawn(
            process.execPath,
            [
                serverPath
            ],
            {
                cwd:
                    backendDir,
                env: {
                    ...process.env,
                    RELAY_PORT:
                        String(
                            TEST_PORT
                        ),
                    RELAY_ACCESS_TTL_MS:
                        String(
                            ACCESS_TTL_MS
                        ),
                    RELAY_BOOTSTRAP_USERNAME:
                        TEST_USERNAME,
                    RELAY_BOOTSTRAP_PASSWORD:
                        TEST_PASSWORD
                },
                stdio: [
                    "ignore",
                    "pipe",
                    "pipe"
                ]
            }
        );

    try {

        await waitForServer(
            child
        );

        const response =
            await postJson(
                "/auth/dev-session",
                {
                    userId:
                        "dev-relay-operator",
                    userName:
                        "Relay Operator"
                }
            );

        if (
            response.status !==
            200
        ) {
            throw new Error(
                "Expected auth 200, got " +
                    response.status
            );
        }

        const session =
            await response.json();

        if (
            session.userId !==
                "dev-relay-operator" ||
            !session.accessToken ||
            !session.refreshToken ||
            !session.accessTokenExpiresAt
        ) {
            throw new Error(
                "Invalid session payload."
            );
        }

        const valid =
            await openSocket(
                session.accessToken
            );

        if (
            valid !==
            "opened"
        ) {
            throw new Error(
                "Valid token was rejected: " +
                    valid
            );
        }

        const invalid =
            await openSocket(
                "fabricated-token"
            );

        if (
            invalid !==
            "rejected:401"
        ) {
            throw new Error(
                "Invalid token was not rejected: " +
                    invalid
            );
        }

        await delay(
            ACCESS_TTL_MS +
                150
        );

        const expired =
            await openSocket(
                session.accessToken
            );

        if (
            expired !==
            "rejected:401"
        ) {
            throw new Error(
                "Expired access token was not rejected: " +
                    expired
            );
        }

        const refreshResponse =
            await postJson(
                "/auth/refresh",
                {
                    refreshToken:
                        session.refreshToken
                }
            );

        if (
            refreshResponse.status !==
            200
        ) {
            throw new Error(
                "Expected refresh 200, got " +
                    refreshResponse.status
            );
        }

        const refreshed =
            await refreshResponse.json();

        if (
            refreshed.userId !==
                session.userId
        ) {
            throw new Error(
                "Refresh changed immutable userId."
            );
        }

        if (
            refreshed.accessToken ===
                session.accessToken
        ) {
            throw new Error(
                "Refresh did not rotate access token."
            );
        }

        const refreshedSocket =
            await openSocket(
                refreshed.accessToken
            );

        if (
            refreshedSocket !==
            "opened"
        ) {
            throw new Error(
                "Refreshed access token was rejected: " +
                    refreshedSocket
            );
        }

        const revokeResponse =
            await postJson(
                "/auth/revoke",
                {
                    refreshToken:
                        refreshed.refreshToken
                }
            );

        if (
            revokeResponse.status !==
            204
        ) {
            throw new Error(
                "Expected revoke 204, got " +
                    revokeResponse.status
            );
        }

        const revokedAccess =
            await openSocket(
                refreshed.accessToken
            );

        if (
            revokedAccess !==
            "rejected:401"
        ) {
            throw new Error(
                "Revoked access token remained usable: " +
                    revokedAccess
            );
        }

        const revokedRefreshResponse =
            await postJson(
                "/auth/refresh",
                {
                    refreshToken:
                        refreshed.refreshToken
                }
            );

        if (
            revokedRefreshResponse.status !==
            401
        ) {
            throw new Error(
                "Revoked refresh token remained usable: " +
                    revokedRefreshResponse.status
            );
        }

        console.log(
            "AUTH_LOGOUT_REVOCATION_GREEN"
        );

    } finally {

        child.kill(
            "SIGTERM"
        );
    }
}

main()
    .catch(
        error => {

            console.error(
                error
            );

            process.exitCode =
                1;
        }
    );
