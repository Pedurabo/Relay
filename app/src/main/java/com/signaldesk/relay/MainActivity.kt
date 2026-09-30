package com.signaldesk.relay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.signaldesk.relay.navigation.RelayNavHost
import com.signaldesk.relay.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INCIDENT_ID =
            "relay_incident_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RelayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RelayNavHost(
                        initialIncidentId =
                            intent
                                .getStringExtra(
                                    EXTRA_INCIDENT_ID
                                )
                    )
                }
            }
        }
    }
}
