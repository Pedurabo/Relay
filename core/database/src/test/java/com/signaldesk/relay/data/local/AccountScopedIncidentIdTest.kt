package com.signaldesk.relay.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountScopedIncidentIdTest {

    @Test
    fun identicalIncidentIdUnderDifferentOwnersIsDistinct() {
        val accountA = AccountScopedIncidentId(
            ownerPrincipal = "account-a",
            incidentId = "INC-SHARED"
        )

        val accountB = AccountScopedIncidentId(
            ownerPrincipal = "account-b",
            incidentId = "INC-SHARED"
        )

        assertNotEquals(accountA, accountB)
    }

    @Test
    fun identicalOwnerAndIncidentProducesEqualIdentity() {
        val first = AccountScopedIncidentId(
            ownerPrincipal = "account-a",
            incidentId = "INC-001"
        )

        val second = AccountScopedIncidentId(
            ownerPrincipal = "account-a",
            incidentId = "INC-001"
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun emptyLegacyOwnerIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            AccountScopedIncidentId(
                ownerPrincipal = "",
                incidentId = "INC-001"
            )
        }
    }

    @Test
    fun whitespaceOnlyOwnerIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            AccountScopedIncidentId(
                ownerPrincipal = "   ",
                incidentId = "INC-001"
            )
        }
    }

    @Test
    fun blankIncidentIdIsRejected() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            AccountScopedIncidentId(
                ownerPrincipal = "account-a",
                incidentId = ""
            )
        }
    }

    @Test
    fun ownerIdentityRemainsCaseSensitive() {
        val lower = AccountScopedIncidentId(
            ownerPrincipal = "account-a",
            incidentId = "INC-001"
        )

        val upper = AccountScopedIncidentId(
            ownerPrincipal = "ACCOUNT-A",
            incidentId = "INC-001"
        )

        assertNotEquals(lower, upper)
    }

    @Test
    fun identityPreservesExactAuthenticatedValues() {
        val identity = AccountScopedIncidentId(
            ownerPrincipal = "user-123",
            incidentId = "INC-246"
        )

        assertEquals("user-123", identity.ownerPrincipal)
        assertEquals("INC-246", identity.incidentId)
    }
}
