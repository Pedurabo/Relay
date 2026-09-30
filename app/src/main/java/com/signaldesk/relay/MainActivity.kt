package com.signaldesk.relay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import com.signaldesk.relay.navigation.RelayNavHost
import com.signaldesk.relay.ui.theme.RelayTheme

class MainActivity : ComponentActivity() {

    private val notificationIncidentId =
        mutableStateOf<String?>(
            null
        )

    companion object {
        const val EXTRA_INCIDENT_ID =
            "relay_incident_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationIncidentId.value =
            intent
                .getStringExtra(
                    EXTRA_INCIDENT_ID
                )

        setContent {
            RelayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RelayNavHost(
                        initialIncidentId =
                            notificationIncidentId
                                .value
                    )
                }
            }
        }
    }


    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        notificationIncidentId.value =
            intent
                .getStringExtra(
                    EXTRA_INCIDENT_ID
                )
    }
}
