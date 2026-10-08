plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.signaldesk.relay.core.notifications"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:session"))

    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}