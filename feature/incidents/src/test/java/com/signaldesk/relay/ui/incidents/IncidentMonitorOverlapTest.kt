package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorOverlapTest {

    @Test
    fun oldCommandFinishingLateCannotReleaseNewCommand() = runBlocking {
        val older = IncidentMonitorOwnership(
            "account-a", 10L, "command-1"
        )
        val newer = IncidentMonitorOwnership(
            "account-a", 10L, "command-2"
        )

        var active: IncidentMonitorOwnership? = older
        var optimisticStatus: String? = "INVESTIGATING"
        var inProgress = true

        val started = CompletableDeferred<Unit>()
        val resumeOldMonitor = CompletableDeferred<Unit>()

        val oldMonitor = launch {
            started.complete(Unit)
            resumeOldMonitor.await()

            if (older.canUpdatePresentation(active)) {
                optimisticStatus = null
                inProgress = false
                active = older.releaseIfCurrent(active)
            }
        }

        withTimeout(5000L) {
            started.await()

            active = newer
            optimisticStatus = "RESOLVED"
            inProgress = true

            resumeOldMonitor.complete(Unit)
            oldMonitor.join()
        }

        assertEquals(newer, active)
        assertEquals("RESOLVED", optimisticStatus)
        assertTrue(inProgress)
    }

    @Test
    fun returningAccountRejectsPreviousGenerationCompletion() = runBlocking {
        val previous = IncidentMonitorOwnership(
            "account-a", 20L, "same-command"
        )
        val returned = IncidentMonitorOwnership(
            "account-a", 22L, "same-command"
        )

        var active: IncidentMonitorOwnership? = previous
        var optimisticStatus: String? = "OPEN"

        val started = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()

        val monitor = launch {
            started.complete(Unit)
            resume.await()

            if (previous.canUpdatePresentation(active)) {
                optimisticStatus = null
                active = previous.releaseIfCurrent(active)
            }
        }

        withTimeout(5000L) {
            started.await()
            active = returned
            optimisticStatus = "ACKNOWLEDGED"
            resume.complete(Unit)
            monitor.join()
        }

        assertEquals(returned, active)
        assertEquals("ACKNOWLEDGED", optimisticStatus)
        assertFalse(previous.canUpdatePresentation(active))
    }

    @Test
    fun statusCompletionCannotReleaseIndependentSeverityOwner() = runBlocking {
        val status = IncidentMonitorOwnership(
            "account-a", 30L, "status-command"
        )
        val severity = IncidentMonitorOwnership(
            "account-a", 30L, "severity-command"
        )

        var activeStatus: IncidentMonitorOwnership? = status
        var activeSeverity: IncidentMonitorOwnership? = severity

        val resumeStatus = CompletableDeferred<Unit>()
        val statusStarted = CompletableDeferred<Unit>()

        val statusMonitor = launch {
            statusStarted.complete(Unit)
            resumeStatus.await()

            if (status.canUpdatePresentation(activeStatus)) {
                activeStatus = status.releaseIfCurrent(activeStatus)
            }
        }

        withTimeout(5000L) {
            statusStarted.await()
            resumeStatus.complete(Unit)
            statusMonitor.join()
        }

        assertNull(activeStatus)
        assertEquals(severity, activeSeverity)
        assertTrue(severity.canUpdatePresentation(activeSeverity))
    }

    @Test
    fun lateSaveFailureCannotClearRestoredDifferentCommand() = runBlocking {
        val saving = IncidentMonitorOwnership(
            "account-a", 40L, "saving-command"
        )
        val restored = IncidentMonitorOwnership(
            "account-a", 40L, "restored-command"
        )

        var active: IncidentMonitorOwnership? = saving
        var optimisticSeverity = "HIGH"
        var errorMessage: String? = null

        val saveStarted = CompletableDeferred<Unit>()
        val failSave = CompletableDeferred<Unit>()

        val saveJob = launch {
            saveStarted.complete(Unit)
            failSave.await()

            if (saving.canUpdatePresentation(active)) {
                optimisticSeverity = ""
                errorMessage = "Unable to save severity change."
                active = saving.releaseIfCurrent(active)
            }
        }

        withTimeout(5000L) {
            saveStarted.await()

            active = restored
            optimisticSeverity = "CRITICAL"

            failSave.complete(Unit)
            saveJob.join()
        }

        assertEquals(restored, active)
        assertEquals("CRITICAL", optimisticSeverity)
        assertNull(errorMessage)
    }
}