package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.session.SessionIdentity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentSessionLifecycleTest {

    @Test
    fun replacementResetsBeforeRestoration() = runBlocking {
        val identities = MutableStateFlow(
            SessionIdentity("account-a", 10L)
        )

        val events = Channel<String>(Channel.UNLIMITED)

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            collectIncidentSessionLifecycle(
                identities = identities,
                onReset = {
                    events.trySend("reset")
                },
                restoreStatus = { identity ->
                    events.send("status-${identity.generation}")
                    awaitCancellation()
                },
                restoreSeverity = { identity ->
                    events.send("severity-${identity.generation}")
                    awaitCancellation()
                }
            )
        }

        try {
            withTimeout(5000L) {
                val initial = setOf(
                    events.receive(),
                    events.receive()
                )

                assertEquals(
                    setOf("status-10", "severity-10"),
                    initial
                )

                identities.value = SessionIdentity("account-b", 11L)

                assertEquals("reset", events.receive())

                val replacement = setOf(
                    events.receive(),
                    events.receive()
                )

                assertEquals(
                    setOf("status-11", "severity-11"),
                    replacement
                )
            }
        } finally {
            job.cancelAndJoin()
            events.close()
        }
    }

    @Test
    fun returningAccountUsesNewGeneration() = runBlocking {
        val identities = MutableStateFlow(
            SessionIdentity("account-a", 20L)
        )

        val events = Channel<String>(Channel.UNLIMITED)
        val cancellations = Channel<String>(Channel.UNLIMITED)

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            collectIncidentSessionLifecycle(
                identities = identities,
                onReset = {
                    events.trySend("reset")
                },
                restoreStatus = { identity ->
                    events.send("status-${identity.generation}")
                    try {
                        awaitCancellation()
                    } finally {
                        cancellations.trySend(
                            "status-${identity.generation}"
                        )
                    }
                },
                restoreSeverity = { identity ->
                    events.send("severity-${identity.generation}")
                    try {
                        awaitCancellation()
                    } finally {
                        cancellations.trySend(
                            "severity-${identity.generation}"
                        )
                    }
                }
            )
        }

        try {
            withTimeout(5000L) {
                assertEquals(
                    setOf("status-20", "severity-20"),
                    setOf(events.receive(), events.receive())
                )

                identities.value = SessionIdentity("account-b", 21L)

                assertEquals(
                    setOf("status-20", "severity-20"),
                    setOf(cancellations.receive(), cancellations.receive())
                )

                assertEquals("reset", events.receive())

                assertEquals(
                    setOf("status-21", "severity-21"),
                    setOf(events.receive(), events.receive())
                )

                identities.value = SessionIdentity("account-a", 22L)

                assertEquals(
                    setOf("status-21", "severity-21"),
                    setOf(cancellations.receive(), cancellations.receive())
                )

                assertEquals("reset", events.receive())

                assertEquals(
                    setOf("status-22", "severity-22"),
                    setOf(events.receive(), events.receive())
                )
            }
        } finally {
            job.cancelAndJoin()
            events.close()
            cancellations.close()
        }
    }

    @Test
    fun signOutCancelsRestorationAndResets() = runBlocking {
        val identities = MutableStateFlow(
            SessionIdentity("account-a", 30L)
        )

        val events = Channel<String>(Channel.UNLIMITED)
        val cancellations = Channel<String>(Channel.UNLIMITED)

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            collectIncidentSessionLifecycle(
                identities = identities,
                onReset = {
                    events.trySend("reset")
                },
                restoreStatus = { identity ->
                    events.send("status-${identity.generation}")
                    try {
                        awaitCancellation()
                    } finally {
                        cancellations.trySend("status-cancelled")
                    }
                },
                restoreSeverity = { identity ->
                    events.send("severity-${identity.generation}")
                    try {
                        awaitCancellation()
                    } finally {
                        cancellations.trySend("severity-cancelled")
                    }
                }
            )
        }

        try {
            withTimeout(5000L) {
                assertEquals(
                    setOf("status-30", "severity-30"),
                    setOf(events.receive(), events.receive())
                )

                identities.value = SessionIdentity(null, 31L)

                assertEquals(
                    setOf("status-cancelled", "severity-cancelled"),
                    setOf(cancellations.receive(), cancellations.receive())
                )

                assertEquals("reset", events.receive())
                assertEquals(
                    SessionIdentity(null, 31L),
                    identities.value
                )
            }
        } finally {
            job.cancelAndJoin()
            events.close()
            cancellations.close()
        }
    }
}