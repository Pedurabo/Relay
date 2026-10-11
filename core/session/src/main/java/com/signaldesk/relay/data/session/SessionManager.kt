package com.signaldesk.relay.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class SessionGenerationCounter {

    private val counter =
        java.util.concurrent.atomic.AtomicLong(0L)

    fun current(): Long =
        counter.get()

    fun advance(): Long =
        counter.incrementAndGet()
}

object SessionManager {
    /*
     * Session transitions requested by the user must
     * outlive an individual screen's ViewModel.
     *
     * This scope is process-lifetime, not durable across
     * application process termination.
     */
    private val sessionOperationsScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default
        )

    private val sessionGenerationCounter =
        SessionGenerationCounter()

    val sessionGeneration: Long
        get() = sessionGenerationCounter.current()


    private val _sessionState =
        MutableStateFlow<SessionState>(
            SessionState.SignedOut
        )

    val sessionState:
        StateFlow<SessionState> =
        _sessionState.asStateFlow()

    private val _sessionTerminationReason =
        MutableStateFlow<SessionTerminationReason?>(
            null
        )

    val sessionTerminationReason:
        StateFlow<SessionTerminationReason?> =
        _sessionTerminationReason.asStateFlow()

    private var credentialStore:
        SessionCredentialStore? =
        null

    private var initialized =
        false

    fun initialize(
        context: Context
    ) {
        if (initialized) {
            return
        }

        val store =
            SessionCredentialStore(
                context.applicationContext
            )

        credentialStore =
            store

        val session =
            store.read()

        sessionGenerationCounter.advance()

        _sessionState.value =
            if (
                session != null
            ) {
                SessionState.SignedIn(
                    userId =
                        session.userId,
                    userName =
                        session.userName,
                    accessToken =
                        session.accessToken,
                    refreshToken =
                        session.refreshToken,
                    accessTokenExpiresAt =
                        session.accessTokenExpiresAt
                )
            } else {
                SessionState.SignedOut
            }

        initialized = true
    }

    private fun establishSession(
        session:
            AuthenticatedSession
    ) {



        require(
            session.userId.isNotBlank()
        )

        require(
            session.userName.isNotBlank()
        )

        require(
            session.accessToken.isNotBlank()
        )

        require(
            session.refreshToken.isNotBlank()
        )

        require(
            session.accessTokenExpiresAt >
                0L
        )

        val store =
            checkNotNull(
                credentialStore
            ) {
                "SessionManager must be initialized first."
            }

        store.save(
            session
        )

        _sessionTerminationReason.value =
            null

        sessionGenerationCounter.advance()

        _sessionState.value =
            SessionState.SignedIn(
                userId =
                    session.userId,
                userName =
                    session.userName,
                accessToken =
                    session.accessToken,
                refreshToken =
                    session.refreshToken,
                accessTokenExpiresAt =
                    session.accessTokenExpiresAt
            )
    }

    private fun signOut(
        reason: SessionTerminationReason? =
            null
    ) {



        credentialStore
            ?.clear()

        _sessionTerminationReason.value =
            reason

        sessionGenerationCounter.advance()

        _sessionState.value =
            SessionState.SignedOut
    }

    /*
     * Shared session-ownership coordination.
     *
     * All protected snapshot writes and authentication
     * transitions must use this same gate.
     *
     * The legacy synchronous transition methods remain
     * temporarily available during caller migration.
     */
    private val ownershipGate =
        SessionOwnershipGate()

    private val loginAttemptAuthority =
        LoginAttemptAuthority()

    /**
     * Captures the authenticated principal and current generation.
     *
     * Capture alone does not authorize a database write.
     * Every protected commit must use withAuthorizedOwnership.
     */
    fun captureAuthenticatedOwnership():
        AuthenticatedOwnershipContext? =
        AuthenticatedOwnershipContext.capture(
            sessionState = sessionState.value,
            generation = sessionGeneration
        )

    /**
     * Executes work only while the original authenticated
     * principal and session generation are still current.
     *
     * Shares the ownership gate used by coordinated sign-in,
     * sign-out and authoritative snapshot operations.
     *
     * A null result can mean rejection or an operation that
     * itself returned null. Callers must account for this.
     */
    suspend fun <T> withAuthorizedOwnership(
        context: AuthenticatedOwnershipContext,
        operation: suspend () -> T
    ): T? =
        ownershipGate.withSnapshotOwnership(
            expectedGeneration = context.sessionGeneration,
            currentGeneration = { sessionGeneration }
        ) {
            if (
                !context.matches(
                    sessionState = sessionState.value,
                    generation = sessionGeneration
                )
            ) {
                null
            } else {
                operation()
            }
        }

    fun captureLoginAttemptRevision(): Long =
        loginAttemptAuthority.capture()

    suspend fun <T> withAuthorizedSnapshot(
        expectedGeneration: Long,
        operation: suspend () -> T
    ): T? =
        ownershipGate.withSnapshotOwnership(
            expectedGeneration = expectedGeneration,
            currentGeneration = {
                sessionGeneration
            },
            operation = operation
        )

    suspend fun establishSessionCoordinated(
        session: AuthenticatedSession
    ) {
        ownershipGate.withSessionTransition {
            establishSession(session)
        }
    }

    suspend fun signOutCoordinated(
        reason: SessionTerminationReason? = null
    ) {
        ownershipGate.withSessionTransition {
            signOut(reason)
        }
    }
    /**
     * Returns false without publishing when the originating
     * session was superseded while the caller was suspended.
     *
     * The ownership check and session publication occur
     * under the same gate.
     */
    /**
     * Interactive login is authorized only while the same
     * signed-out generation remains active.
     *
     * The check and publication share the ownership gate.
     */
    suspend fun establishSessionIfSignedOutCoordinated(
        expectedGeneration: Long,
        expectedLoginRevision: Long,
        session: AuthenticatedSession
    ): Boolean =
        ownershipGate.withSessionTransition {
            loginAttemptAuthority.publishIfCurrent(
                expectedRevision = expectedLoginRevision
            ) {
                if (
                    sessionGeneration != expectedGeneration ||
                    sessionState.value != SessionState.SignedOut
                ) {
                    false
                } else {
                    establishSession(session)
                    true
                }
            } ?: false
        }

    suspend fun establishSessionIfCurrentCoordinated(
        expectedGeneration: Long,
        expectedSession: SessionState.SignedIn,
        session: AuthenticatedSession
    ): Boolean =
        ownershipGate.withSessionTransition {
            if (
                sessionGeneration != expectedGeneration ||
                sessionState.value != expectedSession
            ) {
                false
            } else {
                establishSession(session)
                true
            }
        }

    /**
     * Signs out only when both the generation and state
     * captured by the caller are still current.
     *
     * SignedOut is deliberately allowed as an expected state:
     * an explicit sign-out can invalidate an in-flight login.
     */

    /**
     * Schedules explicit sign-out independently of ViewModel
     * cancellation. Returns a Job for diagnostics/tests.
     *
     * A superseded request reports false without signing out
     * the replacement session.
     */
    fun requestSignOutIfUnchanged(
        expectedGeneration: Long,
        expectedSession: SessionState,
        onComplete: suspend (Boolean) -> Unit
    ): Job {

        // Invalidate pending login responses synchronously,
        // before the sign-out coroutine is scheduled.
        loginAttemptAuthority.invalidate()

        return sessionOperationsScope.launch {

            val signedOut =
                signOutIfUnchangedCoordinated(
                    expectedGeneration =
                        expectedGeneration,
                    expectedSession =
                        expectedSession
                )

            onComplete(signedOut)
        }
    }
    suspend fun signOutIfUnchangedCoordinated(
        expectedGeneration: Long,
        expectedSession: SessionState,
        reason: SessionTerminationReason? = null
    ): Boolean =
        ownershipGate.withSessionTransition {
            if (
                sessionGeneration != expectedGeneration ||
                sessionState.value != expectedSession
            ) {
                false
            } else {
                signOut(reason)
                true
            }
        }

    suspend fun signOutIfCurrentCoordinated(
        expectedGeneration: Long,
        expectedSession: SessionState.SignedIn,
        reason: SessionTerminationReason? = null
    ): Boolean =
        ownershipGate.withSessionTransition {
            if (
                sessionGeneration != expectedGeneration ||
                sessionState.value != expectedSession
            ) {
                false
            } else {
                signOut(reason)
                true
            }
        }
}
