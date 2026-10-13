package com.signaldesk.relay.data.session

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionIdentityPublicationTest {

    @Test
    fun repeatedSignOutPublishesNewGenerationEvenWhenStateIsUnchanged() =
        runBlocking {
            val initialState = SessionManager.sessionState.value
            val initialIdentity = SessionManager.sessionIdentity.value
            val initialGeneration = SessionManager.sessionGeneration

            assertEquals(SessionState.SignedOut, initialState)
            assertEquals(null, initialIdentity.ownerPrincipal)
            assertEquals(initialGeneration, initialIdentity.generation)

            val observed = mutableListOf<SessionIdentity>()

            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                SessionManager.sessionIdentity
                    .take(2)
                    .toList(observed)
            }

            val signedOut =
                SessionManager.signOutIfUnchangedCoordinated(
                    expectedGeneration = initialGeneration,
                    expectedSession = initialState
                )

            collector.join()

            assertTrue(signedOut)
            assertEquals(2, observed.size)
            assertEquals(initialIdentity, observed[0])
            assertFalse(observed[1].isAuthenticated)
            assertEquals(null, observed[1].ownerPrincipal)
            assertTrue(observed[1].generation > observed[0].generation)

            assertEquals(
                SessionManager.sessionGeneration,
                observed[1].generation
            )
            assertEquals(
                observed[1],
                SessionManager.sessionIdentity.value
            )
        }
}