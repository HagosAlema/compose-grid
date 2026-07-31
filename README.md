# ComposeGrid

A performant, virtualized, feature-rich data table/grid for Jetpack Compose (Android).

> **Status: `0.1.0`, pre-release.** Every feature in the v1 scope is implemented
> and tested, but the public API has not been frozen — expect breaking changes
> on `0.x` releases. See [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) for the
> roadmap and [`CHANGELOG.md`](./CHANGELOG.md) for what has landed.

## Why

Compose doesn't have a solid, actively maintained data grid. Every
large-screen/dashboard/admin Compose app either hand-rolls one or falls back to
a `WebView`. ComposeGrid aims to fix that with:

- **Real 2D virtualization** — only cells whose row *and* column intersect the
  viewport are composed, built directly on `LazyLayout` (the primitive behind
  `LazyColumn`) rather than layered on top of it.
- **Column resizing and freezing** that work *with* Compose's layout system
  instead of fighting it. Pinned columns are a separate `LazyLayout` pass
  sharing one scroll state, not a clipping trick.
- **A `LazyColumn`-shaped API** — columns are plain Kotlin objects with
  composable slots, so you keep full control of cell rendering.
- **Pluggable data sources** — plain `List<T>`, or Paging 3 via the separate
  `grid-paging` artifact.
- **Accessibility that isn't an afterthought** — row/column semantics, sort
  state announcements, a scrollable container TalkBack can actually page
  through, and column resizing that works without a drag gesture.

## Modules

| Module | Purpose |
|---|---|
| `grid-core` | Rendering engine, column model, `GridState`, sorting helpers. No Material3 or Paging dependency. |
| `grid-material3` | Optional: Material3-token defaults — colors, sort indicator, resize handle. |
| `grid-paging` | Optional: `LazyPagingItems` → `GridDataSource` adapter. |
| `sample-app` | Demo app covering every feature. |
| `benchmark` | Macrobenchmark suite for scroll and startup frame timing. |

`grid-core` is usable on its own — it deliberately depends on neither Material3
nor Paging, so you can bring your own design system.

## Installation

Not yet published to Maven Central; see [`RELEASING.md`](./RELEASING.md) for the
remaining steps. Once released:

```kotlin
dependencies {
    implementation("io.github.hagosalema:grid-core:0.1.0")
    // optional
    implementation("io.github.hagosalema:grid-material3:0.1.0")
    implementation("io.github.hagosalema:grid-paging:0.1.0")
}
```

The `groupId` is `io.github.hagosalema` while the Kotlin packages are
`io.github.composegrid`. That's deliberate: the groupId has to match a namespace
verifiable against a GitHub account, and the two don't need to agree.

## Quick start

```kotlin
val columns = remember {
    listOf(
        GridColumn<Employee>(
            id = "name",
            header = { Text("Name") },
            width = GridColumnWidth.Range(min = 80.dp, max = 200.dp, initial = 140.dp),
            sortable = true,
            comparator = compareBy { it.name },
            pinned = ColumnPin.Start,
            cell = { Text(it.name) },
        ),
        GridColumn<Employee>(
            id = "department",
            header = { Text("Department") },
            width = GridColumnWidth.Fixed(140.dp),
            sortable = true,
            comparator = compareBy { it.department },
            cell = { Text(it.department) },
        ),
    )
}
val gridState = rememberGridState()

DataGrid(
    columns = columns,
    dataSource = rememberSortedGridDataSource(employees, columns, gridState),
    state = gridState,
    style = GridDefaults.style(), // from grid-material3
    rowKey = { it.id },
)
```

Hold `columns` in a `remember`: `DataGrid` keys internal item providers on it.

## Guides

### Column sizing

| `GridColumnWidth` | Behaviour |
|---|---|
| `Fixed(dp)` | Exact width, ignored by weighted distribution. |
| `Weighted(f)` | Splits leftover space proportionally among weighted columns. |
| `Range(min, max, initial)` | User-resizable by dragging the header edge, clamped to the range. |

### Column freezing

Set `pinned = ColumnPin.Start` or `ColumnPin.End`. Pinned columns are exempt
from horizontal scroll but share the vertical scroll state, so they stay in
lockstep with the scrollable region. Any number of columns can be pinned to
either edge.

### Sorting

`DataGrid` owns the sort *state* but never reorders your data. The data belongs
to the `GridDataSource`, and a paged or network-backed source can only be
ordered at the source — so the grid reports the click and you decide what
happens.

**In-memory**: give each sortable column a `comparator` and wrap your list:

```kotlin
val dataSource = rememberSortedGridDataSource(items, columns, gridState)
```

**Server-side**: leave `comparator` null, keep `sortable = true`, and react to
the change. A column being `sortable` with no comparator is the deliberate
signal for "the backend orders this."

```kotlin
DataGrid(
    columns = columns,
    dataSource = pagingItems.asGridDataSource(),
    state = gridState,
    onSortChange = { column, direction -> viewModel.reload(column.id, direction) },
)
```

Both patterns are demonstrated in the sample app's two tabs. Clicking a header
cycles ascending → descending → none.

