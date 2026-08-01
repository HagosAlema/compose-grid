# Contributing

## Requirements

- **JDK 17**
- **Android SDK Platform 37**
- **Android Studio Otter 3 Feature Drop or later** (needed for AGP 9.x)

## Common commands

```bash
./gradlew build                                  # everything
./gradlew test                                   # unit tests
./gradlew :grid-core:connectedDebugAndroidTest   # instrumented tests (needs a device)
./gradlew :sample-app:installDebug               # install the demo
./gradlew dokkaGeneratePublicationHtml           # API docs -> build/dokka/html
```

CI runs lint, unit tests, assemble, the instrumented tests, and a docs build on
every PR.

## Benchmarks

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Run these on a **physical device**. Macrobenchmark deliberately refuses to run on
an emulator, where the host scheduler and absent thermal behaviour swamp the
signal. To smoke-test the harness itself — not to get real numbers:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pcomposegrid.benchmark.suppressErrors=EMULATOR
```

## Gotchas worth knowing before you lose an afternoon

### Focus assertions need keyboard input mode

Android starts in `InputMode.Touch`, and the focus target inside
`Modifier.clickable` — which is what the grid's cells use — **declines focus in
touch mode**. `requestFocus()` doesn't throw; the node simply never becomes
focused and every assertion downstream fails for no visible reason.

Use `setContentWithKeyboardInputMode` from `FocusTestSupport.kt` for anything
focus-related. Two further traps:

- A bare `Modifier.focusable()` is **not** input-mode gated, so a control test
  built on it will "prove" the harness works while `clickable` still fails.
- The input mode is window-global and leaks between tests in the shared
  instrumentation process, so any assertion about *touch*-mode behaviour is
  order-dependent and shouldn't be written.

`FocusHarnessInvestigationTest` documents this and guards the one deterministic
part.

### AGP 9 compiles Kotlin itself

There's no `org.jetbrains.kotlin.android` plugin anywhere and no
`kotlinOptions { jvmTarget = ... }` blocks. That's intentional.

The consequence: tooling that discovers source sets *through* the Kotlin Gradle
Plugin finds none, because KGP is never applied. Dokka hit this and produced
empty module pages until each library module was given explicit `sourceRoots`.
Google's Compose Preview Screenshot Testing plugin hits it too and has no
equivalent escape hatch — see [docs/ROADMAP.md](docs/ROADMAP.md).

If you hit a third-party plugin incompatibility, Google's migration guide covers
the `android.builtInKotlin=false` opt-out:
https://developer.android.com/build/migrate-to-built-in-kotlin

### Keep `GridStyle` in a stable reference

`GridStyle`'s composable slots compare by reference, and `DataGrid` keys its
internal item providers on the style instance. A style rebuilt inline on every
recomposition throws those providers away each time. `GridStyle.Default` and
`GridDefaults.style()` are both safe; wrap hand-built ones in `remember`.

## Project layout

| Module | Purpose |
|---|---|
| `grid-core` | Rendering engine, column model, `GridState`, sorting helpers |
| `grid-material3` | Material3-token defaults |
| `grid-paging` | Paging 3 adapter |
| `sample-app` | Demo app covering every feature |
| `benchmark` | Macrobenchmark suite |

Design rationale is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); planned work
is in [docs/ROADMAP.md](docs/ROADMAP.md).
