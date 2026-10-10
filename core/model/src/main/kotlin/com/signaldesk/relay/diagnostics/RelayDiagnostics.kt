package com.signaldesk.relay.diagnostics

enum class DiagnosticLevel {
    INFO,
    WARNING,
    ERROR
}

data class DiagnosticEvent(
    val name: String,
    val level: DiagnosticLevel,
    val attributes: Map<String, String> =
        emptyMap(),
    val error: Throwable? =
        null
)

fun interface DiagnosticReporter {

    fun report(
        event: DiagnosticEvent
    )
}

object RelayDiagnostics {

    @Volatile
    private var reporter:
        DiagnosticReporter? =
        null

    fun configure(
        reporter: DiagnosticReporter?
    ) {

        this.reporter =
            reporter
    }

    fun info(
        name: String,
        attributes:
            Map<String, String> =
            emptyMap()
    ) {

        reporter
            ?.report(
                DiagnosticEvent(
                    name =
                        name,
                    level =
                        DiagnosticLevel.INFO,
                    attributes =
                        attributes
                )
            )
    }

    fun warning(
        name: String,
        attributes:
            Map<String, String> =
            emptyMap(),
        error: Throwable? =
            null
    ) {

        reporter
            ?.report(
                DiagnosticEvent(
                    name =
                        name,
                    level =
                        DiagnosticLevel.WARNING,
                    attributes =
                        attributes,
                    error =
                        error
                )
            )
    }

    fun error(
        name: String,
        attributes:
            Map<String, String> =
            emptyMap(),
        error: Throwable? =
            null
    ) {

        reporter
            ?.report(
                DiagnosticEvent(
                    name =
                        name,
                    level =
                        DiagnosticLevel.ERROR,
                    attributes =
                        attributes,
                    error =
                        error
                )
            )
    }
}