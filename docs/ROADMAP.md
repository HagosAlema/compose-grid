# Roadmap

Known gaps and planned work. Shipped changes are in
[CHANGELOG.md](../CHANGELOG.md).

## Planned

### ~~Screenshot testing~~ — done

Roborazzi now pins the grid's rendered appearance across light/dark, sort states,
selection, and the opt-in chevron handle. Runs on the JVM under Robolectric and
is verified by an ordinary `./gradlew test`. See
[CONTRIBUTING.md](../CONTRIBUTING.md#screenshot-tests).

Two things were needed to get there, both worth remembering:

- **Roborazzi's Gradle plugin doesn't work under AGP 9** — it requires the legacy
  `TestedExtension`, which AGP 9 removed. The plugin only registers tasks that
  set system properties, so those are wired by hand and everything else works.
- **Google's first-party Compose Preview Screenshot Testing plugin was tried
  first and abandoned.** It wires up and compiles, but preview discovery finds
  nothing. Ruled out: private vs public previews, top-level vs class-wrapped,
  library vs application module. Almost certainly the same root cause — AGP 9
  compiles Kotlin itself, so the Kotlin Gradle Plugin is never applied and
  tooling hooking into it comes up empty. Dokka hit this too and had an escape
  hatch in explicit `sourceRoots`; preview discovery exposes none.

### ~~Resize handle touch target~~ — done

The grab area is now 48dp, Material's minimum, measured inward from the column
boundary and capped at half the column so a narrow column keeps header surface
that isn't resize surface. Taps fall through to the sort click either way, so
widening cost nothing there.

Fixing this surfaced a separate bug: the resize indicator was centred inside the
grab area, so it rendered ~12dp *inside* the boundary it moves rather than on it.
Measured off a screenshot — the column ended at 360dp, the line drew at 349dp.
It now aligns to the trailing edge.

### Variable row height

Rows share a uniform height today. Supporting per-row heights means replacing
`visibleRowRange`'s O(1) arithmetic with something that tracks cumulative
offsets, and deciding how that interacts with the placeholder path for
not-yet-loaded paged rows.

## Deferred past v1 by design

Cell editing, grouping/pivoting, filtering UI, multi-column sort, column
reordering, export, and Compose Multiplatform. All are natural v2+ candidates,
excluded so v1 could ship lean.

## Watch-items

- **2D virtualization on `LazyLayout`** is the highest-risk piece and remains
  under-documented territory upstream.
- **Frozen-column scroll sync** is a common source of jank in grid libraries
  across every framework. Verify on real devices, not just an emulator.
- **Paging adapter scope creep** — easy to rabbit-hole into supporting every
  Paging 3 feature. `grid-paging` is deliberately thin.
