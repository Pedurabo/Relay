
package com.signaldesk.relay.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentSnapshotClientTest {

    @Test
    fun parsesAuthoritativeIncidentSnapshot() {

        val snapshots =
            parseIncidentSnapshotResponse(
                """
                {
                  "count": 1,
                  "incidents": [
                    {
                      "id": "INC-244F",
                      "title": "Bootstrap proof",
                      "status": "Monitoring",
                      "severity": "CRITICAL",
                      "sequence": 7
                    }
                  ]
                }
                """.trimIndent()
            )

        assertEquals(
            1,
            snapshots.size
        )

        assertEquals(
            IncidentSnapshot(
                id = "INC-244F",
                title = "Bootstrap proof",
                status = "Monitoring",
                severity = "CRITICAL",
                sequence = 7L
            ),
            snapshots.single()
        )
    }

    @Test
    fun missingSeverityAndSequenceUseSafeDefaults() {

        val snapshot =
            parseIncidentSnapshotResponse(
                """
                {
                  "count": 1,
                  "incidents": [
                    {
                      "id": "INC-1",
                      "title": "Older payload",
                      "status": "Investigating"
                    }
                  ]
                }
                """.trimIndent()
            )
                .single()

        assertEquals(
            "MEDIUM",
            snapshot.severity
        )

        assertEquals(
            0L,
            snapshot.sequence
        )
    }
}
