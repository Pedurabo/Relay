package com.signaldesk.relay.data.realtime

object RealtimeEndpointConfig {

    private var configuredWebSocketUrl:
        String? =
        null

    val webSocketUrl: String
        get() =
            checkNotNull(
                configuredWebSocketUrl
            ) {
                "RealtimeEndpointConfig must be configured before use."
            }

    fun configure(
        webSocketUrl: String
    ) {

        require(
            webSocketUrl.isNotBlank()
        )

        configuredWebSocketUrl =
            webSocketUrl
    }
}