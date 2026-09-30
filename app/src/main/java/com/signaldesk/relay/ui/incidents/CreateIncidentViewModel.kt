package com.signaldesk.relay.ui.incidents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.repository.IncidentRepository
import kotlinx.coroutines.launch

class CreateIncidentViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = IncidentRepository(RelayDatabase.getInstance(application).incidentDao())

    fun createIncident(
        title: String,
        status: String,
        onCreated: () -> Unit
    ) {
        if (title.isBlank() || status.isBlank()) {
            return
        }

        viewModelScope.launch {
            repository.createIncident(
                title = title,
                status = status
            )

            onCreated()
        }
    }
}

