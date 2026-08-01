# Architecture

Why ComposeGrid is built the way it is. For build and test instructions see
[CONTRIBUTING.md](../CONTRIBUTING.md).

## Goal

A data grid for Jetpack Compose that handles the two things existing options get
wrong: real row virtualization at scale, and column freezing/resizing that
doesn't fight the Compose layout system.

Deferred past v1 by design: cell editing, grouping/pivoting, filtering UI,
multi-column sort, column reordering, export, and Compose Multiplatform.

## Data layer

Rendering is decoupled from where rows come from via `GridDataSource<T>`:

```kotlin
interface GridDataSource<T> {
    val itemCount: Int
    fun peek(index: Int): T?              // null when not yet loaded
    val loadState: GridLoadState
}
```

`ListGridDataSource` wraps a plain `List<T>`; `PagingGridDataSource` (in
`grid-paging`) bridges Paging 3's `LazyPagingItems`. The rendering core only ever
asks "what's at this index," and doesn't care whether that's already in memory or
triggers a fetch.

Splitting `grid-paging` into its own artifact keeps the Paging 3 dependency off
consumers who only need `List<T>`.

## Rendering core

Built directly on `LazyLayout` — the primitive behind `LazyColumn`/`LazyRow` —
rather than layered on top of `LazyColumn`, so rows *and* columns virtualize
jointly in a 2D viewport without double-measuring. Only cells whose row and
column both intersect the viewport are composed, measured, and placed.

A grid is up to three horizontal regions — `PinnedStart`, `Scrollable`,
`PinnedEnd` — each its own `LazyLayout` pair (header + body) sharing one
`GridState`. Pinned regions are exempt from horizontal scroll but share the
vertical scroll position, so they stay in lockstep. This is deliberately *not*
clipping tricks over a normal scrollable row.

Column widths resolve once per layout pass into a `GridColumnLayoutInfo`
(offsets + widths) that every region reads from, so resize and freeze can't
disagree about geometry.

Two consequences worth knowing:

- `LazyLayout` is backed by `SubcomposeLayout`, which doesn't automatically
  remeasure when state read during measurement changes. `GridState` captures a
  `Remeasurement` per region and calls `forceRemeasure()` on every scroll delta,
  mirroring what Compose's own `LazyListState` does.
- Row geometry lives in `GridRowLayoutInfo`, which has two representations. The
  uniform one stores a single height and is O(1) and allocation-free at any row
  count; the variable one precomputes cumulative offsets, costing one float per
  row for an O(log n) binary search. Callers only pay for per-row heights if they
  ask for them.

## Sorting

`DataGrid` owns the sort *state* but never reorders data. The data belongs to the
`GridDataSource`, and a paged or network-backed source can only be ordered at the
source — so the grid reports header clicks and the caller decides.

For the in-memory case that would otherwise mean hand-rolling a `when` over
column ids at every call site, so `sortedByGridState` / `rememberSortedGridDataSource`
do it instead, driven by `GridColumn.comparator`. A column that is `sortable`
with no comparator is the deliberate signal for "the backend orders this."

## Styling

`grid-core` takes a design-system-agnostic `GridStyle` of plain `Color`s so it
stays usable standalone; `grid-material3` builds one from Material3 tokens. The
composable slots (`sortIndicator`, `resizeHandle`) let consumers replace
affordances without touching internals, while the grid keeps ownership of
behaviour — the resize handle's touch target, drag gesture, and system
gesture-exclusion bookkeeping are not delegated, so a custom handle can't
accidentally break back-swipe protection.


## Tech stack

Kotlin 2.2.x compiled by AGP 9's built-in Kotlin support (no `kotlin-android`
plugin), Compose Compiler via the Kotlin Compose plugin, Gradle 9.5.1 with
version catalogs. minSdk 24, compile/target SDK 37. Apache 2.0. Published to
Maven Central via the Sonatype Central Portal using
`com.vanniktech.maven.publish`.
