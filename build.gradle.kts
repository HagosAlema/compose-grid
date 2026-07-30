plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Kotlin compilation is built into AGP 9+ — no separate kotlin-android
    // plugin needed. kotlin-compose (the Compose compiler) is unaffected.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
