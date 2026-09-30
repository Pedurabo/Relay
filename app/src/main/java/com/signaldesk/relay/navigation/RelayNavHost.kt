package com.signaldesk.relay.navigation
import com.signaldesk.relay.model.IncidentSeverity

import androidx.compose.runtime.Composable
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

private object Routes {
    const val INCIDENTS = "incidents"
    const val CREATE_INCIDENT = "incidents/create"
    const val INCIDENT_DETAIL = "incident/{incidentId}"

    fun incidentDetail(
        incidentId: String
    ): String {
        return "incident/$incidentId"
    }
}

@Composable
fun RelayNavHost(
    modifier: Modifier = Modifier
) {
    val navController =
        rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.INCIDENTS,
        modifier = modifier
    ) {

        composable(
            route = Routes.INCIDENTS
        ) {
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

            IncidentsScreen(
                incidents = incidents,
                connectionState = connectionState,
                sessionState = sessionState,
                signInInProgress =
                    signInInProgress,
                signInError =
                    signInError,
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

            CreateIncidentScreen(
                onCreateIncident = {
                    title,
                    status ->

                    viewModel.createIncident(
                        title = title,
                        status = status,
                        onCreated = {
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

            val severityUpdateError =
                viewModel
                    .severityUpdateError
                    .collectAsState()
                    .value


            IncidentDetailScreen(
                
                optimisticSeverity =
                    optimisticSeverity,
                severityUpdateInProgress =
                    severityUpdateInProgress,
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
                onPostUpdate =
                    viewModel::postUpdate,
                onRetry =
                    viewModel::retry
            )
        }
    }
}












