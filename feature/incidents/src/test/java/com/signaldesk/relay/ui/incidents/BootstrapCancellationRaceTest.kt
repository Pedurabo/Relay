package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapCancellationRaceTest {

    @Test
    fun supersededBootstrapDoesNotApply() = runBlocking {
        val sessions = MutableStateFlow(1L)

        val firstStarted = CompletableDeferred<Unit>()
        val firstCancelled = CompletableDeferred<Unit>()
        val secondApplied = CompletableDeferred<Unit>()

        val releaseFirst = CompletableDeferred<Unit>()
        val applied = mutableListOf<Long>()

        val collector = launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            sessions.collectLatest { generation ->
                if (generation == 1L) {
                    firstStarted.complete(Unit)

                    try {
                        releaseFirst.await()
                        applied.add(generation)
                    } finally {
                        firstCancelled.complete(Unit)
                    }
                } else {
                    applied.add(generation)
                    secondApplied.complete(Unit)
                }
            }
        }

        try {
            firstStarted.await()

            sessions.value = 2L

            firstCancelled.await()
            secondApplied.await()

            releaseFirst.complete(Unit)
            yield()

            assertEquals(listOf(2L), applied)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun unchangedGenerationDoesNotRestartCollection() =
        runBlocking {

            val sessions = MutableStateFlow(1L)
            val firstStarted = CompletableDeferred<Unit>()

            var starts = 0

            val collector = launch(
                start = CoroutineStart.UNDISPATCHED
            ) {
                sessions.collectLatest {
                    starts += 1
                    firstStarted.complete(Unit)

                    CompletableDeferred<Unit>().await()
                }
            }

            try {
                firstStarted.await()

                sessions.value = 1L

                yield()

                assertEquals(1, starts)
            } finally {
                collector.cancelAndJoin()
            }
        }

    @Test
    fun cancelledBootstrapCannotReachApplyStage() =
        runBlocking {

            val sessions = MutableStateFlow(1L)

            val loading = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val replacement = CompletableDeferred<Unit>()

            var staleApplied = false
            var replacementApplied = false

            val collector = launch(
                start = CoroutineStart.UNDISPATCHED
            ) {
                sessions.collectLatest { generation ->

                    if (generation == 1L) {

                        try {
                            loading.complete(Unit)

                            release.await()

                            staleApplied = true
                        } finally {
                            cancelled.complete(Unit)
                        }

                    } else {
                        replacementApplied = true
                        replacement.complete(Unit)
                    }
                }
            }

            try {
                loading.await()

                sessions.value = 2L

                cancelled.await()
                replacement.await()

                release.complete(Unit)
                yield()

                assertFalse(staleApplied)
                assertTrue(replacementApplied)
            } finally {
                collector.cancelAndJoin()
            }
        }
}