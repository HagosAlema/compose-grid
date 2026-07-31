# ComposeGrid — Development Plan

*A performant, virtualized, feature-rich data table/grid for Jetpack Compose (Android).*

> Name: **ComposeGrid** — settled, see §6.1.

---

## 1. Goals & Non-Goals

**Goal:** Ship a production-quality, well-documented, actively maintained data grid for
Jetpack Compose that handles the two things every existing option gets wrong: real
row virtualization at scale, and column freezing/resizing that doesn't fight the
Compose layout system.

**Non-goals for v1:** cell editing, grouping/pivoting, filtering UI, multi-column sort,
column reordering, export, Compose Multiplatform support. All are natural v2+ candidates
and are explicitly deferred so v1 ships lean and solid.

---

## 2. v1 Feature Set

| Feature | Notes |
|---|---|
| Column definitions | header, width (fixed/weighted), alignment, custom cell content |
| Sorting | single-column, click header to toggle asc/desc/none |
| Row virtualization | only compose/measure visible rows (built on `LazyLayout`) |
| Lazy/paged data sources | support both `List<T>` and a paging-style lazy source |
| Column resizing | drag column border, min/max width constraints |
| Column freezing | pin N columns left and/or right; they stay put during horizontal scroll |
| Row selection | single and multi-select, checkbox column support |
| Theming | Material3-token-driven defaults, fully overridable via a `GridColors`/`GridTypography`-style API |

---

## 3. Architecture

### 3.1 Data layer abstraction

To support both in-memory lists and paged/lazy sources under one API, we abstract over
a `GridDataSource<T>` interface rather than requiring `List<T>` directly:

```kotlin
interface GridDataSource<T> {
    val itemCount: Int // -1 or Int.MAX if unknown/streaming
    fun peek(index: Int): T?      // returns null if not yet loaded
    suspend fun load(index: Int): T
}
```

- `ListGridDataSource<T>` — trivial wrapper around `List<T>`, for the common case.
- `PagingGridDataSource<T>` — adapter over Paging 3's `LazyPagingItems<T>`, so we don't
  reinvent paging; we just bridge it into the grid's rendering loop.

This keeps the rendering/virtualization core agnostic to where data comes from — it
just asks for "item at index," and doesn't care if that's already in memory or triggers
a network fetch.

### 3.2 Rendering core

- Built directly on `LazyLayout` (the same primitive `LazyColumn`/`LazyRow` are built on),
  not layered on top of `LazyColumn`, so we can jointly virtualize rows *and* columns
  in a 2D viewport without double-measuring.
- A single `Grid` composable owns both scroll axes; frozen columns are implemented as a
  second `LazyLayout` pass sharing the vertical scroll state but exempt from horizontal
  scroll — not clipping tricks on top of a normal scrollable row.
- Column widths are resolved once per layout pass into a `GridColumnLayoutInfo`
  (offsets + widths), which both the frozen and scrollable regions read from, so resize
  and freeze stay consistent.

### 3.3 Public API sketch

```kotlin
@Composable
fun <T> DataGrid(
    columns: List<GridColumn<T>>,
    dataSource: GridDataSource<T>,
    state: GridState = rememberGridState(),
    modifier: Modifier = Modifier,
    onSortChange: (column: GridColumn<T>, direction: SortDirection) -> Unit = { _, _ -> },
    rowKey: (T) -> Any = { it.hashCode() },
)

class GridColumn<T>(
    val id: String,
    val header: @Composable () -> Unit,
    val width: GridColumnWidth,          // Fixed(dp) | Weighted(f) | Range(min, max)
    val sortable: Boolean = false,
    val pinned: ColumnPin = ColumnPin.None, // None | Start | End
    val cell: @Composable (T) -> Unit,
)

class GridState(
    val verticalScroll: ScrollState,
    val horizontalScroll: ScrollState,
    val selection: SnapshotStateList<Any>, // row keys
    // column widths, sort state, etc.
)
```

