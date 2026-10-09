package com.signaldesk.relay.ui.incidents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signaldesk.relay.data.realtime.CreateIncidentCommandQueue
import com.signaldesk.relay.data.realtime.CreateIncidentOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.IncidentSeverity
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CreateIncidentViewModel(
    application: Application
) : AndroidViewModel(application) {


    val createInProgress =
        MutableStateFlow(false)

    val createError =
        MutableStateFlow<String?>(null)
    fun createIncident(
        title: String,
        status: String,
        onCreated: () -> Unit
    ) {

        val normalizedTitle =
            title.trim()

        val normalizedStatus =
            status.trim()

        if (
            normalizedTitle.isBlank() ||
            normalizedStatus.isBlank()
        ) {
            return
        }

        val session =
            SessionManager
                .sessionState
                .value

        if (
            session !is
                SessionState.SignedIn
        ) {
            return
        }

        val ownerPrincipal =
            session.userId

        val incidentId =
            "INC-" +
                UUID.randomUUID()
                    .toString()
                    .take(6)
                    .uppercase()

        val commandId =
            "CMD-CREATE-" +
                UUID.randomUUID()
                    .toString()
                    .uppercase()

        if (createInProgress.value) {
            return
        }

        createError.value =
            null

        createInProgress.value =
            true

        viewModelScope.launch {

            try {

                CreateIncidentCommandQueue.enqueue(
                    context =
                        getApplication<Application>(),
                    commandId =
                        commandId,
                    incidentId =
                        incidentId,
                    title =
                        normalizedTitle,
                    status =
                        normalizedStatus,
                    severity =
                        IncidentSeverity
                            .MEDIUM
                            .name,
                    ownerPrincipal =
                        ownerPrincipal
                )

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (
                error: Exception
            ) {

                createError.value =
                    "Unable to save incident. Try again."

                createInProgress.value =
                    false

                return@launch
            }

            /*
             * Persistence succeeded. The operator's intent is now
             * durable before delivery is requested.
             */
            CreateIncidentOutboxCoordinator
                .kick()

            createInProgress.value =
                false

            onCreated()
        }
    }
}