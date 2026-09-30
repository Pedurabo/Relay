const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");
const { loadConfig } = require("./config");
const { RelayStorage } = require("./storage");
const { createUserRecord } = require("./credentials");

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

function expectThrow(
    fn,
    fragment
) {

    let thrown =
        false;

    try {

        fn();

    } catch (error) {

        thrown =
            true;

        assert(
            String(
                error.message
            ).includes(
                fragment
            ),
            "Unexpected error: " +
                error.message
        );
    }

    assert(
        thrown,
        "Expected configuration failure containing: " +
            fragment
    );
}

function cleanup(
    file
) {

    for (
        const suffix of
        [
            "",
            "-wal",
            "-shm"
        ]
    ) {

        const candidate =
            file +
            suffix;

        if (
            fs.existsSync(
                candidate
            )
        ) {
            fs.unlinkSync(
                candidate
            );
        }
    }
}

function main() {

    expectThrow(
        () =>
            loadConfig({
                RELAY_ENV:
                    "production"
            }),
        "RELAY_DATABASE_PATH"
    );

    expectThrow(
        () =>
            loadConfig({
                RELAY_ENV:
                    "production",
                RELAY_DATABASE_PATH:
                    "prod.sqlite",
                RELAY_BOOTSTRAP_PASSWORD:
                    "unsafe"
            }),
        "bootstrap credentials"
    );

    expectThrow(
        () =>
            loadConfig({
                RELAY_ENV:
                    "production",
                RELAY_DATABASE_PATH:
                    "prod.sqlite",
                RELAY_ENABLE_TEST_SHUTDOWN:
                    "1"
            }),
        "TEST_SHUTDOWN"
    );

    const emptyDb =
        path.join(
            __dirname,
            "relay-prod-empty-" +
                process.pid +
                ".sqlite"
        );

    cleanup(
        emptyDb
    );

    const emptyStart =
        spawnSync(
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
                    RELAY_ENV:
                        "production",
                    RELAY_DATABASE_PATH:
                        emptyDb,
                    RELAY_PORT:
                        "9301"
                },
                encoding:
                    "utf8"
            }
        );

    assert(
        emptyStart.status !==
            0,
        "Production server started with an empty user store."
    );

    assert(
        (
            emptyStart.stderr +
            emptyStart.stdout
        ).includes(
            "Production user store is empty"
        ),
        "Production empty-user failure was not explicit."
    );

    cleanup(
        emptyDb
    );

    const readyDb =
        path.join(
            __dirname,
            "relay-prod-ready-" +
                process.pid +
                ".sqlite"
        );

    cleanup(
        readyDb
    );

    const storage =
        new RelayStorage(
            readyDb
        );

    storage.insertUser({
        ...createUserRecord(
            "prod-admin",
            "prod.admin",
            "Production Admin",
            "StrongTestPassword123!"
        ),
        isAdmin:
            true
    });

    storage.close();

    const config =
        loadConfig({
            RELAY_ENV:
                "production",
            RELAY_DATABASE_PATH:
                readyDb,
            RELAY_PORT:
                "9302"
        });

    assert(
        config.isProduction ===
            true,
        "Valid production config was not accepted."
    );

    cleanup(
        readyDb
    );

    console.log(
        "PRODUCTION_CONFIG_GREEN"
    );
}

try {

    main();

} catch (error) {

    console.error(
        error
    );

    process.exitCode =
        1;
}
