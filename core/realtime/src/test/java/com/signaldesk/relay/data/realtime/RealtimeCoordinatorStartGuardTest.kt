package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeCoordinatorStartGuardTest {

    @Test
    fun allowsInitialStartWhenNoJobExists() {

        assertTrue(
            shouldStartRealtimeCoordinator(
                null
            )
        )
    }


    @Test
    fun blocksDuplicateStartWhileExistingJobIsIncomplete() {

        val job =
            Job()

        try {

            assertFalse(
                shouldStartRealtimeCoordinator(
                    job
                )
            )

        } finally {

            job.cancel()
        }
    }


    @Test
    fun allowsRestartAfterExistingJobCompletes() {

        val job =
            Job()

        job.complete()

        assertTrue(
            job.isCompleted
        )

        assertTrue(
            shouldStartRealtimeCoordinator(
                job
            )
        )
    }
}