Design intent: columns are declared like `LazyColumn` items — plain Kotlin objects with
composable slots — so consumers get full control of cell rendering without us building
a mini templating system.

### 3.4 Module structure

```
composegrid/
  grid-core/        -- rendering engine, LazyLayout logic, GridState, column model
  grid-paging/       -- optional artifact: Paging 3 adapter (keeps core free of the paging dependency)
  grid-material3/     -- default Material3-themed styling, sort icons, selection checkboxes
  sample-app/         -- demo Android app showcasing features
  benchmark/          -- macrobenchmark module for scroll/frame timing regression tracking
```

Splitting `grid-paging` out as its own artifact means consumers who only need `List<T>`
don't pull in the Paging 3 dependency.

---

## 4. Tech Stack & Tooling

- **Language:** Kotlin 2.2.x, compiled via AGP 9's built-in Kotlin support
  (no separate `kotlin-android` plugin); Compose Compiler via the Kotlin
  Compose plugin.
- **Min SDK:** 24 (matches current Compose baseline norms; revisit before release)
- **Compile/target SDK:** 37 (Android 17), via AGP 9.3.0. Note: Google Play
  doesn't require apps to *target* API 37 until August 2027 — this project
  tracks current stable rather than the Play minimum, since a library should
  build cleanly against what consumers will eventually need.
- **Build:** Gradle 9.5.1 (required minimum for AGP 9.x) with version catalogs
  (`libs.versions.toml`)
- **Testing:** `compose-ui-test` for behavior, Macrobenchmark for scroll performance,
  screenshot testing (Paparazzi or Roborazzi) for visual regressions
- **CI:** GitHub Actions — build + unit/UI tests on PR, benchmark job on a schedule
- **Docs:** Dokka for API reference, MkDocs or just a solid README + `/docs` folder for guides
- **License:** Apache 2.0 (standard for Android/Kotlin OSS, permissive, patent grant)
- **Publishing:** Maven Central via the Sonatype Central Portal, using the
  `com.vanniktech.maven.publish` Gradle plugin (handles signing + publishing boilerplate)

---

## 5. Milestones

> **Status:** M0–M7 complete. M8 is the only milestone outstanding, and what
> remains of it is account-side setup that can't be done from the repo — see
> [`RELEASING.md`](./RELEASING.md).

**M0 — Project scaffolding** ✅ (small)
Repo, module structure, CI skeleton, license, README stub, sample app shell.

**M1 — Rendering core (walking skeleton)** ✅
Basic `DataGrid` rendering a `List<T>` with fixed-width columns, vertical virtualization
only (no horizontal scroll/freeze yet). Proves the `LazyLayout` approach works.

**M2 — Horizontal scroll + column resizing** ✅
Full 2D virtualization, drag-to-resize columns with min/max constraints.

**M3 — Column freezing/pinning** ✅
Left/right pinned columns, scroll-sync between frozen and scrollable regions.

**M4 — Sorting + row selection** ✅
Header click-to-sort, single/multi row selection with checkbox column.

**M5 — Paging integration** ✅
`grid-paging` artifact, `LazyPagingItems` adapter, loading/placeholder states.

**M6 — Theming & polish** ✅
`grid-material3` defaults, customization API pass, accessibility (TalkBack row/cell
semantics, keyboard navigation if feasible).

**M7 — Docs, samples, benchmarks** ✅
Full README, API docs via Dokka, sample app covering every feature, benchmark suite
green and tracked.

**M8 — Publish (shipping as `0.1.0`, not `1.0.0` — see §6.4)** ⏳
Maven Central release, GitHub release notes, announce.

*Suggested cadence: each milestone as its own PR/branch with a working sample-app demo
attached, so we can sanity-check the API feel before locking it in.*

---

## 6. Decisions (resolved)

