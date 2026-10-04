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

internal fun incidentCommandInvalidatedToken(
    httpStatusCode: Int?,
    accessToken: String?
): String? {

    if (
        httpStatusCode !=
        401
    ) {
        return null
    }

    return accessToken
        ?.takeIf {
            it.isNotBlank()
        }
}

internal fun correlateIncidentCommandAcknowledgement(
    type: String,
    command: String,
    incidentId: String,
    commandId: String,
    expectedCommand: String,
    expectedIncidentId: String,
    expectedCommandId: String
): IncidentCommandResult? {

    if (
        type !=
            "command.accepted" &&
        type !=
            "command.rejected"
    ) {
        return null
    }

    if (
        command !=
        expectedCommand
    ) {
        return null
    }

    if (
        incidentId !=
        expectedIncidentId
    ) {
        return null
    }

    if (
        commandId !=
        expectedCommandId
    ) {
        return null
    }

    return if (
        type ==
        "command.accepted"
    ) {
        IncidentCommandResult
            .ACCEPTED
    } else {
        IncidentCommandResult
            .REJECTED
    }
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

                            val result =
                                correlateIncidentCommandAcknowledgement(
                                    type =
                                        json.optString(
                                            "type"
                                        ),
                                    command =
                                        json.optString(
                                            "command"
                                        ),
                                    incidentId =
                                        json.optString(
                                            "incidentId"
                                        ),
                                    commandId =
                                        json.optString(
                                            "commandId"
                                        ),
                                    expectedCommand =
                                        "incident.severity.update",
                                    expectedIncidentId =
                                        incidentId,
                                    expectedCommandId =
                                        commandId
                                )
                                    ?: return

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

                            incidentCommandInvalidatedToken(
                                httpStatusCode =
                                    response?.code,
                                accessToken =
                                    accessToken
                            )
                                ?.let(
                                    onSessionInvalidated
                                )

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

    suspend fun createIncident(
        incidentId: String,
        title: String,
        status: String,
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
                                        "incident.create"
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
                                        "title",
                                        title.trim()
                                    )
                                    .put(
                                        "status",
                                        status.trim()
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

                            val result =
                                correlateIncidentCommandAcknowledgement(
                                    type =
                                        json.optString(
                                            "type"
                                        ),
                                    command =
                                        json.optString(
                                            "command"
                                        ),
                                    incidentId =
                                        json.optString(
                                            "incidentId"
                                        ),
                                    commandId =
                                        json.optString(
                                            "commandId"
                                        ),
                                    expectedCommand =
                                        "incident.create",
                                    expectedIncidentId =
                                        incidentId,
                                    expectedCommandId =
                                        commandId
                                )
                                    ?: return

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

                            incidentCommandInvalidatedToken(
                                httpStatusCode =
                                    response?.code,
                                accessToken =
                                    accessToken
                            )
                                ?.let(
                                    onSessionInvalidated
                                )

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

                            val result =
                                correlateIncidentCommandAcknowledgement(
                                    type =
                                        json.optString(
                                            "type"
                                        ),
                                    command =
                                        json.optString(
                                            "command"
                                        ),
                                    incidentId =
                                        json.optString(
                                            "incidentId"
                                        ),
                                    commandId =
                                        json.optString(
                                            "commandId"
                                        ),
                                    expectedCommand =
                                        "incident.status.update",
                                    expectedIncidentId =
                                        incidentId,
                                    expectedCommandId =
                                        commandId
                                )
                                    ?: return

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

                            incidentCommandInvalidatedToken(
                                httpStatusCode =
                                    response?.code,
                                accessToken =
                                    accessToken
                            )
                                ?.let(
                                    onSessionInvalidated
                                )

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
