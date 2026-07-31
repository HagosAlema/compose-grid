# Changelog

Notable changes to ComposeGrid. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/). While on `0.x`, minor bumps may break API.

## [Unreleased]

Nothing yet.

## [0.1.0] — unreleased

First release candidate. Everything in the v1 scope
([`DEVELOPMENT_PLAN.md`](./DEVELOPMENT_PLAN.md) §2) is implemented, tested, and
demonstrated in `sample-app`.

### Added

**Rendering core (`grid-core`)**

- `DataGrid` — joint row *and* column virtualization on a custom `LazyLayout`.
  Only cells whose row and column both intersect the viewport are composed,
  measured, and placed.
- `GridColumn` with `Fixed` / `Weighted` / `Range` sizing, per-column
  `sortable`, `comparator`, `pinned`, and composable `header`/`cell` slots.
- Column freezing via `ColumnPin.Start` / `ColumnPin.End`. Pinned regions are
  separate `LazyLayout` passes sharing one vertical scroll state, so they stay
  aligned with the scrollable region without clipping tricks.
- Column resizing by dragging a `Range` column's header edge, clamped to the
  declared bounds. Handles register `systemGestureExclusionRects` (API 29+) so
  a drag near the screen edge isn't stolen by the back gesture.
- Single-column sorting: header click cycles ascending → descending → none.
- Row selection via `GridState.selectedRowKeys` / `toggleSelection` /
  `clearSelection`.
- `GridDataSource` abstraction with `ListGridDataSource` and
  `List<T>.asGridDataSource()`.
- `sortedByGridState()` and `rememberSortedGridDataSource()` for in-memory
  sorting driven by `GridColumn.comparator`.
- `GridStyle` — design-system-agnostic styling (plain `Color`s, no Material
  dependency) with composable `sortIndicator` and `resizeHandle` slots, a
  `cellPadding` inset, and `sortIndicatorPosition` for placing the sort arrow
  before or after the header label (`Trailing` by default; `Leading` suits
  right-aligned numeric columns).

**Theming (`grid-material3`)**

- `GridDefaults.colors()` / `GridDefaults.style()` building a `GridStyle` from
  Material3 tokens, plus `GridColors.toGridStyle()` for token-level tweaks.
- `Material3SortIndicator` and the opt-in `Material3ResizeHandle`, which draws
  chevrons while hovered or dragged and omits one at each width bound. Both are
  Canvas/text-drawn — no `material-icons-extended` dependency.

**Paging (`grid-paging`)**

- `PagingGridDataSource` / `LazyPagingItems.asGridDataSource()` bridging
  Paging 3, with load state surfaced through `GridLoadState` for placeholder
  cells.

**Accessibility**

- `CollectionInfo` / `CollectionItemInfo` on the body and its cells, with column
  indices correct across the pinned/scrollable split.
- `Role.Button` and a sort-direction `stateDescription` on sortable headers.
- `selected` on selected rows.
- Scroll semantics (`scrollBy` plus horizontal/vertical scroll-axis ranges), so
  the grid is a real scroll container to accessibility services rather than only
  being touch-scrollable.
- "Increase column width" / "Decrease column width" custom actions, making
  resizing operable without a drag gesture.
- Focusable cells with a themed focus ring and arrow-key focus movement.

**Tooling**

- Macrobenchmark suite covering vertical scroll, horizontal scroll, and startup.
- Dokka API reference aggregating the three library modules.
- GitHub Actions CI: lint, unit tests, assemble, instrumented tests, and a docs
  build.

### Known limitations

- Rows share a single uniform `rowHeight`; variable per-row height is out of v1
  scope.
- Arrow-key navigation only reaches currently-composed cells — it does not
  scroll an off-screen row or column into view.
- `Material3ResizeHandle`'s chevrons overlap the sort indicator while active on
  a column that is both sortable and resizable.

[Unreleased]: https://github.com/HagosAlema/compose-grid/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/HagosAlema/compose-grid/releases/tag/v0.1.0
