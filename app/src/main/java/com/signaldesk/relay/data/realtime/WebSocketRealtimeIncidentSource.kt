package com.signaldesk.relay.data.realtime

import android.util.Log
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
internal fun <T : Any> shouldApplyRealtimeSocketTerminalState(
    activeSocket: T?,
    callbackSocket: T
): Boolean =
    activeSocket == null ||
        activeSocket === callbackSocket

internal fun <T : Any> shouldProcessRealtimeSocketMessage(
    activeSocket: T?,
    callbackSocket: T
): Boolean =
    activeSocket === callbackSocket

internal fun shouldAcceptRealtimeSocketOpen(
    activeAttemptId: Long,
    callbackAttemptId: Long
): Boolean =
    activeAttemptId != 0L &&
        activeAttemptId ==
        callbackAttemptId

class WebSocketRealtimeIncidentSource(
    private val url: String,
    private val tokenProvider: () -> String? = { null },
    private val onSessionInvalidated: (String) -> Unit = {},
    private val client: OkHttpClient =
        OkHttpClient()
) : RealtimeIncidentSource {

    private val _connectionState =
        MutableStateFlow<RealtimeConnectionState>(
            RealtimeConnectionState.Disconnected
        )

    override val connectionState:
        StateFlow<RealtimeConnectionState> =
        _connectionState

    @Volatile
    private var activeSocket:
        WebSocket? = null

    private val attemptCounter =
        AtomicLong(0L)

    @Volatile
    private var activeAttemptId =
        0L

    override val events: Flow<IncidentEvent> =
        callbackFlow {

            val attemptId =
                attemptCounter
                    .incrementAndGet()

            activeAttemptId =
                attemptId

            _connectionState.value =
                RealtimeConnectionState.Connecting

            val requestBuilder =
                Request.Builder()
                    .url(url)

            val accessToken =
                tokenProvider()
                    ?.takeIf {
                        it.isNotBlank()
                    }

            accessToken
                ?.let { token ->
                    requestBuilder.header(
                        "Authorization",
                        "Bearer $token"
                    )
                }

            val request =
                requestBuilder.build()

            val listener =
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {
                        if (
                            !shouldAcceptRealtimeSocketOpen(
                                activeAttemptId =
                                    activeAttemptId,
                                callbackAttemptId =
                                    attemptId
                            )
                        ) {
                            webSocket.cancel()

                            return
                        }

                        activeSocket =
                            webSocket

                        _connectionState.value =
                            RealtimeConnectionState.Connected
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {
                        if (
                            !shouldProcessRealtimeSocketMessage(
                                activeSocket =
                                    activeSocket,
                                callbackSocket =
                                    webSocket
                            )
                        ) {
                            return
                        }
                        val json =
                            runCatching {
                                JSONObject(text)
                            }.getOrNull()
                                ?: return

                        if (
                            json.optString("type") ==
                            "session.invalidated"
                        ) {

                            if (
                                !shouldApplyRealtimeSocketTerminalState(
                                    activeSocket =
                                        activeSocket,
                                    callbackSocket =
                                        webSocket
                                )
                            ) {
                                return
                            }
                            _connectionState.value =
                                RealtimeConnectionState.Disconnected

                            accessToken
                                ?.let(
                                    onSessionInvalidated
                                )

                            webSocket.close(
                                4001,
                                "Session invalidated"
                            )

                            close()

                            return
                        }

                        val event =
                            parseEvent(json)
                                ?: return

                        trySend(event)
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        webSocket.close(
                            code,
                            reason
                        )
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        if (
                            shouldApplyRealtimeSocketTerminalState(
                                activeSocket =
                                    activeSocket,
                                callbackSocket =
                                    webSocket
                            )
                        ) {

                            if (
                                activeSocket ===
                                webSocket
                            ) {
                                activeSocket = null
                            }

                            _connectionState.value =
                                RealtimeConnectionState.Disconnected
                        }

                        close()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        val shouldApplyTerminalState =
                            shouldApplyRealtimeSocketTerminalState(
                                activeSocket =
                                    activeSocket,
                                callbackSocket =
                                    webSocket
                            )

                        if (
                            shouldApplyTerminalState
                        ) {

                            if (
                                activeSocket ===
                                webSocket
                            ) {
                                activeSocket = null
                            }

                            _connectionState.value =
                                RealtimeConnectionState.Disconnected
                        }

                        if (
                            shouldApplyTerminalState &&
                            response?.code ==
                            401
                        ) {
                            accessToken
                                ?.let(
                                    onSessionInvalidated
                                )
                        }

                        close(t)
                    }
                }

            val socket =
                client.newWebSocket(
                    request,
                    listener
                )

            awaitClose {
                if (
                    activeAttemptId ==
                    attemptId
                ) {
                    activeAttemptId =
                        0L
                }

                if (
                    activeSocket === socket
                ) {
                    activeSocket = null
                }

                socket.cancel()
            }
        }

    override fun requestReplay(
        incidentId: String,
        fromSequence: Long,
        throughSequence: Long
    ): Boolean {

        if (
            fromSequence >
            throughSequence
        ) {
            return false
        }

        val socket =
            activeSocket
                ?: return false

        val payload =
            JSONObject()
                .put(
                    "type",
                    "replay.request"
                )
                .put(
                    "incidentId",
                    incidentId
                )
                .put(
                    "fromSequence",
                    fromSequence
                )
                .put(
                    "throughSequence",
                    throughSequence
                )
                .toString()

        val accepted =
            socket.send(
                payload
            )

        Log.i(
            TAG,
            "REPLAY_REQUEST|" +
                incidentId +
                "|" +
                fromSequence +
                "|" +
                throughSequence +
                "|accepted=" +
                accepted
        )

        return accepted
    }

    private fun parseEvent(
        json: JSONObject
    ): IncidentEvent? {
        return runCatching {

            val type =
                json.getString("type")

            val eventId =
                json.getString("eventId")

            val incidentId =
                json.getString("incidentId")

            val occurredAt =
                json.getLong("occurredAt")

            when (type) {

                "incident.created" -> {
                    IncidentCreatedEvent(
                        eventId = eventId,
                        incidentId = incidentId,
                        occurredAt = occurredAt,
                        title =
                            json.getString(
                                "title"
                            ),
                        status =
                            json.getString(
                                "status"
                            ),
                        severity =
                            json.optString(
                                "severity",
                                "MEDIUM"
                            ),
                        sequence =
                            json.optLong(
                                "sequence",
                                0L
                            )
                    )
                }

                "incident.updated" -> {
                    IncidentUpdatedEvent(
                        eventId = eventId,
                        incidentId = incidentId,
                        occurredAt = occurredAt,
                        title =
                            json.optString(
                                "title",
                                ""
                            ).takeIf {
                                it.isNotBlank()
                            },
                        status =
                            json.optString(
                                "status",
                                ""
                            ).takeIf {
                                it.isNotBlank()
                            },
                        severity =
                            json.optString(
                                "severity",
                                ""
                            ).takeIf {
                                it.isNotBlank()
                            },
                        sequence =
                            json.optLong(
                                "sequence",
                                0L
                            )
                    )
                }

                "timeline.entry.added" -> {
                    TimelineEntryAddedEvent(
                        eventId = eventId,
                        incidentId = incidentId,
                        occurredAt = occurredAt,
                        entryId =
                            json.getString(
                                "entryId"
                            ),
                        message =
                            json.getString(
                                "message"
                            ),
                        author =
                            json.getString(
                                "author"
                            )
                    )
                }

                else -> null
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG =
            "RelayRealtime"
    }
}
