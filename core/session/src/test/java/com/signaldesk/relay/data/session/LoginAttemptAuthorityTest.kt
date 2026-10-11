package com.signaldesk.relay.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginAttemptAuthorityTest {

    @Test
    fun currentAttemptCanPublish() {
        val authority = LoginAttemptAuthority()
        val origin = authority.capture()

        val result = authority.publishIfCurrent(origin) {
            "published"
        }

        assertEquals("published", result)
    }

    @Test
    fun invalidatedAttemptCannotPublish() {
        val authority = LoginAttemptAuthority()
        val origin = authority.capture()
        var published = false

        authority.invalidate()

        val result = authority.publishIfCurrent(origin) {
            published = true
            "published"
        }

        assertNull(result)
        assertFalse(published)
    }

    @Test
    fun newAttemptAfterInvalidationCanPublish() {
        val authority = LoginAttemptAuthority()
        val oldOrigin = authority.capture()

        authority.invalidate()

        val newOrigin = authority.capture()

        assertFalse(oldOrigin == newOrigin)

        assertEquals(
            "new-session",
            authority.publishIfCurrent(newOrigin) {
                "new-session"
            }
        )
    }

    @Test
    fun delayedResponseCannotPublishAfterSignOutIntent() =
        runBlocking {

            val authority = LoginAttemptAuthority()
            val origin = authority.capture()

            val requestStarted = CompletableDeferred<Unit>()
            val finishRequest = CompletableDeferred<Unit>()

            var published = false

            val request = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                requestStarted.complete(Unit)
                finishRequest.await()

                authority.publishIfCurrent(origin) {
                    published = true
                    "session"
                }
            }

            requestStarted.await()

            authority.invalidate()
            finishRequest.complete(Unit)

            assertNull(request.await())
            assertFalse(published)
        }

    @Test
    fun publicationAndInvalidationHaveConsistentOrdering() =
        runBlocking {

            val authority = LoginAttemptAuthority()
            val origin = authority.capture()

            val publishing = CompletableDeferred<Unit>()
            val allowPublicationToFinish =
                CompletableDeferred<Unit>()

            // Use threads to exercise the synchronous monitor
            // independently of coroutine scheduling.
            val first = async(kotlinx.coroutines.Dispatchers.Default) {
                authority.publishIfCurrent(origin) {
                    publishing.complete(Unit)
                    Thread.sleep(30)
                    "published"
                }
            }

            publishing.await()

            val second = async(kotlinx.coroutines.Dispatchers.Default) {
                authority.invalidate()
                allowPublicationToFinish.complete(Unit)
            }

            assertEquals("published", first.await())
            second.await()
            allowPublicationToFinish.await()

            assertTrue(authority.capture() != origin)

            // Once invalidation completes, the old attempt
            // cannot publish anything else.
            assertNull(
                authority.publishIfCurrent(origin) {
                    "unexpected"
                }
            )
        }
}