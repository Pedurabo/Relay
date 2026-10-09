package com.signaldesk.relay.navigation
import com.signaldesk.relay.model.IncidentSeverity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.signaldesk.relay.ui.incidents.CreateIncidentScreen
import com.signaldesk.relay.ui.incidents.CreateIncidentViewModel
import com.signaldesk.relay.ui.incidents.IncidentDetailScreen
import com.signaldesk.relay.ui.incidents.IncidentDetailViewModel
import com.signaldesk.relay.ui.incidents.IncidentsScreen
import com.signaldesk.relay.ui.incidents.IncidentsViewModel
import kotlinx.coroutines.delay

private object Routes {
    const val INCIDENTS = "incidents"
    const val CREATE_INCIDENT = "incidents/create"
    const val INCIDENT_DETAIL = "incident/{incidentId}"
    const val CREATE_FEEDBACK = "create_feedback"

    fun incidentDetail(
        incidentId: String
    ): String {
        return "incident/$incidentId"
    }
}

@Composable
fun RelayNavHost(
    modifier: Modifier = Modifier,
    initialIncidentId: String? = null
) {
    val navController =
        rememberNavController()

    LaunchedEffect(
        initialIncidentId
    ) {

        initialIncidentId
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let { incidentId ->

                navController.navigate(
                    Routes.incidentDetail(
                        incidentId
                    )
                )
            }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.INCIDENTS,
        modifier = modifier
    ) {

        composable(
            route = Routes.INCIDENTS
        ) { backStackEntry ->
            val viewModel:
                IncidentsViewModel = viewModel()

            val incidents by
                viewModel.incidents.collectAsState()

            val connectionState by
                viewModel.connectionState.collectAsState()

            val sessionState by
                viewModel.sessionState.collectAsState()

            val signInInProgress by
                viewModel.signInInProgress.collectAsState()

            val signInError by
                viewModel.signInError.collectAsState()

            val createFeedback by
                backStackEntry
                    .savedStateHandle
                    .getStateFlow<String?>(
                        Routes.CREATE_FEEDBACK,
                        null
                    )
                    .collectAsState()

            LaunchedEffect(
                createFeedback
            ) {
                if (createFeedback != null) {
                    delay(4_000L)

                    backStackEntry
                        .savedStateHandle
                        .remove<String>(
                            Routes.CREATE_FEEDBACK
                        )
                }
            }

            IncidentsScreen(
                incidents = incidents,
                connectionState = connectionState,
                sessionState = sessionState,
                signInInProgress =
                    signInInProgress,
                signInError =
                    signInError,
                operationMessage =
                    createFeedback,
                onIncidentClick = { incidentId ->
                    navController.navigate(
                        Routes.incidentDetail(
                            incidentId
                        )
                    )
                },
                onCreateIncidentClick = {
                    navController.navigate(
                        Routes.CREATE_INCIDENT
                    )
                }
            ,
                onSignIn =
                    viewModel::signIn,
                onSignOut =
                    viewModel::signOut
            )
        }

        composable(
            route = Routes.CREATE_INCIDENT
        ) {
            val viewModel:
                CreateIncidentViewModel = viewModel()

            val createInProgress by
                viewModel
                    .createInProgress
                    .collectAsState()

            val createError by
                viewModel
                    .createError
                    .collectAsState()

            CreateIncidentScreen(
                createInProgress =
                    createInProgress,
                createError =
                    createError,
                onCreateIncident = {
                    title,
                    status ->

                    viewModel.createIncident(
                        title = title,
                        status = status,
                        onCreated = {
                            navController
                                .previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(
                                    Routes.CREATE_FEEDBACK,
                                    "Incident saved. Waiting to sync."
                                )

                            navController.popBackStack()
                        }
                    )
                }
            )
        }

        composable(
            route = Routes.INCIDENT_DETAIL,
            arguments = listOf(
                navArgument("incidentId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val routeIncidentId =
                checkNotNull(
                    backStackEntry
                        .arguments
                        ?.getString(
                            "incidentId"
                        )
                )

            val viewModel:
                IncidentDetailViewModel =
                viewModel(
                    viewModelStoreOwner =
                        backStackEntry,
                    key =
                        "incident-detail-$routeIncidentId"
                )

            val incident by
                viewModel.incident.collectAsState()

            val timeline by
                viewModel.timeline.collectAsState()

            val timelineRetryInProgressEntryId by
                viewModel
                    .timelineRetryInProgressEntryId
                    .collectAsState()

            val timelineRetryErrorEntryId by
                viewModel
                    .timelineRetryErrorEntryId
                    .collectAsState()

            val timelineRetryError by
                viewModel
                    .timelineRetryError
                    .collectAsState()

            val timelinePostInProgress by
                viewModel
                    .timelinePostInProgress
                    .collectAsState()

            val timelinePostError by
                viewModel
                    .timelinePostError
                    .collectAsState()

            val timelinePostSaved by
                viewModel
                    .timelinePostSaved
                    .collectAsState()

            val optimisticStatus =
                viewModel
                    .optimisticStatus
                    .collectAsState()
                    .value

            val statusUpdateInProgress =
                viewModel
                    .statusUpdateInProgress
                    .collectAsState()
                    .value

            val statusUpdateMessage =
                viewModel
                    .statusUpdateMessage
                    .collectAsState()
                    .value


            val statusUpdateError =
                viewModel
                    .statusUpdateError
                    .collectAsState()
                    .value

            val optimisticSeverity =
                viewModel
                    .optimisticSeverity
                    .collectAsState()
                    .value

            val severityUpdateInProgress =
                viewModel
                    .severityUpdateInProgress
                    .collectAsState()
                    .value

            val severityUpdateMessage =
                viewModel
                    .severityUpdateMessage
                    .collectAsState()
                    .value


            val severityUpdateError =
                viewModel
                    .severityUpdateError
                    .collectAsState()
                    .value


            IncidentDetailScreen(

                optimisticStatus =
                    optimisticStatus,
                statusUpdateInProgress =
                    statusUpdateInProgress,
                statusUpdateMessage =
                    statusUpdateMessage,
                statusUpdateError =
                    statusUpdateError,
                onStatusChange = { status ->

                    incident
                        ?.let { displayedIncident ->

                            viewModel
                                .updateStatus(
                                    targetIncidentId =
                                        displayedIncident.id,
                                    status =
                                        status
                                )
                        }
                },

                optimisticSeverity =
                    optimisticSeverity,
                severityUpdateInProgress =
                    severityUpdateInProgress,
                severityUpdateMessage =
                    severityUpdateMessage,
                severityUpdateError =
                    severityUpdateError,
                onSeverityChange = { severity ->

                    incident
                        ?.let { displayedIncident ->

                            viewModel
                                .updateSeverity(
                                    targetIncidentId =
                                        displayedIncident.id,
                                    severity =
                                        severity
                                )
                        }
                },
                incident = incident,
                timeline = timeline,
                timelinePostInProgress =
                    timelinePostInProgress,
                timelinePostError =
                    timelinePostError,
                timelinePostSaved =
                    timelinePostSaved,
                onTimelinePostSavedConsumed =
                    viewModel::consumeTimelinePostSaved,
                onPostUpdate =
                    viewModel::postUpdate,
                timelineRetryInProgressEntryId =
                    timelineRetryInProgressEntryId,
                timelineRetryErrorEntryId =
                    timelineRetryErrorEntryId,
                timelineRetryError =
                    timelineRetryError,
                onRetry =
                    viewModel::retry
            )
        }
    }
}