### Selection

`GridState` exposes `selectedRowKeys`, `toggleSelection(key)`, and
`clearSelection()`. Tapping a cell toggles its row. Hoist the state above the
grid to drive an external "N selected" toolbar:

```kotlin
val gridState = rememberGridState()
Text("${gridState.selectedRowKeys.size} selected")
DataGrid(state = gridState, /* ... */)
```

### Theming

`grid-core` takes a design-system-agnostic `GridStyle` of plain `Color`s.
`grid-material3` builds one from Material3 tokens:

```kotlin
val style = GridDefaults.style()                       // ready-made
val style = GridDefaults.colors()
    .copy(selectedRowBackground = MaterialTheme.colorScheme.tertiaryContainer)
    .toGridStyle()                                     // tweak the tokens
```

`GridStyle` carries two composable slots — `sortIndicator` and `resizeHandle` —
so you can replace those affordances without touching grid internals. An
icon-style resize handle ships as an opt-in:

```kotlin
colors.toGridStyle(resizeHandle = { Material3ResizeHandle(it) })
```

`sortIndicatorPosition` moves the sort arrow to either side of the header label.
It defaults to `Trailing`, which is what most grids do for left-aligned text;
`Leading` is the conventional choice for right-aligned numeric columns:

```kotlin
colors.toGridStyle(sortIndicatorPosition = SortIndicatorPosition.Leading)
```

One caveat with `Leading`: since an indicator normally draws nothing for
`SortDirection.None`, the label shifts sideways when sort toggles — on the very
element just clicked. Give your `sortIndicator` a fixed width in every direction
(a faint hint, or a transparent spacer for `None`) if you want the label to hold
still.

**Keep the style in a stable reference.** Its slots compare by reference and
`DataGrid` keys item providers on the instance, so a style rebuilt inline every
recomposition throws those providers away each time. `GridStyle.Default` and
`GridDefaults.style()` are both safe; wrap hand-built ones in `remember`.

### Accessibility

Supported out of the box:

- Rows and cells expose `CollectionInfo`/`CollectionItemInfo`, so TalkBack
  announces "row 3, column 2" style positions — correct even across the
  pinned/scrollable split.
- Sortable headers are `Role.Button` with a `stateDescription` of the current
  sort direction.
- Selected rows report `selected`.
- The body is a real scroll container (`scrollBy` plus scroll-axis ranges), so
  TalkBack can page through it and reach off-screen rows.
- Resizable columns expose "Increase/Decrease column width" custom actions,
  because dragging alone is not operable by TalkBack or switch access.
- Every cell is a focus target with a themed focus ring, and arrow keys move
  focus between cells — including across a scroll boundary. Focus search only
  sees composed cells, so when the next cell is scrolled out of view the grid
  scrolls one row or column that way and retries, which means a keyboard user
  can walk the whole dataset rather than stopping at the viewport edge.

Testing note for contributors: focus assertions need Compose in
`InputMode.Keyboard`. Android starts in `InputMode.Touch`, where the focus target
inside `Modifier.clickable` declines focus — `requestFocus()` doesn't throw, the
node just never becomes focused. Use `setContentWithKeyboardInputMode` from
`FocusTestSupport.kt` for anything focus-related; `FocusHarnessInvestigationTest`
documents the details.

## Building locally

```bash
./gradlew build                        # everything
./gradlew test                         # unit tests
./gradlew :grid-core:connectedDebugAndroidTest   # instrumented tests (needs a device)
./gradlew :sample-app:installDebug     # install the demo
./gradlew dokkaGeneratePublicationHtml # API docs -> build/dokka/html
```

Requirements: **JDK 17**, **Android SDK Platform 37**, and **Android Studio
Otter 3 Feature Drop or later** (needed for AGP 9.x).

### Benchmarks

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

Run these on a **physical device**. Macrobenchmark deliberately refuses to run
on an emulator, where the host scheduler and absent thermal behaviour swamp the
signal. To smoke-test the harness itself — not to get real numbers:

```bash
./gradlew :benchmark:connectedBenchmarkAndroidTest -Pcomposegrid.benchmark.suppressErrors=EMULATOR
```

### On the AGP 9 toolchain

This project targets **AGP 9.3.0**, which made Kotlin compilation part of the
Android Gradle plugin — there's no `org.jetbrains.kotlin.android` plugin applied
anywhere and no `kotlinOptions { jvmTarget = ... }` blocks. That's intentional.

One consequence worth knowing: tooling that discovers source sets *through* the
Kotlin Gradle Plugin finds none, because it isn't applied. Dokka is affected,
which is why each library module registers its source roots explicitly. If you
hit a plugin incompatibility, Google's migration guide covers the
`android.builtInKotlin=false` opt-out:
https://developer.android.com/build/migrate-to-built-in-kotlin

## Roadmap

See [`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md). M0–M7 are complete; M8 is
the Maven Central release.

Deferred past v1 by design: cell editing, grouping/pivoting, filtering UI,
multi-column sort, column reordering, export, and Compose Multiplatform.

## License

Apache 2.0 — see [`LICENSE`](./LICENSE).
