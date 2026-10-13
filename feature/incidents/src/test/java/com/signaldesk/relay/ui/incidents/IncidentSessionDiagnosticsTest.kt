package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.session.SessionIdentity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentSessionDiagnosticsTest {

    private val identity = SessionIdentity(
        ownerPrincipal = "account-a",
        generation = 60L
    )

    @Test
    fun statusFailureReportsOnlyOperationAndExceptionType() = runBlocking {
        val reports = mutableListOf<Pair<String, String>>()

        collectIncidentSessionLifecycle(
            identities = flowOf(identity),
            onReset = {},
            restoreStatus = {
                throw IllegalStateException(
                    "private account-a incident information"
                )
            },
            restoreSeverity = {},
            onRestoreFailure = { operation, type ->
                reports.add(operation to type)
            }
        )

        assertEquals(
            listOf("status" to "IllegalStateException"),
            reports
        )
    }

    @Test
    fun severityFailureReportsOnlyOperationAndExceptionType() = runBlocking {
        val reports = mutableListOf<Pair<String, String>>()

        collectIncidentSessionLifecycle(
            identities = flowOf(identity),
            onReset = {},
            restoreStatus = {},
            restoreSeverity = {
                throw IllegalArgumentException(
                    "private command content"
                )
            },
            onRestoreFailure = { operation, type ->
                reports.add(operation to type)
            }
        )

        assertEquals(
            listOf("severity" to "IllegalArgumentException"),
            reports
        )
    }
}