plugins {
    // Macrobenchmarks drive a *separate* app process and measure it from the
    // outside, so this is com.android.test rather than com.android.library —
    // the module produces only a test APK, with sample-app as the app under
    // test (see targetProjectPath below).
    alias(libs.plugins.android.test)
}

android {
    namespace = "io.github.composegrid.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 28 // Macrobenchmark requires API 28+
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Macrobenchmark hard-fails on an emulator, a debuggable target, and
        // similar accuracy hazards. That default is correct and stays on: real
        // numbers must come from a physical device. This only lets someone
        // *explicitly* downgrade a named check to a warning to smoke-test the
        // harness itself, e.g.
        //   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
        //     -Pcomposegrid.benchmark.suppressErrors=EMULATOR
        // Results from such a run are not measurements — see the class KDoc.
        providers.gradleProperty("composegrid.benchmark.suppressErrors").orNull?.let {
            testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = it
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Macrobenchmark needs a non-debuggable, non-minified build to produce
    // numbers that mean anything. `debug` here matches sample-app's debug
    // signing so the harness can install both, while keeping the app under
    // test profileable.
    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":sample-app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.macrobenchmark)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
}

androidComponents {
    // Only the benchmark build type should produce a runnable variant; the
    // default debug/release ones would silently measure the wrong thing.
    beforeVariants(selector().all()) { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}
