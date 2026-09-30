const { spawn } = require("child_process");
const path = require("path");

const backendDir = __dirname;
const TEST_PORT = 9400;
const WINDOW_MS = 300;
const USERNAME = "relay.operator";
const PASSWORD = "RelayDemo123!";

function waitForServer(
    child
) {

    return new Promise(
        (
            resolve,
            reject
        ) => {

            const timer =
                setTimeout(
                    () =>
                        reject(
                            new Error(
                                "Server start timeout."
                            )
                        ),
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
                            timer
                        );

                        resolve();
                    }
                }
            );

            child.stderr.on(
                "data",
                data =>
                    process.stderr.write(
                        data.toString()
                    )
            );
        }
    );
}

function delay(
    millis
) {

    return new Promise(
        resolve =>
            setTimeout(
                resolve,
                millis
            )
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

    return {
        status:
            response.status,
        retryAfter:
            response.headers
                .get(
                    "retry-after"
                ),
        body:
            await response.json()
    };
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

async function assertFailedAttempts(
    username
) {

    for (
        let attempt = 1;
        attempt <= 3;
        attempt += 1
    ) {

        const result =
            await login(
                username,
                "wrong-password"
            );

        assert(
            result.status ===
                401,
            "Expected failed credential attempt " +
                attempt +
                " to return 401 for " +
                username +
                ", got " +
                result.status
        );

        assert(
            result.body.error ===
                "invalid_credentials",
            "Credential failure response leaked account state."
        );
    }

    const limited =
        await login(
            username,
            "wrong-password"
        );

    assert(
        limited.status ===
            429,
        "Expected fourth failed attempt to be rate limited for " +
            username +
            ", got " +
            limited.status
    );

    assert(
        limited.body.error ===
            "invalid_credentials",
        "Rate-limited response leaked account state."
    );

    assert(
        Number(
            limited.retryAfter
        ) >=
            1,
        "Rate-limited response did not include Retry-After."
    );
}

async function main() {

    const child =
        spawn(
            process.execPath,
            [
                path.join(
                    backendDir,
                    "server.js"
                )
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
                    RELAY_BOOTSTRAP_USERNAME:
                        USERNAME,
                    RELAY_BOOTSTRAP_PASSWORD:
                        PASSWORD,
                    RELAY_LOGIN_RATE_LIMIT_WINDOW_MS:
                        String(
                            WINDOW_MS
                        ),
                    RELAY_LOGIN_RATE_LIMIT_USERNAME_FAILURES:
                        "3",
                    RELAY_LOGIN_RATE_LIMIT_CLIENT_FAILURES:
                        "100"
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

        await assertFailedAttempts(
            USERNAME
        );

        await delay(
            WINDOW_MS +
                100
        );

        const recovered =
            await login(
                USERNAME,
                PASSWORD
            );

        assert(
            recovered.status ===
                200,
            "Valid credentials did not recover after rate-limit window."
        );

        const secondSuccess =
            await login(
                USERNAME,
                PASSWORD
            );

        assert(
            secondSuccess.status ===
                200,
            "Successful login was incorrectly throttled."
        );

        await assertFailedAttempts(
            "does.not.exist"
        );

        console.log(
            "LOGIN_RATE_LIMIT_GREEN"
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
