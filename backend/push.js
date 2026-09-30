const fs = require("fs");
const crypto = require("crypto");

function base64Url(
    value
) {

    return Buffer
        .from(
            value
        )
        .toString(
            "base64url"
        );
}

function createServiceAccountJwt(
    serviceAccount
) {

    const nowSeconds =
        Math.floor(
            Date.now() /
            1000
        );

    const header =
        base64Url(
            JSON.stringify({
                alg:
                    "RS256",
                typ:
                    "JWT"
            })
        );

    const claim =
        base64Url(
            JSON.stringify({
                iss:
                    serviceAccount.client_email,
                scope:
                    "https://www.googleapis.com/auth/firebase.messaging",
                aud:
                    "https://oauth2.googleapis.com/token",
                iat:
                    nowSeconds,
                exp:
                    nowSeconds +
                    3600
            })
        );

    const unsigned =
        header +
        "." +
        claim;

    const signature =
        crypto
            .sign(
                "RSA-SHA256",
                Buffer.from(
                    unsigned
                ),
                serviceAccount.private_key
            )
            .toString(
                "base64url"
            );

    return (
        unsigned +
        "." +
        signature
    );
}

function buildIncidentPushPayload(
    event,
    content,
    incident =
        {}
) {

    return {
        eventId:
            String(
                event.eventId
            ),
        eventType:
            String(
                event.type ||
                ""
            ),
        incidentId:
            String(
                event.incidentId
            ),
        severity:
            String(
                event.severity ||
                incident.severity ||
                ""
            ).toUpperCase(),
        occurredAt:
            String(
                event.occurredAt ||
                Date.now()
            ),
        sequence:
            String(
                event.sequence ||
                incident.sequence ||
                0
            ),
        incidentTitle:
            String(
                incident.title ||
                event.title ||
                ""
            ),
        incidentStatus:
            String(
                incident.status ||
                event.status ||
                ""
            ),
        entryId:
            String(
                event.entryId ||
                ""
            ),
        message:
            String(
                event.message ||
                ""
            ),
        author:
            String(
                event.author ||
                ""
            ),
        content:
            String(
                content
            )
    };
}

class FcmPushSender {

    constructor(
        projectId,
        credentialPath
    ) {

        this.projectId =
            projectId;

        this.serviceAccount =
            JSON.parse(
                fs.readFileSync(
                    credentialPath,
                    "utf8"
                )
            );

        this.accessToken =
            null;

        this.accessTokenExpiresAt =
            0;
    }

    async sendToTokens(
        tokens,
        data
    ) {

        if (
            tokens.length ===
            0
        ) {
            return;
        }

        const accessToken =
            await this
                .getAccessToken();

        await Promise.all(
            tokens.map(
                async token => {

                    const response =
                        await fetch(
                            "https://fcm.googleapis.com/v1/projects/" +
                                encodeURIComponent(
                                    this.projectId
                                ) +
                                "/messages:send",
                            {
                                method:
                                    "POST",
                                headers: {
                                    Authorization:
                                        "Bearer " +
                                        accessToken,
                                    "Content-Type":
                                        "application/json"
                                },
                                body:
                                    JSON.stringify({
                                        message: {
                                            token,
                                            data,
                                            android: {
                                                priority:
                                                    "high",
                                                ttl:
                                                    "300s"
                                            }
                                        }
                                    })
                            }
                        );

                    if (
                        !response.ok
                    ) {

                        const body =
                            await response
                                .text();

                        throw new Error(
                            "FCM send failed: HTTP " +
                                response.status +
                                " " +
                                body
                        );
                    }
                }
            )
        );
    }

    async getAccessToken() {

        if (
            this.accessToken &&
            this.accessTokenExpiresAt -
                60_000 >
                Date.now()
        ) {
            return this.accessToken;
        }

        const assertion =
            createServiceAccountJwt(
                this.serviceAccount
            );

        const body =
            new URLSearchParams({
                grant_type:
                    "urn:ietf:params:oauth:grant-type:jwt-bearer",
                assertion
            });

        const response =
            await fetch(
                "https://oauth2.googleapis.com/token",
                {
                    method:
                        "POST",
                    headers: {
                        "Content-Type":
                            "application/x-www-form-urlencoded"
                    },
                    body
                }
            );

        if (
            !response.ok
        ) {

            throw new Error(
                "FCM credential exchange failed: HTTP " +
                    response.status
            );
        }

        const payload =
            await response.json();

        this.accessToken =
            payload.access_token;

        this.accessTokenExpiresAt =
            Date.now() +
            Number(
                payload.expires_in ||
                3600
            ) *
                1000;

        return this.accessToken;
    }
}

class DisabledPushSender {

    async sendToTokens(
        tokens,
        data
    ) {
        void tokens;
        void data;
    }
}

function createPushSender(
    env =
        process.env
) {

    const projectId =
        String(
            env.RELAY_FIREBASE_PROJECT_ID ||
            ""
        )
            .trim();

    const credentialPath =
        String(
            env.GOOGLE_APPLICATION_CREDENTIALS ||
            ""
        )
            .trim();

    if (
        !projectId ||
        !credentialPath
    ) {

        return new DisabledPushSender();
    }

    return new FcmPushSender(
        projectId,
        credentialPath
    );
}

module.exports = {
    buildIncidentPushPayload,
    createPushSender
};
