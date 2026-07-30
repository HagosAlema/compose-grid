plugins {
    alias(libs.plugins.android.library)
    // Kotlin compilation is built into AGP 9+; no kotlin-android plugin here.
}

android {
    namespace = "io.github.composegrid.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 28 // Macrobenchmark requires API 28+
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // NOTE: Macrobenchmark modules normally use com.android.test rather than
    // com.android.library, and target the sample-app as the app-under-test
    // via `targetProjectPath`. Left as android-library here as a scaffolding
    // placeholder — wire up properly in M7 per DEVELOPMENT_PLAN.md.
}

dependencies {
    androidTestImplementation(libs.macrobenchmark)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
