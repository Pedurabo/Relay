plugins {
    alias(libs.plugins.ksp.plugin)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.signaldesk.relay"
    compileSdk = 37

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
        val relayHttpBaseUrl =
            providers.gradleProperty("relayHttpBaseUrl")
                .orElse(System.getenv("RELAY_HTTP_BASE_URL") ?: "http://127.0.0.1:9000")
                .get()

        val relayWebSocketUrl =
            providers.gradleProperty("relayWebSocketUrl")
                .orElse(System.getenv("RELAY_WEBSOCKET_URL") ?: "ws://127.0.0.1:9000")
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
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
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






