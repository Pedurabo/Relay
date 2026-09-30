class LoginRateLimiter {

    constructor({
        usernameLimit,
        clientLimit,
        windowMs
    }) {

        this.usernameLimit =
            usernameLimit;

        this.clientLimit =
            clientLimit;

        this.windowMs =
            windowMs;

        this.usernameFailures =
            new Map();

        this.clientFailures =
            new Map();
    }

    check(
        username,
        clientKey,
        nowMillis =
            Date.now()
    ) {

        const usernameState =
            this.#activeState(
                this.usernameFailures,
                username,
                nowMillis
            );

        const clientState =
            this.#activeState(
                this.clientFailures,
                clientKey,
                nowMillis
            );

        const limited =
            (
                usernameState?.count ||
                0
            ) >=
                this.usernameLimit ||
            (
                clientState?.count ||
                0
            ) >=
                this.clientLimit;

        if (!limited) {
            return {
                allowed: true,
                retryAfterSeconds:
                    0
            };
        }

        const expiresAt =
            Math.max(
                usernameState?.expiresAt ||
                    0,
                clientState?.expiresAt ||
                    0
            );

        return {
            allowed: false,
            retryAfterSeconds:
                Math.max(
                    1,
                    Math.ceil(
                        (
                            expiresAt -
                            nowMillis
                        ) /
                        1000
                    )
                )
        };
    }

    recordFailure(
        username,
        clientKey,
        nowMillis =
            Date.now()
    ) {

        this.#increment(
            this.usernameFailures,
            username,
            nowMillis
        );

        this.#increment(
            this.clientFailures,
            clientKey,
            nowMillis
        );
    }

    recordSuccess(
        username
    ) {

        this.usernameFailures
            .delete(
                username
            );
    }

    #activeState(
        map,
        key,
        nowMillis
    ) {

        const state =
            map.get(
                key
            );

        if (!state) {
            return null;
        }

        if (
            state.expiresAt <=
            nowMillis
        ) {

            map.delete(
                key
            );

            return null;
        }

        return state;
    }

    #increment(
        map,
        key,
        nowMillis
    ) {

        const current =
            this.#activeState(
                map,
                key,
                nowMillis
            );

        if (current) {

            current.count +=
                1;

            return;
        }

        map.set(
            key,
            {
                count:
                    1,
                expiresAt:
                    nowMillis +
                    this.windowMs
            }
        );
    }
}

module.exports = {
    LoginRateLimiter
};
