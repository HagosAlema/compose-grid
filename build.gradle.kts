plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    // Kotlin compilation is built into AGP 9+ — no separate kotlin-android
    // plugin needed. kotlin-compose (the Compose compiler) is unaffected.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka)
}

// Single source of truth for the published coordinates. Every publishable
// module inherits these rather than repeating them, so cutting a release is a
// one-line change here — see RELEASING.md.
allprojects {
    // io.github.hagosalema is the reverse-DNS of hagosalema.github.io and maps
    // to the GitHub account the Sonatype namespace is verified against. The
    // Kotlin packages stay io.github.composegrid — groupId and package name
    // don't have to match, and renaming packages would break every import for
    // no benefit.
    group = "io.github.hagosalema"
    // Next release line. Note this carries no -SNAPSHOT suffix, so a local
    // `publishToMavenLocal` produces artifacts labelled 0.2.0 that are not the
    // released 0.2.0 — don't hand those to anyone.
    version = "0.2.0"
}

// Aggregate the three library modules into one API reference. sample-app and
// benchmark are deliberately excluded: they aren't published, so their
// internals aren't part of the public surface.
dependencies {
    dokka(project(":grid-core"))
    dokka(project(":grid-paging"))
    dokka(project(":grid-material3"))
}

dokka {
    moduleName.set("ComposeGrid")
}

// No hand-rolled `clean` task here: Dokka applies the `base` plugin, which
// already contributes one, and registering a second by that name fails the
// build.
