package com.signaldesk.relay.appstate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppVisibilityTracker {

    private val startedActivityCount =
        AtomicInteger(0)

    private val initialized =
        AtomicBoolean(false)

    private val _foregroundState =
        MutableStateFlow(false)

    val foregroundState:
        StateFlow<Boolean> =
        _foregroundState.asStateFlow()

    val isForeground: Boolean
        get() =
            _foregroundState.value

    fun initialize(
        application: Application
    ) {
        if (
            !initialized.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        application.registerActivityLifecycleCallbacks(
            object :
                Application.ActivityLifecycleCallbacks {

                override fun onActivityStarted(
                    activity: Activity
                ) {

                    val count =
                        startedActivityCount
                            .incrementAndGet()

                    if (
                        count > 0
                    ) {
                        _foregroundState.value =
                            true
                    }
                }

                override fun onActivityStopped(
                    activity: Activity
                ) {

                    val count =
                        startedActivityCount
                            .updateAndGet {
                                current ->

                                (current - 1)
                                    .coerceAtLeast(0)
                            }

                    _foregroundState.value =
                        count > 0
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) = Unit

                override fun onActivityResumed(
                    activity: Activity
                ) = Unit

                override fun onActivityPaused(
                    activity: Activity
                ) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle
                ) = Unit

                override fun onActivityDestroyed(
                    activity: Activity
                ) = Unit
            }
        )
    }
}
