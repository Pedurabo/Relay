package com.signaldesk.relay.data.mapper

import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.IncidentSeverity

fun IncidentEntity.toDomain():
    Incident {

    return Incident(
        id = id,
        title = title,
        status = status,
        severity =
            IncidentSeverity
                .fromStoredValue(
                    severity
                )
    )
}
