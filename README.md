# ComposeGrid

A performant, virtualized, feature-rich data table/grid for Jetpack Compose (Android).

> **Status: pre-alpha (M1 scaffolding).** The public API is still moving —
> expect breaking changes on every `0.x` release. See
> [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) for the full roadmap and
> current milestone.

## Why

Compose doesn't have a solid, actively maintained data grid. Every
large-screen/dashboard/admin Compose app either hand-rolls one or falls back
to a `WebView`. ComposeGrid aims to fix that with:

- **Real row virtualization** — only visible rows are composed, built on the
  same `LazyLayout` primitive that powers `LazyColumn`.
- **Column resizing and freezing** that work *with* Compose's layout system
  instead of fighting it.
- **A `LazyColumn`-shaped API** — columns are plain Kotlin objects with
  composable slots, so you keep full control of cell rendering.
- **Pluggable data sources** — plain `List<T>` today, Paging 3 support via
  the separate `grid-paging` artifact.

## Modules

| Module | Purpose |
|---|---|
| `grid-core` | Rendering engine, column model, `GridState`. No Material3 or Paging dependency. |
| `grid-paging` | Optional: `LazyPagingItems` → `GridDataSource` adapter. |
| `grid-material3` | Optional: Material3-themed defaults (colors, sort icons, selection styling). |
| `sample-app` | Demo Android app. |
| `benchmark` | Macrobenchmark suite for scroll/frame-timing regressions. |

## Quick start

```kotlin
val columns = listOf(
    GridColumn<Employee>(
        id = "name",
        header = { Text("Name") },
        width = GridColumnWidth.Fixed(160.dp),
        sortable = true,
        cell = { Text(it.name) },
    ),
    // ...more columns
)

DataGrid(
    columns = columns,
    dataSource = employees.asGridDataSource(),
    rowKey = { it.id },
)
```

See `sample-app/src/main/kotlin/io/github/composegrid/sample/MainActivity.kt`
for a complete runnable example.

## Building locally

This repo's Gradle wrapper jar (`gradle/wrapper/gradle-wrapper.jar`) is
intentionally **not** checked in from this scaffolding pass — it's a binary
that needs to be fetched from `services.gradle.org`, which wasn't reachable
from the sandbox this scaffold was generated in. Before your first build:

```bash
gradle wrapper --gradle-version 9.5.1   # requires a local Gradle install
```

This generates `gradlew`, `gradlew.bat`, and the wrapper jar from the
`gradle-wrapper.properties` already in this repo. After that, the usual
commands apply:

```bash
./gradlew build          # build all modules
./gradlew :sample-app:installDebug   # install the demo app
./gradlew test           # unit tests
```

You'll also need:
- **Android SDK Platform 37** installed, with a `local.properties` pointing
  at your SDK (or `ANDROID_HOME` set) — Android Studio generates this
  automatically on first open.
- **Android Studio Otter 3 Feature Drop or later** (required for AGP 9.x).
- **JDK 17.**

### On the AGP 9 toolchain

This project targets **AGP 9.3.0**, which made Kotlin compilation a built-in
part of the Android Gradle plugin — you'll notice there's no
`org.jetbrains.kotlin.android` plugin applied anywhere in this repo, and no
`kotlinOptions { jvmTarget = ... }` blocks. That's intentional, not an
oversight; see the comments in each `build.gradle.kts` for details. If you
hit a plugin incompatibility (some third-party Gradle plugins haven't caught
up yet), Google's migration guide covers the `android.builtInKotlin=false`
temporary opt-out: https://developer.android.com/build/migrate-to-built-in-kotlin

## Roadmap

See [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) for the full milestone
breakdown (M0–M8). Current milestone: **M1 — rendering core walking
skeleton** (vertical virtualization only; horizontal scroll, column
resizing, and freezing land in M2/M3).

## License

Apache 2.0 — see [`LICENSE`](./LICENSE).
