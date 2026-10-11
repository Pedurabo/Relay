
package com.signaldesk.relay.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class IncidentSnapshot(
    val id: String,
    val title: String,
    val status: String,
    val severity: String,
    val sequence: Long
)

class IncidentSnapshotHttpException(
    val statusCode: Int
) : IOException(
    "Incident snapshot failed: HTTP $statusCode"
)

class IncidentSnapshotClient(
    private val baseUrl: String,
    private val client: OkHttpClient =
        OkHttpClient()
) {

    suspend fun load(
        accessToken: String
    ): List<IncidentSnapshot> {

        require(
            accessToken.isNotBlank()
        )

        val response =
            execute(
                Request.Builder()
                    .url(
                        "$baseUrl/incidents"
                    )
                    .header(
                        "Authorization",
                        "Bearer $accessToken"
                    )
                    .get()
                    .build()
            )

        return withContext(
            Dispatchers.IO
        ) {

            response.use {

                if (
                    !response.isSuccessful
                ) {

                    throw IncidentSnapshotHttpException(
                        response.code
                    )
                }

                parseIncidentSnapshotResponse(
                    response.body
                        ?.string()
                        .orEmpty()
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

internal fun parseIncidentSnapshotResponse(
    body: String
): List<IncidentSnapshot> {

    val root =
        JSONObject(
            body
        )

    val incidents =
        root.getJSONArray(
            "incidents"
        )

    return buildList {

        for (
            index in
            0 until incidents.length()
        ) {

            val incident =
                incidents.getJSONObject(
                    index
                )

            add(
                IncidentSnapshot(
                    id =
                        incident.getString(
                            "id"
                        ),
                    title =
                        incident.getString(
                            "title"
                        ),
                    status =
                        incident.getString(
                            "status"
                        ),
                    severity =
                        incident
                            .optString(
                                "severity",
                                "MEDIUM"
                            )
                            .uppercase(),
                    sequence =
                        incident.optLong(
                            "sequence",
                            0L
                        )
                )
            )
        }
    }
}
