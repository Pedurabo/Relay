package com.signaldesk.relay.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedOwnershipContextTest {

    private fun signedIn(
        userId: String
    ) = SessionState.SignedIn(
        userId = userId,
        userName = "Test User",
        accessToken = "access",
        refreshToken = "refresh",
        accessTokenExpiresAt = 100000L
    )

    @Test
    fun capturesAuthenticatedOwnerAndGeneration() {
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                12L
            )

        assertNotNull(context)
        assertEquals("account-a", context?.ownerPrincipal)
        assertEquals(12L, context?.sessionGeneration)
    }

    @Test
    fun signedOutStateCannotCreateContext() {
        assertNull(
            AuthenticatedOwnershipContext.capture(
                SessionState.SignedOut,
                12L
            )
        )
    }

    @Test
    fun sameOwnerAndGenerationRemainValid() {
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                12L
            )!!

        assertTrue(
            context.matches(
                signedIn("account-a"),
                12L
            )
        )
    }

    @Test
    fun replacedGenerationInvalidatesContext() {
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                12L
            )!!

        assertFalse(
            context.matches(
                signedIn("account-a"),
                13L
            )
        )
    }

    @Test
    fun replacementOwnerOrSignedOutStateIsRejected() {
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                12L
            )!!

        assertFalse(
            context.matches(
                signedIn("account-b"),
                12L
            )
        )

        assertFalse(
            context.matches(
                SessionState.SignedOut,
                12L
            )
        )

        assertNull(
            AuthenticatedOwnershipContext.capture(
                signedIn("   "),
                12L
            )
        )
    }
}
