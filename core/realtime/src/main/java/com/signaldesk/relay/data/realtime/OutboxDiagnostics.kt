package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.diagnostics.RelayDiagnostics

internal data class OutboxDiagnosticPayload(
    val name: String,
    val attributes: Map<String, String>
)

internal fun parseOutboxDiagnostic(
    raw: String
): OutboxDiagnosticPayload {

    val parts =
        raw.split("|")

    val rawName =
        parts
            .firstOrNull()
            .orEmpty()

    val name =
        rawName
            .lowercase()
            .replace(
                "_",
                "."
            )

    val attributes =
        linkedMapOf<String, String>()

    var positionalIndex =
        1

    parts
        .drop(1)
        .forEach { part ->

            val separator =
                part.indexOf("=")

            if (
                separator > 0
            ) {

                val key =
                    part.substring(
                        0,
                        separator
                    )

                val value =
                    part.substring(
                        separator + 1
                    )

                attributes[
                    key
                ] =
                    value

            } else if (
                part.isNotEmpty()
            ) {

                attributes[
                    "arg$positionalIndex"
                ] =
                    part

                positionalIndex +=
                    1
            }
        }

    return OutboxDiagnosticPayload(
        name =
            name,
        attributes =
            attributes
    )
}

internal fun reportOutboxEvent(
    raw: String
) {

    val payload =
        parseOutboxDiagnostic(
            raw
        )

    RelayDiagnostics.info(
        name =
            payload.name,
        attributes =
            payload.attributes
    )
}