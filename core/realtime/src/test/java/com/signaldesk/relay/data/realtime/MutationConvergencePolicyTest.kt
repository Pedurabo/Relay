package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class MutationConvergencePolicyTest {

    @Test
    fun missingAuthoritativeValue_keepsCommandPending() {

        assertEquals(
            MutationConvergenceDecision
                .KEEP_PENDING,
            decideMutationConvergence(
                currentValue =
                    null,
                baseValue =
                    "Active",
                requestedValue =
                    "Monitoring"
            )
        )
    }

    @Test
    fun unchangedBaseValue_keepsCommandPending() {

        assertEquals(
            MutationConvergenceDecision
                .KEEP_PENDING,
            decideMutationConvergence(
                currentValue =
                    "Active",
                baseValue =
                    "Active",
                requestedValue =
                    "Monitoring"
            )
        )
    }

    @Test
    fun requestedAuthoritativeValue_marksCommandConverged() {

        assertEquals(
            MutationConvergenceDecision
                .CONVERGED,
            decideMutationConvergence(
                currentValue =
                    "Monitoring",
                baseValue =
                    "Active",
                requestedValue =
                    "Monitoring"
            )
        )
    }

    @Test
    fun thirdAuthoritativeValue_marksCommandSuperseded() {

        assertEquals(
            MutationConvergenceDecision
                .SUPERSEDED,
            decideMutationConvergence(
                currentValue =
                    "Resolved",
                baseValue =
                    "Active",
                requestedValue =
                    "Monitoring"
            )
        )
    }

    @Test
    fun requestedValueWinsWhenBaseAndRequestedAreIdentical() {

        assertEquals(
            MutationConvergenceDecision
                .CONVERGED,
            decideMutationConvergence(
                currentValue =
                    "Active",
                baseValue =
                    "Active",
                requestedValue =
                    "Active"
            )
        )
    }

    @Test
    fun severityValues_followSamePolicy() {

        assertEquals(
            MutationConvergenceDecision
                .CONVERGED,
            decideMutationConvergence(
                currentValue =
                    "CRITICAL",
                baseValue =
                    "MEDIUM",
                requestedValue =
                    "CRITICAL"
            )
        )

        assertEquals(
            MutationConvergenceDecision
                .SUPERSEDED,
            decideMutationConvergence(
                currentValue =
                    "HIGH",
                baseValue =
                    "MEDIUM",
                requestedValue =
                    "CRITICAL"
            )
        )
    }
}