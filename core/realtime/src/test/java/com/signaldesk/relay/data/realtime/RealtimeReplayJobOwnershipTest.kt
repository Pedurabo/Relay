package com.signaldesk.relay.data.realtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeReplayJobOwnershipTest {

    @Test
    fun currentReplayJobRemovesItsOwnRegistryEntry() {

        val replayJobs =
            ConcurrentHashMap<String, Job>()

        val currentJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            currentJob

        assertTrue(
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "incident-1",
                replayJob =
                    currentJob
            )
        )

        assertFalse(
            replayJobs.containsKey(
                "incident-1"
            )
        )
    }


    @Test
    fun staleReplayJobCannotRemoveReplacementJob() {

        val replayJobs =
            ConcurrentHashMap<String, Job>()

        val staleJob =
            Job()

        val replacementJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            replacementJob

        assertFalse(
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "incident-1",
                replayJob =
                    staleJob
            )
        )

        assertSame(
            replacementJob,
            replayJobs[
                "incident-1"
            ]
        )
    }


    @Test
    fun canceledReplayCannotRemoveFutureReplacement() {

        val replayJobs =
            ConcurrentHashMap<String, Job>()

        val oldJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            oldJob

        replayJobs.clear()

        val replacementJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            replacementJob

        assertFalse(
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "incident-1",
                replayJob =
                    oldJob
            )
        )

        assertSame(
            replacementJob,
            replayJobs[
                "incident-1"
            ]
        )
    }
}
