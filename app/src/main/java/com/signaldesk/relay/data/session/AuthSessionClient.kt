package com.signaldesk.relay.data.session

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AuthenticatedSession(
    val userId: String,
    val userName: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long
)

class AuthSessionClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient()
) {

    suspend fun login(
        username: String,
        password: String
    ): AuthenticatedSession {

        val body =
            JSONObject()
                .put(
                    "username",
                    username
                )
                .put(
                    "password",
                    password
                )

        return postForSession(
            path =
                "/auth/login",
            body =
                body
        )
    }

    suspend fun refreshSession(
        refreshToken: String
    ): AuthenticatedSession {

        val body =
            JSONObject()
                .put(
                    "refreshToken",
                    refreshToken
                )

        return postForSession(
            path =
                "/auth/refresh",
            body =
                body
        )
    }

    suspend fun revokeSession(
        refreshToken: String
    ) {

        val body =
            JSONObject()
                .put(
                    "refreshToken",
                    refreshToken
                )
                .toString()
                .toRequestBody(
                    "application/json"
                        .toMediaType()
                )

        execute(
            Request.Builder()
                .url(
                    "$baseUrl/auth/revoke"
                )
                .post(body)
                .build()
        ).use { response ->

            if (
                response.code !=
                    204 &&
                response.code !=
                    401
            ) {

                throw IOException(
                    "Session revoke failed: HTTP " +
                        response.code
                )
            }
        }
    }

    private suspend fun postForSession(
        path: String,
        body: JSONObject
    ): AuthenticatedSession {

        val requestBody =
            body
                .toString()
                .toRequestBody(
                    "application/json"
                        .toMediaType()
                )

        val response =
            execute(
                Request.Builder()
                    .url(
                        baseUrl +
                            path
                    )
                    .post(
                        requestBody
                    )
                    .build()
            )

        response.use {

            if (
                !response.isSuccessful
            ) {

                throw IOException(
                    "Authentication failed: HTTP " +
                        response.code
                )
            }

            val json =
                JSONObject(
                    response.body
                        ?.string()
                        .orEmpty()
                )

            return AuthenticatedSession(
                userId =
                    json.getString(
                        "userId"
                    ),
                userName =
                    json.getString(
                        "userName"
                    ),
                accessToken =
                    json.getString(
                        "accessToken"
                    ),
                refreshToken =
                    json.getString(
                        "refreshToken"
                    ),
                accessTokenExpiresAt =
                    json.getLong(
                        "accessTokenExpiresAt"
                    )
            )
        }
    }

    private suspend fun execute(
        request: Request
    ): Response =
        suspendCancellableCoroutine {
            continuation ->

            val call =
                client.newCall(
                    request
                )

            call.enqueue(
                object : Callback {

                    override fun onFailure(
                        call: Call,
                        e: IOException
                    ) {
                        if (
                            continuation.isActive
                        ) {
                            continuation
                                .resumeWithException(
                                    e
                                )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response
                    ) {
                        if (
                            continuation.isActive
                        ) {
                            continuation.resume(
                                response
                            )
                        } else {
                            response.close()
                        }
                    }
                }
            )

            continuation
                .invokeOnCancellation {
                    call.cancel()
                }
        }
}
