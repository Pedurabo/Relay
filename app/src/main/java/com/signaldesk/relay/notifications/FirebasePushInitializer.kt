package com.signaldesk.relay.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.signaldesk.relay.BuildConfig

object FirebasePushInitializer {

    fun initialize(
        context: Context
    ): Boolean {

        if (!isConfigured()) {
            return false
        }

        if (
            FirebaseApp
                .getApps(
                    context
                )
                .isEmpty()
        ) {

            val options =
                FirebaseOptions
                    .Builder()
                    .setApplicationId(
                        BuildConfig
                            .FIREBASE_APPLICATION_ID
                    )
                    .setProjectId(
                        BuildConfig
                            .FIREBASE_PROJECT_ID
                    )
                    .setApiKey(
                        BuildConfig
                            .FIREBASE_API_KEY
                    )
                    .setGcmSenderId(
                        BuildConfig
                            .FIREBASE_SENDER_ID
                    )
                    .build()

            FirebaseApp
                .initializeApp(
                    context,
                    options
                )
        }

        return true
    }

    fun fetchToken(
        context: Context,
        onToken: (String) -> Unit
    ) {

        if (
            !initialize(
                context
            )
        ) {
            return
        }

        FirebaseMessaging
            .getInstance()
            .token
            .addOnSuccessListener {
                token ->

                if (
                    token.isNotBlank()
                ) {
                    onToken(
                        token
                    )
                }
            }
    }

    private fun isConfigured():
        Boolean =
        BuildConfig
            .FIREBASE_APPLICATION_ID
            .isNotBlank() &&
            BuildConfig
                .FIREBASE_PROJECT_ID
                .isNotBlank() &&
            BuildConfig
                .FIREBASE_API_KEY
                .isNotBlank() &&
            BuildConfig
                .FIREBASE_SENDER_ID
                .isNotBlank()
}