1. **Library name — `ComposeGrid` / repo `compose-grid`.** Kept after checking
   for collisions: no Maven Central artifact matches `composegrid`, and no
   GitHub project uses the name. `compose-table` was considered and rejected —
   it's taken twice on GitHub (`windedge/compose-table`,
   `tinytinycn/compose-table`) and sits in a crowded field alongside
   `ComposeDataTable`, `composable-table`, `compose-data-table`, and
   `Jetpack-Compose-Tables`. "Table" is the more accurate category word, but
   uniqueness beat accuracy: the ambiguity with Compose's *layout* grids is
   fixable with a README tagline and GitHub topics, whereas the name collision
   is not. Keeping "grid" also keeps the `DataGrid` composable name coherent,
   which matches WPF/AG Grid/MUI convention for this component.
2. **groupId — `io.github.hagosalema`.** The reverse-DNS of
   `hagosalema.github.io`, mapping to the GitHub account the Sonatype namespace
   verifies against. `io.github.composegrid` was the original plan but could
   never verify: Sonatype grants an `io.github.<name>` namespace only to whoever
   controls that account, and no `composegrid` user or org exists. Kotlin
   packages stay `io.github.composegrid` — groupId and package name don't have
   to agree. A useful side effect: the groupId is independent of the project
   name, so any future rename wouldn't drag the coordinates with it.
3. **Repo — personal account (`HagosAlema/compose-grid`).** A dedicated org buys
   nothing until there are co-maintainers, and moving to one later is cheap
   (GitHub redirects the repo) *because* the groupId doesn't encode the owner.
4. **Versioning — SemVer, first release `0.1.0`.** Deliberately not `1.0.0` as
   originally sketched for M8. `1.0.0` is a commitment to the current public
   surface, and that surface moved substantially during M6/M7 — `GridStyle`'s
   composable slots, the sorting helpers, and `GridColumn.comparator` are all
   new and unexercised by real consumers. Better to live on `0.x` through some
   real usage first.

---

## 7. Remaining work

Outside the v1 scope but worth tracking:

- ~~`requestFocus()` from `compose-ui-test` doesn't grant focus here.~~
  **Resolved.** The cause was Compose's input mode: Android starts in
  `InputMode.Touch`, and the focus target inside `Modifier.clickable` declines
  focus there. `setContentWithKeyboardInputMode` (in `FocusTestSupport.kt`)
  switches to `InputMode.Keyboard` first, and the focus-dependent tests now run
  normally. Two traps worth remembering: a bare `Modifier.focusable()` is *not*
  input-mode gated, so a control test built on it falsely suggests the harness
  works; and the input mode is window-global and leaks between tests, so any
  assertion about touch-mode behaviour is order-dependent and shouldn't be
  written.
- **Resize handle touch target is 24dp wide**, under Material's 48dp minimum.
  Widening it would start swallowing header taps on narrow columns, so it needs
  a deliberate design decision rather than a bump.
- **Uniform row height only.** Variable per-row height was excluded from v1 and
  would need a different `visibleRowRange` strategy than the current O(1) math.
- **Sample app doesn't cover quite everything** M7 claimed: there's no
  `ColumnPin.End` demo, no visible selection UI driven by
  `GridState.selectedRowKeys`, and no demonstration of
  `SortIndicatorPosition.Leading`.
- **Screenshot testing** (Paparazzi or Roborazzi) is listed in §4 but not set
  up. Worth having before the API freezes, since theming is now a real surface.

---

## 8. Risks / Watch-items

- **2D virtualization on `LazyLayout` is the highest-risk technical piece.** It's
  under-documented territory; budget extra time in M1–M3 and be ready to fall back to a
  simpler (if less elegant) measuring approach if `LazyLayout` proves too restrictive.
- **Frozen-column scroll sync** is a common source of jank/visual tearing in every grid
  library across every framework — treat M3 as needing real device testing, not just
  emulator.
- **Paging adapter scope creep** — easy to rabbit-hole into supporting every Paging 3
  feature. Keep `grid-paging` deliberately thin for v1.
