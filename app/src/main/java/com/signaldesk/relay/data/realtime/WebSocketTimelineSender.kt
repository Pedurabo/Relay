package com.signaldesk.relay.data.realtime

import android.util.Log
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import com.signaldesk.relay.model.TimelineEntry
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebSocketTimelineSender(
    private val url: String,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient =
        OkHttpClient()
) {

    suspend fun send(
        entry: TimelineEntry
    ): TimelineEntryAddedEvent =
        suspendCancellableCoroutine { continuation ->

            val finished =
                AtomicBoolean(false)

            var socket: WebSocket? = null

            fun succeed(
                event: TimelineEntryAddedEvent
            ) {
                if (
                    finished.compareAndSet(
                        false,
                        true
                    )
                ) {
                    Log.i(
                        TAG,
                        "ACK entryId=${event.entryId}"
                    )

                    continuation.resume(event)

                    socket?.close(
                        1000,
                        "Acknowledged"
                    )
                }
            }

            fun fail(
                error: Throwable
            ) {
                if (
                    finished.compareAndSet(
                        false,
                        true
                    )
                ) {
                    Log.e(
                        TAG,
                        "FAILED entryId=${entry.id}",
                        error
                    )

                    continuation
                        .resumeWithException(
                            error
                        )

                    socket?.cancel()
                }
            }

            val requestBuilder =
                Request.Builder()
                    .url(url)

            val token =
                tokenProvider()

            if (
                !token.isNullOrBlank()
            ) {

                requestBuilder.header(
                    "Authorization",
                    "Bearer " + token
                )
            }

            val request =
                requestBuilder.build()

            Log.i(
                TAG,
                "CONNECT entryId=${entry.id} url=$url"
            )

            val listener =
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {
                        Log.i(
                            TAG,
                            "OPEN entryId=${entry.id}"
                        )

                        val payload =
                            JSONObject()
                                .put(
                                    "type",
                                    "timeline.entry.create"
                                )
                                .put(
                                    "entryId",
                                    entry.id
                                )
                                .put(
                                    "incidentId",
                                    entry.incidentId
                                )
                                .put(
                                    "message",
                                    entry.message
                                )
                                .put(
                                    "author",
                                    entry.author
                                )
                                .toString()

                        val accepted =
                            webSocket.send(
                                payload
                            )

                        Log.i(
                            TAG,
                            "SEND entryId=${entry.id} accepted=$accepted"
                        )

                        if (!accepted) {
                            fail(
                                IOException(
                                    "WebSocket rejected send."
                                )
                            )
                        }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {
                        Log.i(
                            TAG,
                            "MESSAGE entryId=${entry.id} payload=$text"
                        )

                        runCatching {
                            val json =
                                JSONObject(text)

                            if (
                                json.optString(
                                    "type"
                                ) !=
                                "timeline.entry.added"
                            ) {
                                return
                            }

                            if (
                                json.optString(
                                    "entryId"
                                ) != entry.id
                            ) {
                                return
                            }

                            succeed(
                                TimelineEntryAddedEvent(
                                    eventId =
                                        json.getString(
                                            "eventId"
                                        ),
                                    incidentId =
                                        json.getString(
                                            "incidentId"
                                        ),
                                    occurredAt =
                                        json.getLong(
                                            "occurredAt"
                                        ),
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
                            )
                        }.onFailure {
                            fail(it)
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        fail(t)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {
                        if (!finished.get()) {
                            fail(
                                IOException(
                                    "Socket closed before acknowledgement."
                                )
                            )
                        }
                    }
                }

            socket =
                client.newWebSocket(
                    request,
                    listener
                )

            continuation
                .invokeOnCancellation {
                    socket?.cancel()
                }
        }

    companion object {
        private const val TAG =
            "RelayTimelineSender"
    }
}
