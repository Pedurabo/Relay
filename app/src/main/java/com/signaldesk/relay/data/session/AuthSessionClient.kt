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
    val accessToken: String
)

class AuthSessionClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient()
) {

    suspend fun createDevelopmentSession():
        AuthenticatedSession =
        suspendCancellableCoroutine {
            continuation ->

            val body =
                JSONObject()
                    .put(
                        "userId",
                        "dev-relay-operator"
                    )
                    .put(
                        "userName",
                        "Relay Operator"
                    )
                    .toString()
                    .toRequestBody(
                        "application/json"
                            .toMediaType()
                    )

            val request =
                Request.Builder()
                    .url(
                        "$baseUrl/auth/dev-session"
                    )
                    .post(body)
                    .build()

            val call =
                client.newCall(
                    request
                )

            call.enqueue(
                object : Callback {

                    override fun onFailure(
                        call: Call,
                        error: IOException
                    ) {
                        if (
                            continuation.isActive
                        ) {
                            continuation
                                .resumeWithException(
                                    error
                                )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response
                    ) {

                        response.use {

                            if (
                                !response.isSuccessful
                            ) {

                                if (
                                    continuation.isActive
                                ) {
                                    continuation
                                        .resumeWithException(
                                            IOException(
                                                "Authentication failed: HTTP " +
                                                    response.code
                                            )
                                        )
                                }

                                return
                            }

                            val json =
                                JSONObject(
                                    response.body
                                        ?.string()
                                        .orEmpty()
                                )

                            val session =
                                AuthenticatedSession(
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
                                        )
                                )

                            if (
                                continuation.isActive
                            ) {
                                continuation.resume(
                                    session
                                )
                            }
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
