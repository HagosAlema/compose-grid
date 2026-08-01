import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

android {
    namespace = "io.github.composegrid.paging"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":grid-core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    testImplementation(libs.junit)
}

// Published to Maven Central via the Sonatype Central Portal. groupId and
// version come from the root build.gradle.kts so all artifacts stay in step.
//
// Signing and Sonatype credentials are read from the environment / Gradle
// properties and are never checked in — see RELEASING.md.
mavenPublishing {
    // CENTRAL_PORTAL explicitly: with this plugin version a bare
    // publishToMavenCentral() targets the *legacy* OSSRH/Nexus service, which
    // fails for a Central Portal account with an opaque
    // "Cannot get stagingProfiles ... (402)" from createStagingRepository.
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

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

    coordinates(group.toString(), "grid-paging", version.toString())

    pom {
        name.set("ComposeGrid Paging")
        description.set("Paging 3 adapter for ComposeGrid: bridges LazyPagingItems into a GridDataSource.")
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
            remoteUrl("https://github.com/HagosAlema/compose-grid/tree/master/grid-paging/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
