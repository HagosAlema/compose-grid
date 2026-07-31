plugins {
    alias(libs.plugins.android.library)
    // Kotlin compilation is built into AGP 9+; no kotlin-android plugin here.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
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
    // `api`, not `implementation`: grid-core's public surface is made of
    // Compose types — GridColumn takes @Composable slots, GridStyle exposes
    // Color, DataGrid takes a Modifier. Publishing these as `implementation`
    // puts them in runtime scope in the POM, so a consumer that doesn't already
    // depend on compose-ui couldn't compile against our own API.
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
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

// Published to Maven Central via the Sonatype Central Portal. groupId and
// version come from the root build.gradle.kts so all artifacts stay in step.
//
// Signing and Sonatype credentials are read from the environment / Gradle
// properties and are never checked in — see RELEASING.md.
mavenPublishing {
    publishToMavenCentral()

    // Sign only when a key is actually configured. Maven Central rejects
    // unsigned artifacts and release.yml supplies the key, so real releases are
    // always signed — but requiring one unconditionally would make
    // `publishToMavenLocal` impossible to run without a GPG key, which is the
    // cheapest way to inspect what a release would contain. See RELEASING.md.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    coordinates(group.toString(), "grid-core", version.toString())

    pom {
        name.set("ComposeGrid Core")
        description.set("Virtualized data grid engine for Jetpack Compose: rendering, column model, and grid state. No Material3 or Paging dependency.")
        inceptionYear.set("2026")
        url.set("https://github.com/HagosAlema/compose-grid")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("HagosAlema")
                name.set("Hagos Alema")
                url.set("https://github.com/HagosAlema")
            }
        }
        scm {
            url.set("https://github.com/HagosAlema/compose-grid")
            connection.set("scm:git:git://github.com/HagosAlema/compose-grid.git")
            developerConnection.set("scm:git:ssh://git@github.com/HagosAlema/compose-grid.git")
        }
    }
}

// AGP 9 compiles Kotlin itself, so the Kotlin Gradle Plugin is never applied
// and Dokka has no source sets to auto-discover — it would emit an empty
// module page. Point it at the sources explicitly.
dokka {
    dokkaSourceSets.register("main") {
        sourceRoots.from(file("src/main/kotlin"))
        jdkVersion.set(17)
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/HagosAlema/compose-grid/tree/master/grid-core/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
