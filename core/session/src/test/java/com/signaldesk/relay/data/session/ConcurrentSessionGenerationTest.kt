package com.signaldesk.relay.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrentSessionGenerationTest {

    @Test
    fun concurrentTransitionsReceiveUniqueGenerations() {
        val counter = SessionGenerationCounter()

        val workers = 8
        val transitionsPerWorker = 100

        val executor = Executors.newFixedThreadPool(workers)
        val start = CountDownLatch(1)

        try {
            val futures = (0 until workers).map {
                executor.submit(
                    Callable {
                        start.await()

                        (0 until transitionsPerWorker).map {
                            counter.advance()
                        }
                    }
                )
            }

            start.countDown()

            val generations = futures.flatMap {
                it.get(15, TimeUnit.SECONDS)
            }

            assertEquals(800, generations.size)
            assertEquals(800, generations.toSet().size)
            assertEquals(800L, counter.current())
            assertEquals(1L, generations.minOrNull())
            assertEquals(800L, generations.maxOrNull())

        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun supersededGenerationIsRejected() {
        val counter = SessionGenerationCounter()

        val oldGeneration = counter.advance()

        counter.advance()

        val currentGeneration = counter.advance()

        assertFalse(
            oldGeneration == counter.current()
        )

        assertTrue(
            currentGeneration == counter.current()
        )
    }

    @Test
    fun concurrentReadersObserveMonotonicGenerations() {
        val counter = SessionGenerationCounter()

        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)

        try {
            val writer = executor.submit(
                Callable {
                    start.await()

                    repeat(10_000) {
                        counter.advance()
                    }
                }
            )

            val reader = executor.submit(
                Callable {
                    start.await()

                    var previous = 0L

                    repeat(10_000) {
                        val current = counter.current()

                        assertTrue(current >= previous)

                        previous = current
                    }
                }
            )

            start.countDown()

            writer.get(15, TimeUnit.SECONDS)
            reader.get(15, TimeUnit.SECONDS)

            assertEquals(10_000L, counter.current())

        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}