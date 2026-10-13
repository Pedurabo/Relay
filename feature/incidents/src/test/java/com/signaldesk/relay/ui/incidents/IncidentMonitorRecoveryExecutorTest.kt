package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorRecoveryExecutorTest {

    @Test
    fun normalCompletionDoesNotReloadOrRetry() = runBlocking {
        var attempts = 0
        var reloads = 0
        var stopped = false

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = { attempts++ },
            reload = {
                reloads++
                "command-a"
            },
            mayRetry = { true },
            acquireRetryPermit = { true },
            onStopped = { stopped = true }
        )

        assertEquals(1, attempts)
        assertEquals(0, reloads)
        assertFalse(stopped)
    }

    @Test
    fun unexpectedFailureRetriesOnceAfterReload() = runBlocking {
        val events = mutableListOf<String>()
        var attempts = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++
                events.add("monitor-$attempts")
                if (attempts == 1) {
                    throw IllegalStateException("observation failed")
                }
            },
            reload = {
                events.add("reload")
                "command-a"
            },
            mayRetry = {
                events.add("authorize")
                true
            },
            acquireRetryPermit = {
                events.add("permit")
                true
            },
            onStopped = { events.add("stopped") }
        )

        assertEquals(
            listOf(
                "monitor-1",
                "reload",
                "authorize",
                "permit",
                "monitor-2"
            ),
            events
        )
    }

    @Test
    fun repeatedFailureStopsAfterSecondAttempt() = runBlocking {
        var attempts = 0
        var stopped = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++
                throw IllegalStateException("persistent failure")
            },
            reload = { "command-a" },
            mayRetry = { true },
            acquireRetryPermit = { true },
            onStopped = { stopped++ }
        )

        assertEquals(2, attempts)
        assertEquals(1, stopped)
    }

    @Test
    fun cancellationDoesNotReloadOrRetry() = runBlocking {
        var reloads = 0
        var stopped = false
        var cancelled = false

        try {
            executeMonitorWithOneRecovery(
                initial = "command-a",
                monitor = {
                    throw CancellationException("session reset")
                },
                reload = {
                    reloads++
                    "command-a"
                },
                mayRetry = { true },
                acquireRetryPermit = { true },
                onStopped = { stopped = true }
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(0, reloads)
        assertFalse(stopped)
    }

    @Test
    fun cancellationDuringReloadPropagates() = runBlocking {
        var attempts = 0
        var stopped = false
        var cancelled = false

        try {
            executeMonitorWithOneRecovery(
                initial = "command-a",
                monitor = {
                    attempts++
                    throw IllegalStateException("monitor failed")
                },
                reload = {
                    throw CancellationException("account changed")
                },
                mayRetry = { true },
                acquireRetryPermit = { true },
                onStopped = { stopped = true }
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(1, attempts)
        assertFalse(stopped)
    }

    @Test
    fun changedCommandCannotConsumeRetryPermit() = runBlocking {
        var attempts = 0
        var permits = 0
        var stopped = false

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++
                throw IllegalStateException("failed")
            },
            reload = { "command-b" },
            mayRetry = { it == "command-a" },
            acquireRetryPermit = {
                permits++
                true
            },
            onStopped = { stopped = true }
        )

        assertEquals(1, attempts)
        assertEquals(0, permits)
        assertTrue(stopped)
    }

    @Test
    fun exhaustedBudgetPreventsSecondAttempt() = runBlocking {
        var attempts = 0
        var stopped = false

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++
                throw IllegalStateException("failed")
            },
            reload = { "command-a" },
            mayRetry = { true },
            acquireRetryPermit = { false },
            onStopped = { stopped = true }
        )

        assertEquals(1, attempts)
        assertTrue(stopped)
    }

    @Test
    fun reloadFailureStopsWithoutRetry() = runBlocking {
        var attempts = 0
        var stopped = false

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++
                throw IllegalStateException("monitor failed")
            },
            reload = {
                throw IllegalStateException("storage unavailable")
            },
            mayRetry = { true },
            acquireRetryPermit = { true },
            onStopped = { stopped = true }
        )

        assertEquals(1, attempts)
        assertTrue(stopped)
    }
}