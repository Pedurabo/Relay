package com.signaldesk.relay.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGenerationCounterTest {

    @Test
    fun initialGenerationIsZero() {
        val counter = SessionGenerationCounter()

        assertEquals(0L, counter.current())
    }

    @Test
    fun everyPublicationAdvancesGeneration() {
        val counter = SessionGenerationCounter()

        val first = counter.advance()
        val second = counter.advance()
        val third = counter.advance()

        assertEquals(1L, first)
        assertEquals(2L, second)
        assertEquals(3L, third)

        assertTrue(first < second)
        assertTrue(second < third)
    }

    @Test
    fun identicalCredentialsCanBelongToDifferentGenerations() {
        val counter = SessionGenerationCounter()

        val firstSession = counter.advance()
        counter.advance()
        val secondSession = counter.advance()

        assertNotEquals(firstSession, secondSession)
        assertEquals(secondSession, counter.current())
    }
}