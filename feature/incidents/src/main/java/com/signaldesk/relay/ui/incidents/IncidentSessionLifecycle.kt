package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.session.SessionIdentity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Orders presentation reset before restoration for each
 * observed authentication identity.
 *
 * Child restoration coroutines are cancelled when collectLatest
 * receives another identity.
 */
internal suspend fun collectIncidentSessionLifecycle(
    identities: Flow<SessionIdentity>,
    onReset: () -> Unit,
    restoreStatus: suspend (SessionIdentity) -> Unit,
    restoreSeverity: suspend (SessionIdentity) -> Unit,
    onRestoreFailure: (String, String) -> Unit = { _, _ -> }
) {
    var previousIdentity: SessionIdentity? = null

    identities.collectLatest { identity ->
        if (previousIdentity != null) {
            onReset()
        }

        previousIdentity = identity

        if (!identity.isAuthenticated) {
            return@collectLatest
        }

        coroutineScope {
            launch {
                try {
                    restoreStatus(identity)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Report only operation and exception type.
                    // No exception message, throwable or user data.
                    onRestoreFailure(
                        "status",
                        error.javaClass.simpleName
                    )
                }
            }

            launch {
                try {
                    restoreSeverity(identity)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    onRestoreFailure(
                        "severity",
                        error.javaClass.simpleName
                    )
                }
            }
        }
    }
}