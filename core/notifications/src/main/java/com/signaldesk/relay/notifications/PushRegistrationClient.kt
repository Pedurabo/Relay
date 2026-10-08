package com.signaldesk.relay.notifications

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

class PushRegistrationClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient()
) {

    suspend fun register(
        accessToken: String,
        registrationToken: String
    ) {

        postToken(
            path =
                "/push/register",
            accessToken =
                accessToken,
            registrationToken =
                registrationToken,
            expectedStatus =
                204
        )
    }

    suspend fun unregister(
        accessToken: String,
        registrationToken: String
    ) {

        postToken(
            path =
                "/push/unregister",
            accessToken =
                accessToken,
            registrationToken =
                registrationToken,
            expectedStatus =
                204
        )
    }

    private suspend fun postToken(
        path: String,
        accessToken: String,
        registrationToken: String,
        expectedStatus: Int
    ) {

        val body =
            JSONObject()
                .put(
                    "token",
                    registrationToken
                )
                .toString()
                .toRequestBody(
                    "application/json"
                        .toMediaType()
                )

        execute(
            Request.Builder()
                .url(
                    baseUrl +
                        path
                )
                .header(
                    "Authorization",
                    "Bearer $accessToken"
                )
                .post(
                    body
                )
                .build()
        ).use { response ->

            if (
                response.code !=
                expectedStatus
            ) {
                throw IOException(
                    "Push registration failed: HTTP " +
                        response.code
                )
            }
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
