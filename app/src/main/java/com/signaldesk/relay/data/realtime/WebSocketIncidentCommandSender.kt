package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.model.IncidentSeverity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.coroutines.resume

enum class IncidentCommandResult {
    ACCEPTED,
    REJECTED,
    TRANSPORT_FAILURE
}

class WebSocketIncidentCommandSender(
    private val url: String,
    private val tokenProvider: () -> String?,
    private val onSessionInvalidated:
        (String) -> Unit = {},
    private val client: OkHttpClient =
        OkHttpClient()
) {

    suspend fun updateSeverity(
        incidentId: String,
        severity: IncidentSeverity,
        commandId: String
    ): IncidentCommandResult {

        return withTimeoutOrNull(
            3000L
        ) {

            suspendCancellableCoroutine {
                continuation ->

                var completed =
                    false

                var socket:
                    WebSocket? =
                    null

                fun finish(
                    result:
                        IncidentCommandResult
                ) {
                    if (completed) {
                        return
                    }

                    completed =
                        true

                    if (
                        continuation.isActive
                    ) {
                        continuation.resume(
                            result
                        )
                    }
                }

                val requestBuilder =
                    Request
                        .Builder()
                        .url(url)

                val accessToken =
                    tokenProvider()
                        ?.takeIf {
                            it.isNotBlank()
                        }

                accessToken
                    ?.let {
                        token ->

                        requestBuilder.header(
                            "Authorization",
                            "Bearer $token"
                        )
                    }

                val listener =
                    object :
                        WebSocketListener() {

                        override fun onOpen(
                            webSocket:
                                WebSocket,
                            response:
                                Response
                        ) {

                            val payload =
                                JSONObject()
                                    .put(
                                        "type",
                                        "incident.severity.update"
                                    )
                                    .put(
                                        "commandId",
                                        commandId
                                    )
                                    .put(
                                        "incidentId",
                                        incidentId
                                    )
                                    .put(
                                        "severity",
                                        severity.name
                                    )

                            webSocket.send(
                                payload.toString()
                            )
                        }

                        override fun onMessage(
                            webSocket:
                                WebSocket,
                            text:
                                String
                        ) {

                            val json =
                                runCatching {
                                    JSONObject(
                                        text
                                    )
                                }
                                    .getOrNull()
                                    ?: return

                            val type =
                                json.optString(
                                    "type"
                                )

                            if (
                                type !=
                                    "command.accepted" &&
                                type !=
                                    "command.rejected"
                            ) {
                                return
                            }

                            if (
                                json.optString(
                                    "command"
                                ) !=
                                "incident.severity.update"
                            ) {
                                return
                            }

                            if (
                                json.optString(
                                    "incidentId"
                                ) !=
                                incidentId
                            ) {
                                return
                            }

                            if (
                                json.optString(
                                    "commandId"
                                ) !=
                                commandId
                            ) {
                                return
                            }

                            val result =
                                if (
                                    type ==
                                    "command.accepted"
                                ) {
                                    IncidentCommandResult
                                        .ACCEPTED
                                } else {
                                    IncidentCommandResult
                                        .REJECTED
                                }

                            finish(
                                result
                            )

                            webSocket.close(
                                1000,
                                "Command complete"
                            )
                        }

                        override fun onFailure(
                            webSocket:
                                WebSocket,
                            throwable:
                                Throwable,
                            response:
                                Response?
                        ) {

                            if (
                                response?.code ==
                                401
                            ) {
                                accessToken
                                    ?.let(
                                        onSessionInvalidated
                                    )
                            }

                            finish(
                                IncidentCommandResult
                                    .TRANSPORT_FAILURE
                            )
                        }

                        override fun onClosed(
                            webSocket:
                                WebSocket,
                            code:
                                Int,
                            reason:
                                String
                        ) {
                            if (
                                !completed
                            ) {
                                finish(
                                    IncidentCommandResult
                                        .TRANSPORT_FAILURE
                                )
                            }
                        }
                    }

                socket =
                    client.newWebSocket(
                        requestBuilder.build(),
                        listener
                    )

                continuation
                    .invokeOnCancellation {
                        socket?.cancel()
                    }
            }

        } ?: IncidentCommandResult
            .TRANSPORT_FAILURE
    }

    suspend fun updateStatus(
        incidentId: String,
        status: String,
        commandId: String
    ): IncidentCommandResult {

        return withTimeoutOrNull(
            3000L
        ) {

            suspendCancellableCoroutine {
                continuation ->

                var completed =
                    false

                var socket:
                    WebSocket? =
                    null

                fun finish(
                    result:
                        IncidentCommandResult
                ) {
                    if (completed) {
                        return
                    }

                    completed =
                        true

                    if (
                        continuation.isActive
                    ) {
                        continuation.resume(
                            result
                        )
                    }
                }

                val requestBuilder =
                    Request
                        .Builder()
                        .url(url)

                val accessToken =
                    tokenProvider()
                        ?.takeIf {
                            it.isNotBlank()
                        }

                accessToken
                    ?.let {
                        token ->

                        requestBuilder.header(
                            "Authorization",
                            "Bearer $token"
                        )
                    }

                val listener =
                    object :
                        WebSocketListener() {

                        override fun onOpen(
                            webSocket:
                                WebSocket,
                            response:
                                Response
                        ) {

                            val payload =
                                JSONObject()
                                    .put(
                                        "type",
                                        "incident.status.update"
                                    )
                                    .put(
                                        "commandId",
                                        commandId
                                    )
                                    .put(
                                        "incidentId",
                                        incidentId
                                    )
                                    .put(
                                        "status",
                                        status.trim()
                                    )

                            webSocket.send(
                                payload.toString()
                            )
                        }

                        override fun onMessage(
                            webSocket:
                                WebSocket,
                            text:
                                String
                        ) {

                            val json =
                                runCatching {
                                    JSONObject(
                                        text
                                    )
                                }
                                    .getOrNull()
                                    ?: return

                            val type =
                                json.optString(
                                    "type"
                                )

                            if (
                                type !=
                                    "command.accepted" &&
                                type !=
                                    "command.rejected"
                            ) {
                                return
                            }

                            if (
                                json.optString(
                                    "command"
                                ) !=
                                "incident.status.update"
                            ) {
                                return
                            }

                            if (
                                json.optString(
                                    "incidentId"
                                ) !=
                                incidentId
                            ) {
                                return
                            }

                            if (
                                json.optString(
                                    "commandId"
                                ) !=
                                commandId
                            ) {
                                return
                            }

                            val result =
                                if (
                                    type ==
                                    "command.accepted"
                                ) {
                                    IncidentCommandResult
                                        .ACCEPTED
                                } else {
                                    IncidentCommandResult
                                        .REJECTED
                                }

                            finish(
                                result
                            )

                            webSocket.close(
                                1000,
                                "Command complete"
                            )
                        }

                        override fun onFailure(
                            webSocket:
                                WebSocket,
                            throwable:
                                Throwable,
                            response:
                                Response?
                        ) {

                            if (
                                response?.code ==
                                401
                            ) {
                                accessToken
                                    ?.let(
                                        onSessionInvalidated
                                    )
                            }

                            finish(
                                IncidentCommandResult
                                    .TRANSPORT_FAILURE
                            )
                        }

                        override fun onClosed(
                            webSocket:
                                WebSocket,
                            code:
                                Int,
                            reason:
                                String
                        ) {

                            if (
                                !completed
                            ) {
                                finish(
                                    IncidentCommandResult
                                        .TRANSPORT_FAILURE
                                )
                            }
                        }
                    }

                socket =
                    client.newWebSocket(
                        requestBuilder.build(),
                        listener
                    )

                continuation
                    .invokeOnCancellation {
                        socket?.cancel()
                    }
            }

        } ?: IncidentCommandResult
            .TRANSPORT_FAILURE
    }
}
