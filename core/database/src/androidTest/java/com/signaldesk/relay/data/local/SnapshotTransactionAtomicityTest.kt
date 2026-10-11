package com.signaldesk.relay.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotTransactionAtomicityTest {

    private lateinit var database: RelayDatabase

    @Before
    fun setUp() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            RelayDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun incident(sequence: Long) =
        IncidentEntity(
            id = "INC-245",
            title = "Transaction test",
            status = "Active",
            severity = "HIGH",
            latestSequence = sequence
        )

    private fun gap() =
        IncidentSequenceGapEntity(
            incidentId = "INC-245",
            expectedSequence = 8L,
            receivedSequence = 12L,
            detectedAt = 100L
        )

    private suspend fun seed() {
        database.incidentDao().insert(
            incident(7L)
        )

        database.incidentSequenceGapDao().upsert(
            gap()
        )
    }

    @Test
    fun successfulSnapshotCommitsIncidentAndGap() =
        runBlocking {

            seed()

            val applied =
                database.incidentDao()
                    .reconcileAuthoritativeSnapshot(
                        listOf(incident(12L))
                    )

            assertEquals(1, applied)

            assertEquals(
                12L,
                database.incidentDao()
                    .getById("INC-245")
                    ?.latestSequence
            )

            assertNull(
                database.incidentSequenceGapDao()
                    .getByIncidentId("INC-245")
            )
        }

    @Test
    fun failedTransactionRollsBackBothWrites() =
        runBlocking {

            seed()

            try {
                database.withTransaction {

                    database.incidentDao()
                        .reconcileAuthoritativeSnapshot(
                            listOf(incident(12L))
                        )

                    error("Intentional transaction failure")
                }

                fail("Expected transaction failure")

            } catch (error: IllegalStateException) {
                assertEquals(
                    "Intentional transaction failure",
                    error.message
                )
            }

            assertEquals(
                7L,
                database.incidentDao()
                    .getById("INC-245")
                    ?.latestSequence
            )

            val remainingGap =
                database.incidentSequenceGapDao()
                    .getByIncidentId("INC-245")

            assertNotNull(remainingGap)
            assertEquals(
                8L,
                remainingGap?.expectedSequence
            )
            assertEquals(
                12L,
                remainingGap?.receivedSequence
            )
        }

    @Test
    fun cancelledTransactionRollsBackUncommittedWrites() =
        runBlocking {

            seed()

            val written =
                CompletableDeferred<Unit>()

            val release =
                CompletableDeferred<Unit>()

            val transaction = async {

                database.withTransaction {

                    database.incidentDao()
                        .reconcileAuthoritativeSnapshot(
                            listOf(incident(12L))
                        )

                    written.complete(Unit)

                    release.await()
                }
            }

            try {
                written.await()

                transaction.cancelAndJoin()

            } finally {
                release.complete(Unit)
                transaction.cancelAndJoin()
            }

            assertEquals(
                7L,
                database.incidentDao()
                    .getById("INC-245")
                    ?.latestSequence
            )

            val remainingGap =
                database.incidentSequenceGapDao()
                    .getByIncidentId("INC-245")

            assertNotNull(remainingGap)

            assertEquals(
                8L,
                remainingGap?.expectedSequence
            )

            assertEquals(
                12L,
                remainingGap?.receivedSequence
            )
        }
}