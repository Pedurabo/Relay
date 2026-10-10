plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.signaldesk.relay.core.realtime"
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
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:session"))

    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.ktx)

    testImplementation(libs.junit)
}