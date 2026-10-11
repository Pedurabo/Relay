package com.signaldesk.relay.ui.incidents

import org.junit.Test
import kotlin.test.assertEquals

class IncidentsListContentStateTest {

    @Test
    fun notLoaded_returnsLoading_evenWhenIncidentCountIsZero() {
        assertEquals(
            IncidentsListContentState.Loading,
            incidentsListContentState(
                incidentsLoaded = false,
                incidentCount = 0
            )
        )
    }

    @Test
    fun loadedWithZeroIncidents_returnsEmpty() {
        assertEquals(
            IncidentsListContentState.Empty,
            incidentsListContentState(
                incidentsLoaded = true,
                incidentCount = 0
            )
        )
    }

    @Test
    fun loadedWithIncidents_returnsContent() {
        assertEquals(
            IncidentsListContentState.Content,
            incidentsListContentState(
                incidentsLoaded = true,
                incidentCount = 1
            )
        )
    }

    @Test
    fun notLoaded_returnsLoading_evenIfCachedCountIsNonZero() {
        assertEquals(
            IncidentsListContentState.Loading,
            incidentsListContentState(
                incidentsLoaded = false,
                incidentCount = 3
            )
        )
    }
}