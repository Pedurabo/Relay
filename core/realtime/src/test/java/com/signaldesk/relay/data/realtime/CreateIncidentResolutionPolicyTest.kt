package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateIncidentResolutionPolicyTest {

    @Test
    fun missingAuthoritativeIncident_keepsCreatePending() {

        assertEquals(
            CreateIncidentResolutionDecision
                .KEEP_PENDING,
            decideCreateIncidentResolution(
                authoritativeTitle =
                    null,
                authoritativeStatus =
                    null,
                authoritativeSeverity =
                    null,
                requestedTitle =
                    "Payments unavailable",
                requestedStatus =
                    "Investigating",
                requestedSeverity =
                    "HIGH"
            )
        )
    }

    @Test
    fun matchingAuthoritativeIncident_marksCreateConverged() {

        assertEquals(
            CreateIncidentResolutionDecision
                .CONVERGED,
            decideCreateIncidentResolution(
                authoritativeTitle =
                    "Payments unavailable",
                authoritativeStatus =
                    "Investigating",
                authoritativeSeverity =
                    "HIGH",
                requestedTitle =
                    "Payments unavailable",
                requestedStatus =
                    "Investigating",
                requestedSeverity =
                    "HIGH"
            )
        )
    }

    @Test
    fun severityNormalization_stillMarksMatchingCreateConverged() {

        assertEquals(
            CreateIncidentResolutionDecision
                .CONVERGED,
            decideCreateIncidentResolution(
                authoritativeTitle =
                    "Payments unavailable",
                authoritativeStatus =
                    "Investigating",
                authoritativeSeverity =
                    "high",
                requestedTitle =
                    "Payments unavailable",
                requestedStatus =
                    "Investigating",
                requestedSeverity =
                    "HIGH"
            )
        )
    }

    @Test
    fun differentTitleUnderSameIncidentId_marksCreateSuperseded() {

        assertEquals(
            CreateIncidentResolutionDecision
                .SUPERSEDED,
            decideCreateIncidentResolution(
                authoritativeTitle =
                    "Different incident",
                authoritativeStatus =
                    "Investigating",
                authoritativeSeverity =
                    "HIGH",
                requestedTitle =
                    "Payments unavailable",
                requestedStatus =
                    "Investigating",
                requestedSeverity =
                    "HIGH"
            )
        )
    }

    @Test
    fun differentStatusUnderSameIncidentId_marksCreateSuperseded() {

        assertEquals(
            CreateIncidentResolutionDecision
                .SUPERSEDED,
            decideCreateIncidentResolution(
                authoritativeTitle =
                    "Payments unavailable",
                authoritativeStatus =
                    "Resolved",
                authoritativeSeverity =
                    "HIGH",
                requestedTitle =
                    "Payments unavailable",
                requestedStatus =
                    "Investigating",
                requestedSeverity =
                    "HIGH"
            )
        )
    }

    @Test
    fun differentSeverityUnderSameIncidentId_marksCreateSuperseded() {

        assertEquals(
            CreateIncidentResolutionDecision
                .SUPERSEDED,
            decideCreateIncidentResolution(
                authoritativeTitle =
                    "Payments unavailable",
                authoritativeStatus =
                    "Investigating",
                authoritativeSeverity =
                    "CRITICAL",
                requestedTitle =
                    "Payments unavailable",
                requestedStatus =
                    "Investigating",
                requestedSeverity =
                    "HIGH"
            )
        )
    }
}