package com.signaldesk.relay.ui.incidents

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.session.SessionOwnershipGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotOwnershipRoomTest {

    private lateinit var database: RelayDatabase
    private lateinit var gate: SessionOwnershipGate

    private var generation = 1L

    private fun incident(sequence: Long) =
        IncidentEntity(
            id = "INC-OWNERSHIP",
            title = "Ownership test",
            status = "Active",
            latestSequence = sequence,
            severity = "HIGH"
        )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext,
            RelayDatabase::class.java
        ).build()

        gate = SessionOwnershipGate()
        generation = 1L
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun activeSnapshotCommitsBeforeWaitingTransition() =
        runBlocking {

            val dao = database.incidentDao()

            val written = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var transitioned = false

            val snapshot = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                gate.withSnapshotOwnership(
                    expectedGeneration = 1L,
                    currentGeneration = { generation }
                ) {
                    database.withTransaction {
                        dao.reconcileAuthoritativeSnapshot(
                            listOf(incident(12L))
                        )
                        written.complete(Unit)
                        release.await()
                    }
                }
            }

            try {
                written.await()

                val transition = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    gate.withSessionTransition {
                        generation = 2L
                        transitioned = true
                    }
                }

                yield()

                assertFalse(transitioned)

                release.complete(Unit)

                snapshot.await()
                transition.await()

                assertTrue(transitioned)
                assertEquals(2L, generation)
                assertEquals(
                    12L,
                    dao.getById("INC-OWNERSHIP")
                        ?.latestSequence
                )
            } finally {
                release.complete(Unit)
                snapshot.cancel()
            }
        }

    @Test
    fun snapshotQueuedBehindTransitionCannotWrite() =
        runBlocking {

            val dao = database.incidentDao()

            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val transition = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                gate.withSessionTransition {
                    entered.complete(Unit)
                    release.await()
                    generation = 2L
                }
            }

            try {
                entered.await()

                var executed = false

                val snapshot = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    gate.withSnapshotOwnership(
                        expectedGeneration = 1L,
                        currentGeneration = { generation }
                    ) {
                        executed = true
                        dao.reconcileAuthoritativeSnapshot(
                            listOf(incident(12L))
                        )
                    }
                }

                release.complete(Unit)
                transition.await()

                assertNull(snapshot.await())
                assertFalse(executed)
                assertNull(
                    dao.getById("INC-OWNERSHIP")
                )
            } finally {
                release.complete(Unit)
                transition.cancel()
            }
        }

    @Test
    fun cancelledSnapshotRollsBackAndReleasesTransition() =
        runBlocking {

            val dao = database.incidentDao()

            dao.insert(incident(7L))

            val written = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val snapshot = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                gate.withSnapshotOwnership(
                    expectedGeneration = 1L,
                    currentGeneration = { generation }
                ) {
                    database.withTransaction {
                        dao.reconcileAuthoritativeSnapshot(
                            listOf(incident(12L))
                        )
                        written.complete(Unit)
                        release.await()
                    }
                }
            }

            try {
                written.await()

                val transition = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    gate.withSessionTransition {
                        generation = 2L
                    }
                }

                snapshot.cancelAndJoin()
                transition.await()

                assertEquals(2L, generation)
                assertEquals(
                    7L,
                    dao.getById("INC-OWNERSHIP")
                        ?.latestSequence
                )
            } finally {
                snapshot.cancel()
                release.complete(Unit)
            }
        }
}