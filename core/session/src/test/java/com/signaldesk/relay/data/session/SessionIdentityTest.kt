package com.signaldesk.relay.data.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionIdentityTest {

    @Test
    fun identicalOwnerAndGenerationMatch() {
        val identity = SessionIdentity("account-a", 10L)
        assertTrue(identity.owns("account-a", 10L))
    }

    @Test
    fun differentAccountDoesNotMatch() {
        val identity = SessionIdentity("account-b", 11L)
        assertFalse(identity.owns("account-a", 11L))
    }

    @Test
    fun sameAccountNewGenerationIsDifferentIdentity() {
        val original = SessionIdentity("account-a", 10L)
        val returned = SessionIdentity("account-a", 12L)

        assertNotEquals(original, returned)
        assertFalse(original.owns("account-a", 12L))
        assertTrue(returned.owns("account-a", 12L))
    }

    @Test
    fun signedOutIdentityCannotOwnAnOperation() {
        val signedOut = SessionIdentity(null, 13L)

        assertFalse(signedOut.isAuthenticated)
        assertFalse(signedOut.owns("account-a", 13L))
    }

    @Test
    fun signedOutGenerationsRemainDistinguishable() {
        val first = SessionIdentity(null, 13L)
        val second = SessionIdentity(null, 14L)

        assertNotEquals(first, second)
    }

    @Test
    fun blankPrincipalIsNotAuthenticated() {
        val identity = SessionIdentity("", 15L)

        assertFalse(identity.isAuthenticated)
        assertFalse(identity.owns("", 15L))
    }
}