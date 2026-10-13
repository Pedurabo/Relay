package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CancellationException

/**
 * Executes a presentation monitor and, after unexpected failure,
 * attempts at most one recovery.
 *
 * The caller supplies persistence reload, authorization and
 * retry-permit operations. This executor never sends outbox work.
 */
internal suspend fun <T : Any> executeMonitorWithOneRecovery(
    initial: T,
    monitor: suspend (T) -> Unit,
    reload: suspend () -> T?,
    mayRetry: (T) -> Boolean,
    acquireRetryPermit: () -> Boolean,
    onStopped: () -> Unit
) {
    try {
        monitor(initial)
        return
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Only an unexpected failure is potentially recoverable.
    }

    val reloaded = try {
        reload()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    if (
        reloaded != null &&
        mayRetry(reloaded) &&
        acquireRetryPermit()
    ) {
        try {
            monitor(reloaded)
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The single recovery attempt has failed.
        }
    }

    onStopped()
}