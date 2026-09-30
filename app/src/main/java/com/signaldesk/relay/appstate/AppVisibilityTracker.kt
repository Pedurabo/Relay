package com.signaldesk.relay.appstate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object AppVisibilityTracker {

    private val startedActivityCount =
        AtomicInteger(0)

    private val initialized =
        AtomicBoolean(false)

    val isForeground: Boolean
        get() =
            startedActivityCount.get() > 0

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
                    startedActivityCount
                        .incrementAndGet()
                }

                override fun onActivityStopped(
                    activity: Activity
                ) {
                    startedActivityCount
                        .updateAndGet { current ->
                            (current - 1)
                                .coerceAtLeast(0)
                        }
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
