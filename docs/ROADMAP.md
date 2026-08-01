# Roadmap

Known gaps and planned work. Shipped changes are in
[CHANGELOG.md](../CHANGELOG.md).

## Planned

### Screenshot testing

Theming is a real public surface — colours, two composable slots, cell padding —
and no behavioural test covers how any of it renders. Worth having before the
API stabilises.

Google's first-party **Compose Preview Screenshot Testing** plugin
(`com.android.compose.screenshot` 0.0.1-alpha15) was tried and does not work
here. It wires up correctly — the `screenshotTest` source set compiles and the
`update`/`validate` tasks register — but preview discovery finds nothing:
*"test sources present … did not discover any tests to execute."*

Ruled out: `private` versus public previews, top-level functions versus previews
wrapped in a class, and library versus application module (a one-line probe
preview in `sample-app` fails identically). The likely cause is the same one that
broke Dokka: AGP 9 compiles Kotlin itself, so the Kotlin Gradle Plugin is never
applied and tooling hooking into it finds nothing. Dokka had an escape hatch in
explicit `sourceRoots`; preview discovery exposes none. The wiring was reverted
rather than left failing.

**Next to try: Roborazzi**, which captures from ordinary Robolectric JVM tests
via `captureRoboImage()` and doesn't depend on preview scanning. Expect to pin
`@Config(sdk = …)` below 37, since Robolectric needs a matching `android-all`
jar.

### Resize handle touch target

24dp wide, under Material's 48dp minimum. Widening it naively starts swallowing
header taps on narrow columns, so this needs a deliberate design decision rather
than a bump.

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
