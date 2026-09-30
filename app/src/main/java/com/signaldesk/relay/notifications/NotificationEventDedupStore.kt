package com.signaldesk.relay.notifications

import android.content.Context

class NotificationEventDedupStore(
    context: Context
) {

    private val preferences =
        context
            .getSharedPreferences(
                "relay_notification_events",
                Context.MODE_PRIVATE
            )

    fun markIfNew(
        eventId: String
    ): Boolean {

        synchronized(
            LOCK
        ) {

            if (
                preferences
                    .contains(
                        eventId
                    )
            ) {
                return false
            }

            val now =
                System.currentTimeMillis()

            val editor =
                preferences
                    .edit()
                    .putLong(
                        eventId,
                        now
                    )

            val cutoff =
                now -
                RETENTION_MILLIS

            preferences
                .all
                .forEach {
                    entry ->

                    val timestamp =
                        entry.value as?
                            Long
                            ?: return@forEach

                    if (
                        timestamp <
                        cutoff
                    ) {
                        editor.remove(
                            entry.key
                        )
                    }
                }

            editor.apply()

            return true
        }
    }

    companion object {

        private val LOCK =
            Any()

        private const val RETENTION_MILLIS =
            7L *
                24L *
                60L *
                60L *
                1000L
    }
}
