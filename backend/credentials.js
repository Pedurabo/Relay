const crypto = require("crypto");

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

module.exports = {
    createUserRecord,
    verifyPassword
};
