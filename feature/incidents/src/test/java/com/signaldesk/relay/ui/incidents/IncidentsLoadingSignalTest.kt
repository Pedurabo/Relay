package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncidentsLoadingSignalTest {

    @Test
    fun beforeFirstEmission_loadedStateRemainsFalse() {
        val loadedState =
            MutableStateFlow(false)

        flowOf(emptyList<String>())
            .markIncidentsLoaded(loadedState)

        assertFalse(
            loadedState.value
        )
    }

    @Test
    fun firstEmission_marksIncidentsLoadedTrue() =
        runBlocking {
            val loadedState =
                MutableStateFlow(false)

            flowOf(emptyList<String>())
                .markIncidentsLoaded(loadedState)
                .first()

            assertTrue(
                loadedState.value
            )
        }

    @Test
    fun nonEmptyFirstEmission_alsoMarksIncidentsLoadedTrue() =
        runBlocking {
            val loadedState =
                MutableStateFlow(false)

            flowOf(listOf("incident"))
                .markIncidentsLoaded(loadedState)
                .first()

            assertTrue(
                loadedState.value
            )
        }
}