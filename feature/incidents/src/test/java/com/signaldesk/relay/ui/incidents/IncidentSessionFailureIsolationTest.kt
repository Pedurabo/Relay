package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.session.SessionIdentity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentSessionFailureIsolationTest {

    private val accountA = SessionIdentity(
        ownerPrincipal = "account-a",
        generation = 60L
    )

    private val accountB = SessionIdentity(
        ownerPrincipal = "account-b",
        generation = 61L
    )

    @Test
    fun failedStatusRestorationDoesNotCancelSeverity() = runBlocking {
        var severityRestorations = 0

        collectIncidentSessionLifecycle(
            identities = flowOf(accountA),
            onReset = {},
            restoreStatus = {
                throw IllegalStateException("Status store unavailable")
            },
            restoreSeverity = {
                severityRestorations++
            }
        )

        assertEquals(1, severityRestorations)
    }

    @Test
    fun failedSeverityRestorationDoesNotCancelStatus() = runBlocking {
        var statusRestorations = 0

        collectIncidentSessionLifecycle(
            identities = flowOf(accountA),
            onReset = {},
            restoreStatus = {
                statusRestorations++
            },
            restoreSeverity = {
                throw IllegalStateException("Severity store unavailable")
            }
        )

        assertEquals(1, statusRestorations)
    }

    @Test
    fun restorationFailureDoesNotPreventNextIdentity() = runBlocking {
        var resets = 0
        val severityOwners = mutableListOf<String?>()
        val firstRestorationCompleted =
            kotlinx.coroutines.CompletableDeferred<Unit>()

        val controlledIdentities =
            kotlinx.coroutines.flow.flow {
                emit(accountA)

                // Wait until account A's severity restoration has
                // actually executed before publishing account B.
                firstRestorationCompleted.await()

                emit(accountB)
            }

        collectIncidentSessionLifecycle(
            identities = controlledIdentities,
            onReset = {
                resets++
            },
            restoreStatus = {
                throw IllegalStateException("Status restoration failed")
            },
            restoreSeverity = {
                severityOwners.add(it.ownerPrincipal)

                if (it == accountA) {
                    firstRestorationCompleted.complete(Unit)
                }
            }
        )

        assertEquals(1, resets)
        assertEquals(
            listOf("account-a", "account-b"),
            severityOwners
        )
    }
}