package io.github.composegrid.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll and startup frame-timing benchmarks for `DataGrid`, run against
 * `sample-app`.
 *
 * These exist to catch regressions in the thing the whole library is for:
 * 2D virtualization. A change that accidentally composes off-screen cells,
 * or rebuilds item providers on every frame, shows up here as jank long
 * before anyone notices it by hand.
 *
 * Run on a **physical device**:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 * Emulator numbers are not trustworthy — the host's scheduler and lack of
 * real thermal behaviour swamp the signal. The suite will run there, but
 * treat the output as a smoke test, not a measurement.
 */
@RunWith(AndroidJUnit4::class)
class GridScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollGridVertically() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        val grid = device.wait(Until.findObject(By.scrollable(true)), UI_TIMEOUT_MS)
            ?: error("No scrollable grid found in $TARGET_PACKAGE")
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            grid.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(3) {
            grid.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    @Test
    fun scrollGridHorizontally() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        // Horizontal scrolling is the path that also has to keep the pinned
        // regions and the header in sync, so it's the more failure-prone axis.
        val grid = device.wait(Until.findObject(By.scrollable(true)), UI_TIMEOUT_MS)
            ?: error("No scrollable grid found in $TARGET_PACKAGE")
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            grid.fling(Direction.RIGHT)
            device.waitForIdle()
        }
        repeat(3) {
            grid.fling(Direction.LEFT)
            device.waitForIdle()
        }
    }

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val TARGET_PACKAGE = "io.github.composegrid.sample"
        const val ITERATIONS = 5
        const val UI_TIMEOUT_MS = 5_000L
    }
}
