package com.signaldesk.relay.data.session

import java.io.IOException

enum class AuthLoginFailure {
    INVALID_CREDENTIALS,
    RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    SERVER_ERROR,
    UNEXPECTED
}

fun classifyAuthLoginFailure(
    error: Throwable
): AuthLoginFailure =
    when {
        error is AuthHttpException &&
            error.statusCode == 401 ->
            AuthLoginFailure.INVALID_CREDENTIALS

        error is AuthHttpException &&
            error.statusCode == 429 ->
            AuthLoginFailure.RATE_LIMITED

        error is AuthHttpException ->
            AuthLoginFailure.SERVER_ERROR

        error is IOException ->
            AuthLoginFailure.NETWORK_UNAVAILABLE

        else ->
            AuthLoginFailure.UNEXPECTED
    }
