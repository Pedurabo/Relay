plugins {
    alias(libs.plugins.ksp.plugin)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.signaldesk.relay"
    compileSdk = 37

    val releaseKeystorePath =
        System.getenv(
            "RELAY_RELEASE_KEYSTORE_PATH"
        )

    val releaseKeystorePassword =
        System.getenv(
            "RELAY_RELEASE_KEYSTORE_PASSWORD"
        )

    val releaseKeyAlias =
        System.getenv(
            "RELAY_RELEASE_KEY_ALIAS"
        )

    val releaseKeyPassword =
        System.getenv(
            "RELAY_RELEASE_KEY_PASSWORD"
        )

    val releaseSigningValues =
        listOf(
            releaseKeystorePath,
            releaseKeystorePassword,
            releaseKeyAlias,
            releaseKeyPassword
        )

    val releaseSigningConfigured =
        releaseSigningValues
            .all {
                !it.isNullOrBlank()
            }

    val releaseSigningPartiallyConfigured =
        releaseSigningValues
            .any {
                !it.isNullOrBlank()
            } &&
            !releaseSigningConfigured

    require(
        !releaseSigningPartiallyConfigured
    ) {
        "Release signing requires RELAY_RELEASE_KEYSTORE_PATH, " +
            "RELAY_RELEASE_KEYSTORE_PASSWORD, " +
            "RELAY_RELEASE_KEY_ALIAS, and " +
            "RELAY_RELEASE_KEY_PASSWORD together."
    }

    signingConfigs {

        if (
            releaseSigningConfigured
        ) {

            create(
                "release"
            ) {

                storeFile =
                    file(
                        checkNotNull(
                            releaseKeystorePath
                        )
                    )

                storePassword =
                    releaseKeystorePassword

                keyAlias =
                    releaseKeyAlias

                keyPassword =
                    releaseKeyPassword
            }
        }
    }
    defaultConfig {
        applicationId = "com.signaldesk.relay"

        val firebaseApplicationId =
            providers.gradleProperty("relayFirebaseApplicationId")
                .orElse(System.getenv("RELAY_FIREBASE_APPLICATION_ID") ?: "")
                .get()
        val firebaseProjectId =
            providers.gradleProperty("relayFirebaseProjectId")
                .orElse(System.getenv("RELAY_FIREBASE_PROJECT_ID") ?: "")
                .get()
        val firebaseApiKey =
            providers.gradleProperty("relayFirebaseApiKey")
                .orElse(System.getenv("RELAY_FIREBASE_API_KEY") ?: "")
                .get()
        val firebaseSenderId =
            providers.gradleProperty("relayFirebaseSenderId")
                .orElse(System.getenv("RELAY_FIREBASE_SENDER_ID") ?: "")
                .get()

        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"$firebaseApplicationId\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val relayHttpBaseUrl =
                providers.gradleProperty("relayHttpBaseUrl")
                    .orElse(
                        providers.environmentVariable(
                            "RELAY_HTTP_BASE_URL"
                        )
                    )
                    .orElse(
                        "http://127.0.0.1:9000"
                    )
                    .get()

            val relayWebSocketUrl =
                providers.gradleProperty("relayWebSocketUrl")
                    .orElse(
                        providers.environmentVariable(
                            "RELAY_WEBSOCKET_URL"
                        )
                    )
                    .orElse(
                        "ws://127.0.0.1:9000"
                    )
                    .get()

            buildConfigField(
                "String",
                "RELAY_HTTP_BASE_URL",
                "\"$relayHttpBaseUrl\""
            )

            buildConfigField(
                "String",
                "RELAY_WEBSOCKET_URL",
                "\"$relayWebSocketUrl\""
            )
        }

        release {

            if (
                releaseSigningConfigured
            ) {

                signingConfig =
                    signingConfigs
                        .getByName(
                            "release"
                        )
            }

            val relayHttpBaseUrl =
                providers.gradleProperty("relayHttpBaseUrl")
                    .orElse(
                        providers.environmentVariable(
                            "RELAY_HTTP_BASE_URL"
                        )
                    )
                    .orNull

            val relayWebSocketUrl =
                providers.gradleProperty("relayWebSocketUrl")
                    .orElse(
                        providers.environmentVariable(
                            "RELAY_WEBSOCKET_URL"
                        )
                    )
                    .orNull

            val releaseRequested =
                gradle.startParameter.taskNames.any {
                    it.contains(
                        "release",
                        ignoreCase = true
                    )
                }

            if (releaseRequested) {
                require(
                    !relayHttpBaseUrl.isNullOrBlank()
                ) {
                    "Release requires relayHttpBaseUrl or RELAY_HTTP_BASE_URL."
                }

                require(
                    !relayWebSocketUrl.isNullOrBlank()
                ) {
                    "Release requires relayWebSocketUrl or RELAY_WEBSOCKET_URL."
                }

                require(
                    !relayHttpBaseUrl.contains(
                        "127.0.0.1"
                    ) &&
                        !relayHttpBaseUrl.contains(
                            "localhost",
                            ignoreCase = true
                        )
                ) {
                    "Release HTTP endpoint must not use localhost."
                }

                require(
                    !relayWebSocketUrl.contains(
                        "127.0.0.1"
                    ) &&
                        !relayWebSocketUrl.contains(
                            "localhost",
                            ignoreCase = true
                        )
                ) {
                    "Release WebSocket endpoint must not use localhost."
                }
            }

            buildConfigField(
                "String",
                "RELAY_HTTP_BASE_URL",
                "\"${relayHttpBaseUrl.orEmpty()}\""
            )

            buildConfigField(
                "String",
                "RELAY_WEBSOCKET_URL",
                "\"${relayWebSocketUrl.orEmpty()}\""
            )

            isMinifyEnabled = true

            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:session"))
    implementation(project(":core:database"))
    implementation(project(":core:realtime"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:incidents"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.okhttp.core)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}






