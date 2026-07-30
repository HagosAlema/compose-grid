plugins {
    alias(libs.plugins.android.library)
    // Kotlin compilation is built into AGP 9+; no kotlin-android plugin here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.composegrid.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // AGP's built-in Kotlin support derives the Kotlin JVM target from
        // these values — no separate kotlinOptions { jvmTarget = ... } block
        // needed (that DSL belonged to the now-removed kotlin-android plugin).
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    // Note: grid-core intentionally has no Material3 or Paging dependency.
    // Theming lives in grid-material3; paging support lives in grid-paging.
    // This keeps grid-core usable by consumers who want to bring their own
    // design system or data-loading strategy.

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
