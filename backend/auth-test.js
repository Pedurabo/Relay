const { spawn } = require("child_process");
const path = require("path");
const WebSocket = require("ws");

const backendDir = __dirname;
const TEST_PORT = 9100;
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

                    const text =
                        data.toString();

                    process.stdout.write(
                        text
                    );

                    if (
                        text.includes(
                            "RELAY_DEV_SERVER_READY|" + TEST_PORT
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
                    "ws://127.0.0.1:" + TEST_PORT,
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
                        )
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
            await fetch(
                "http://127.0.0.1:" + TEST_PORT + "/auth/dev-session",
                {
                    method:
                        "POST",
                    headers: {
                        "Content-Type":
                            "application/json"
                    },
                    body:
                        JSON.stringify({
                            userId:
                                "dev-relay-operator",
                            userName:
                                "Relay Operator"
                        })
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
            !session.accessToken
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

        console.log(
            "AUTH_BACKEND_GREEN"
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
