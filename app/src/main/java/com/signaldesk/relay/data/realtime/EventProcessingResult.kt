package com.signaldesk.relay.data.realtime

enum class EventProcessingResult {
    APPLIED,
    IGNORED_STALE,
    DEFERRED,
    GAP_DETECTED,
    DUPLICATE
}
