package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingStatusMonitorLifecycleTest {

    private data class Session(
        val owner: String?,
        val generation: Long
    )

    private data class ObservedIncident(
        val id: String,
        val status: String
    )

    private data class MonitorResult(
        val session: Session,
        val confirmed: Boolean
    )

    private suspend fun awaitTerminalResult(
        sessions: MutableStateFlow<Session>,
        incidents: MutableStateFlow<ObservedIncident>,
        originatingGeneration: Long
    ): MonitorResult {
        return combine(sessions, incidents) { session, incident ->
            MonitorResult(
                session = session,
                confirmed = canConfirmPendingStatus(
                    pendingOwner = "account-a",
                    pendingGeneration = originatingGeneration,
                    pendingIncidentId = "INC-SHARED",
                    requestedStatus = "RESOLVED",
                    activeOwner = session.owner,
                    activeGeneration = session.generation,
                    observedIncidentId = incident.id,
                    observedStatus = incident.status
                )
            )
        }.first { result ->
            result.session.generation != originatingGeneration ||
                result.session.owner != "account-a" ||
                result.confirmed
        }
    }

    @Test
    fun accountBTerminatesOldMonitorWithoutConfirming() = runBlocking {
        val sessions = MutableStateFlow(Session("account-a", 10L))
        val incidents = MutableStateFlow(
            ObservedIncident("INC-SHARED", "OPEN")
        )

        val monitor = async(start = CoroutineStart.UNDISPATCHED) {
            awaitTerminalResult(sessions, incidents, 10L)
        }

        assertFalse(monitor.isCompleted)

        sessions.value = Session("account-b", 11L)
        incidents.value = ObservedIncident("INC-SHARED", "RESOLVED")

        val result = withTimeout(3_000L) {
            monitor.await()
        }

        assertFalse(result.confirmed)
        assertEquals("account-b", result.session.owner)
        assertEquals(11L, result.session.generation)

        sessions.value = Session("account-a", 12L)

        assertTrue(monitor.isCompleted)
        assertFalse(result.confirmed)
    }

    @Test
    fun returningAccountADoesNotAuthorizeGenerationTen() = runBlocking {
        val sessions = MutableStateFlow(Session("account-a", 10L))
        val incidents = MutableStateFlow(
            ObservedIncident("INC-SHARED", "OPEN")
        )

        val monitor = async(start = CoroutineStart.UNDISPATCHED) {
            awaitTerminalResult(sessions, incidents, 10L)
        }

        sessions.value = Session("account-a", 12L)
        incidents.value = ObservedIncident("INC-SHARED", "RESOLVED")

        val result = withTimeout(3_000L) {
            monitor.await()
        }

        assertFalse(result.confirmed)
        assertEquals(12L, result.session.generation)
    }

    @Test
    fun originalAccountCanConfirmWithinSameGeneration() = runBlocking {
        val sessions = MutableStateFlow(Session("account-a", 10L))
        val incidents = MutableStateFlow(
            ObservedIncident("INC-SHARED", "OPEN")
        )

        val monitor = async(start = CoroutineStart.UNDISPATCHED) {
            awaitTerminalResult(sessions, incidents, 10L)
        }

        assertFalse(monitor.isCompleted)

        incidents.value = ObservedIncident("INC-SHARED", "RESOLVED")

        val result = withTimeout(3_000L) {
            monitor.await()
        }

        assertTrue(result.confirmed)
        assertEquals("account-a", result.session.owner)
        assertEquals(10L, result.session.generation)
    }
}