# Feature: Jetpack Compose Migration (Modern UI)

**GitHub Issue:** TBD
**Supersedes:** [PR #221](https://github.com/williscool/CalendarNotification/pull/221) (Jan 2026 draft, never merged)

## Background

The native Android UI is still XML layouts plus `findViewById`, with state held directly in Activities and Fragments. There is no ViewBinding, ViewModel, StateFlow, or LiveData in main code.

`android_modernization.md` rated Compose as **Very Low** priority because "React Native already provides declarative UI." **That premise is wrong.** RN hosts only the sync screens (`MyReactActivity` → Home / settings / sync-debug). Every core screen (event list, event view, snooze, pre-actions, filters, settings) is native Kotlin, about 10.6k lines in `ui/` plus 66 layout XMLs (about 6.5k lines). Once this plan is accepted, that section of `android_modernization.md` should be corrected.

### What's changed since PR #221

PR #221 was a code-heavy draft. Most of its snippets are now stale, and the template discourages them anyway. Its incremental, leaf-first strategy still holds. What has drifted:

| PR #221 assumed | Today |
|---|---|
| Kotlin 1.9.22 + `composeOptions.kotlinCompilerExtensionVersion` | Kotlin **2.0.21**: use the `org.jetbrains.kotlin.plugin.compose` Gradle plugin at the Kotlin version, with no `composeOptions` |
| minSdk 23 | minSdk **24** |
| 2 filter bottom sheets | **4** (`TimeFilter`, `CalendarFilter`, `SnoozedUntilFilter`, `UpcomingTimeFilter`) |
| `MainActivityModern` 636 lines, `ViewEventActivityNoRecents` 945 | 856 and 975 |
| Introduce 3 ViewModels in the list phase | Deferred; see the decision below |
| Migrate `EditEventActivity` | Calendar Editor is **deprecated** (`deprecated_features.md`), so don't migrate it |
| Migrate settings screens | Out of scope; see Non-Goals |
| Feature flag per Compose/View path | Not used; see the decision below |

## Goal

Move the **modern UI** (bottom-nav main screen, its three tabs, filter sheets, and the event view, snooze-all, and pre-action screens) to Jetpack Compose, one screen at a time, with no user-visible regressions and the existing test suite kept green throughout. The main payoff is faster UI iteration: `@Preview`, no XML/ID plumbing, and declarative list state that makes features like multi-select and filter pills cheaper to build.

The first milestone is a **go/no-go pilot**. If Compose adds more friction than it removes (build, CI, test tooling, or APK size), we stop there with only one bottom sheet converted.

## Non-Goals

- **Legacy UI (`MainActivityLegacy`, `DismissedEventsActivity`)** — it's the rollback path for the modern UI and won't be converted. Deleting it is a separate decision.
- **Settings (`SettingsActivityX`, the 7 `PreferenceFragmentCompat` screens, about 1.6k lines of custom dialog `Preference`s)** — AndroidX Preference works, and rewriting settings in Compose means re-implementing persistence binding for no user benefit. Revisit only if Compose settings libraries mature or settings need a redesign.
- **`EditEventActivity`** (1313 lines) — deprecated with Calendar Editor; it's slated for deletion, not migration.
- **Quiet Hours UI** — deprecated.
- **`TestActivity`, `AboutActivity`, `PrivacyPolicyActivity`, `ReportABugActivity`, `CarModeActivity`, `CalendarsActivity`** — low traffic and low churn. They can be done opportunistically later; they aren't part of this program.
- **Navigation Compose** — the Navigation Component + Fragment host stays. Each tab Fragment gets a Compose body. Swapping the nav host is future work, and only worth it if Fragments become the friction point.
- **Introducing Hilt / ViewModels / coroutines app-wide** — separate items in `android_modernization.md`. This plan adds state holders only where a screen needs one (see below).
- **Redesign** — this is a port. Visual changes are limited to what Material3 components naturally impose (see the theming decision).
- **React Native screens** — unchanged.
- **Notifications / RemoteViews** — Compose can't render these, and they stay as they are.

## Key Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Migration shape | **Incremental, leaf-first, one screen per PR** | Each PR is reviewable and revertable, and the test suite stays meaningful |
| Interop direction | **`ComposeView` inside existing Fragments/Activities** | Keeps nav graph, intents, manifest, and `MainActivity` routing untouched |
| Kotlin/Compose wiring | **`org.jetbrains.kotlin.plugin.compose` @ `$kotlin_version` + Compose BOM** | Required on Kotlin 2.x; the BOM keeps artifacts aligned |
| Material version | **Compose Material3**, colors mapped from `colors.xml` / `values-night` | M2 Compose is in maintenance mode. Mapping existing colors keeps the look close |
| XML theme | **Unchanged** (`Theme.AppCompat.DayNight`) | Non-Compose screens and the legacy UI still need it, and `setDefaultNightMode` keeps working |
| Rollout safety | **No per-screen feature flags**; rollback = revert the screen's PR | Flags double the code per screen. The legacy UI toggle already exists as a coarse escape hatch |
| State management | **Stateless composables + existing host owns state**; add a screen state holder only when the host would otherwise grow | Avoids a ViewModel/DI rewrite riding along with every screen port |
| Lists | **Replace RecyclerView + adapter wholesale with `LazyColumn` per tab**, not `ComposeView` rows inside RecyclerView | Row-level interop keeps both systems' complexity (swipe, selection, undo) alive at once |
| Robolectric tests | **`createComposeRule` / `createAndroidComposeRule` under Robolectric** | Fast, and it matches the existing "Robolectric primary" policy |
| Instrumentation tests | **Ultron Compose (`ultron-compose`) at the same 2.3.1 version** | Keeps one test DSL and Allure reporting; validated in the pilot |
| Test selectors | **`Modifier.testTag("<old_view_id_name>")`** | Ports test assertions mechanically; tag names match the IDs tests already use |
| Pilot screen | **`TimeFilterBottomSheet`** (132 lines) | Smallest self-contained screen that still has real logic, a test, and a dark-mode surface |

## Current Architecture

### Modern UI surface (in scope)

| Area | Files | Lines | Notes |
|---|---|---|---|
| Main host | `MainActivityModern` | 856 | Toolbar, search, filter chips, bottom nav, `NavHostFragment` |
| Tabs | `ActiveEventsFragment` / `UpcomingEventsFragment` / `DismissedEventsFragment` | 497 / 320 / 298 | Implement `SearchableFragment` |
| Adapters | `EventListAdapter` / `DismissedEventListAdapter` | 680 / 358 | Swipe-to-dismiss, undo, multi-select (`EventListAdapterSelectionTest`) |
| Filter sheets | `TimeFilter`, `UpcomingTimeFilter`, `CalendarFilter`, `SnoozedUntilFilter` `BottomSheet`s | 132 / 146 / 283 / 386 | `BottomSheetDialogFragment`s |
| Filter model | `FilterState` | 341 | Plain class with Bundle serialization. **Already UI-agnostic, so reuse it as-is** |
| Event view | `ViewEventActivityNoRecents` (+ `ViewEventActivity` shim) | 975 | Snooze presets, custom/until dialogs, `TimeIntervalPickerController` (206) |
| Snooze all | `SnoozeAllActivity` | 685 | Shares snooze preset patterns with event view |
| Pre-actions | `PreActionActivity` | 545 | Pre-mute/snooze/dismiss for upcoming events |
| Custom view | `HorizontalSwipeAwareRefreshLayout` | 66 | Deleted once tabs are Compose (pull-to-refresh is built in) |

### Testability pattern to preserve

Hosts expose nullable companion-object providers (`ActiveEventsFragment` 474–480 for storage, filterState, and clock; similar ones in `UpcomingEventsFragment`, `DismissedEventsFragment`, `PreActionActivity`, `ViewEventActivityNoRecents`, `MainActivityBase`). **The Compose port must not move data access into composables.** Composables take plain data plus callbacks, and the host keeps resolving storage and clock through these providers. Existing test fixtures (`UITestFixture`, `UITestFixtureRobolectric`) then keep working unchanged.

### Tests that pin current behavior

- Robolectric: `test/.../ui/` has 14 files, about 5.9k lines (`*RobolectricTest` per screen, `FilterStateTest`, `ViewEventActivityStateTest`, `TimeIntervalPickerControllerTest`, ...)
- Instrumentation (Ultron): `androidTest/.../ui/` has 11 files, about 2.9k lines, with about 121 `withId(...)` matchers. `MainActivityModernTest` alone is 811 lines.

These are the safety net. A screen is "done" when its existing tests pass against the Compose version, with selectors swapped and **assertions unchanged**.

## Design Decisions

### Why `LazyColumn` per tab instead of Compose rows in RecyclerView

PR #221 proposed `ComposeView` as RecyclerView items as an intermediate step. The event list's hard parts are the swipe gesture (`ItemTouchHelper`), the undo row state, multi-select, and pull-to-refresh interplay (`HorizontalSwipeAwareRefreshLayout` exists to stop horizontal swipes triggering refresh). With Compose rows inside RecyclerView, all of those stay View-side while rendering moves to Compose, so we'd pay the cost twice. Porting a whole tab at once is the same amount of work done once, and removes the custom refresh layout.

To keep each PR small, the **event row composable lands first** (with previews and Robolectric tests, used nowhere yet). Then each tab adopts it in its own PR, starting with Dismissed, which has the fewest interactions.

### State: host-owned first, state holder when needed

Each port starts with the host (Fragment/Activity) holding state exactly where it does today and passing it into `setContent { }` as Compose `mutableStateOf` fields. That keeps the diff close to "swap the view layer."

A screen gets a small plain-Kotlin state holder (not necessarily an AndroidX `ViewModel`) only when the host would otherwise grow. `ViewEventActivityNoRecents` probably needs one: its dialog chain (custom snooze → snooze until → date → time) is best expressed as a sealed state. If a state holder must survive configuration changes, it becomes a `ViewModel` built with a factory that takes the same providers. **No Hilt.**

### Theming

`CNPlusTheme` (Material3) builds `lightColorScheme` / `darkColorScheme` from the existing `colors.xml` / `values-night/colors.xml` values, read with `colorResource` so there's one source of truth. Dark mode follows `isSystemInDarkTheme()`, which respects `AppCompatDelegate.setDefaultNightMode` because that drives the Configuration's `uiMode`. **Verify this in the pilot when the in-app theme setting differs from the system theme.** Typography uses M3 defaults unless a screen visibly regresses.

`ThemeModule` (the RN theme bridge) is unaffected.

### Dialogs and bottom sheets

- `BottomSheetDialogFragment` → M3 `ModalBottomSheet`, hosted from the current owner. Sheets with results (`CalendarFilter`, `SnoozedUntilFilter`) return through a callback rather than fragment result APIs.
- Inline `AlertDialog`s → Compose `AlertDialog` as each owning screen is ported, not before.
- Process death: sheet visibility and in-progress selections use `rememberSaveable`. `FilterState` already has Bundle serialization to reuse.

### Rollout and rollback

No per-screen flags. Each screen is one PR that swaps the implementation and ports its tests. If a regression ships, revert that PR. The **legacy UI toggle** (`useNewNavigationUI`) remains as a user-level escape hatch for the main list throughout. This means the legacy UI can't be deleted until this program finishes, so add a note to that effect wherever its removal gets planned.

### Build/CI risks to clear in the pilot

- **KSP version mismatch:** KSP `2.1.20-1.0.31` against Kotlin `2.0.21` already works for Room, but adding the Compose compiler plugin is another Kotlin-version-coupled piece. Confirm the build, or align KSP to a `2.0.21-*` release first, as its own change.
- **React Native Gradle plugin coexistence** with `buildFeatures.compose`.
- **Robolectric + Compose:** needs `ui-test-manifest` on the test classpath, and the `@Config` SDK must be supported by Compose.
- **Ultron Compose under `UltronAllureTestRunner`**, Espresso pinned at 3.4.0. Check that `ultron-compose` 2.3.1 doesn't force an Espresso bump.
- **APK size:** `minifyEnabled false` today. Compose without R8 adds several MB. Measure the debug and release delta. If it's unacceptable, enabling R8 becomes its own prerequisite plan (it's not trivial with RN + Room + reflection).
- **CI time:** the extra compile cost under the current 8-shard emulator setup.

## Implementation Plan

Phases have names; refer to them by name, e.g. "the time filter pilot", not "Phase 1".

### Milestone 1 — Pilot (go/no-go)

#### Compose build wiring

- **Wiring:** add the compose compiler plugin, `buildFeatures.compose true`, and the BOM. Add `material3`, `ui-tooling-preview`, `activity-compose`, and `lifecycle-runtime-compose`. Add `ui-tooling` and `ui-test-manifest` as `debugImplementation`, `ui-test-junit4` for both `test` and `androidTest`, and `ultron-compose`.
- **Theme:** add `ui/compose/theme/CNPlusTheme.kt` with the color mapping.
- **Smoke tests:** one Robolectric test and one Ultron test that render a themed `Text`, proving both harnesses work. Run them locally (targeted) and in CI.
- **Baseline numbers:** record APK size before and after, plus CI duration, in this doc's Notes.

#### Time filter pilot

Port `TimeFilterBottomSheet` to a `TimeFilterSheet` composable shown from `MainActivityModern`. Port its existing Robolectric and Ultron assertions with selectors swapped. Check light, dark, and in-app-theme-override modes.

### Milestone 1 checkpoint

**Go/no-go review with the maintainer.** Go if both test harnesses are stable in CI, the APK delta is acceptable, and the sheet is indistinguishable in use. No-go means we revert the dependency additions (or keep the pilot if it's harmless) and record why in this doc.

### Milestone 2 — Filter sheets and event row

#### Remaining filter sheets

`UpcomingTimeFilterBottomSheet`, then `CalendarFilterBottomSheet` (search plus multi-select plus color dots), then `SnoozedUntilFilterBottomSheet`. One PR each, or the two small ones together.

#### Event row composable

`EventRow` for active and upcoming rows (color bar, title, time, snoozed-until, status icons) and `DismissedEventRow`. Both get `@Preview`s and Robolectric tests for formatting and state variants (muted, task, alarm, snoozed, pre-muted, selected). The rows aren't wired into any screen yet. They reuse existing formatters (`EventFormatter`); don't reimplement them.

### Milestone 2 checkpoint

All filter UI is Compose and the row component is proven in isolation. `MainActivityModern`'s chips are still Views at this point, which is fine.

### Milestone 3 — Event list tabs

#### Dismissed tab

`DismissedEventsFragment` → `ComposeView` hosting `LazyColumn` + `DismissedEventRow` + empty state + pull-to-refresh. Delete `DismissedEventListAdapter`. Keep the `SearchableFragment` contract.

#### Upcoming tab

Same pattern, plus the tap-through to `PreActionActivity`.

#### Active tab

The hardest list: swipe-to-dismiss with undo (`SwipeToDismissBox`), multi-select, and the snooze-all entry point. Delete `EventListAdapter` and `HorizontalSwipeAwareRefreshLayout`. Port `EventListAdapterSelectionTest` scenarios to the selection state holder this phase introduces.

### Milestone 3 checkpoint

All three tabs are Compose. Validate scroll performance on a long list (hundreds of events) on the CI emulator and a real device before continuing. This is a natural PR-merge and branch-reset point.

### Milestone 4 — Main scaffold

#### Main scaffold and filter chips

`MainActivityModern` content → a Compose `Scaffold` with top bar, search, filter chip row, and bottom `NavigationBar`. The tab bodies are still the Fragments from Milestone 3. Either keep `NavHostFragment` inside an `AndroidView`/`AndroidFragment`, or, if that interop proves awkward, switch tab switching to plain Compose state and drop the nav graph for this activity. **Decide this when starting the phase, by spiking both for an hour.** Collapse-on-scroll behavior must be preserved. `MainActivityModernTest` (811 lines) is the main safety net here.

### Milestone 5 — Event action screens

#### Pre-action screen

`PreActionActivity` (545). It has the simplest action UI, so it establishes the snooze-preset composables.

#### Snooze all screen

`SnoozeAllActivity` (685). Reuses the snooze-preset composables.

#### Event view screen

`ViewEventActivityNoRecents` (975). Introduce the dialog-chain state holder, replace `TimeIntervalPickerController` with a composable picker, and port `ViewEventActivityStateTest` and the related instrumentation tests. It's the largest single PR in the program, so it may split into "screen body" and "dialogs" PRs.

### Final checkpoint

Delete the dead XML layouts, menus, and drawables (verify with lint `UnusedResources`). Move this doc to `docs/dev_completed/`, add it to `docs/README.md`, and update the Compose section of `android_modernization.md`.

## Files to Modify/Create

### New Files

| File | Purpose |
|------|---------|
| `ui/compose/theme/CNPlusTheme.kt` | M3 theme with colors mapped from XML resources |
| `ui/compose/filters/*Sheet.kt` | The four filter sheets |
| `ui/compose/events/EventRow.kt`, `DismissedEventRow.kt` | List rows |
| `ui/compose/events/*TabContent.kt` | Per-tab list bodies |
| `ui/compose/main/MainScaffold.kt` | Main screen chrome |
| `ui/compose/snooze/*` | Snooze presets and interval picker, shared by event view, snooze all, and pre-action |
| `ui/compose/eventview/*` | Event view screen plus dialog-chain state |
| Test files mirroring each of the above | Robolectric compose tests |

(Package names are indicative; settle them in the build-wiring PR.)

### Modified Files

| File | Changes |
|------|---------|
| `android/build.gradle`, `android/app/build.gradle` | Compose plugin, BOM, deps; possibly KSP alignment |
| `MainActivityModern.kt` | Hosts Compose sheets, then the full scaffold |
| `Active/Upcoming/DismissedEventsFragment.kt` | Body becomes a `ComposeView` |
| `PreActionActivity.kt`, `SnoozeAllActivity.kt`, `ViewEventActivityNoRecents.kt` | `setContent` instead of `setContentView` |
| `androidTest/.../ui/*`, `test/.../ui/*`, both `UITestFixture`s | Selectors swapped to test tags; assertions unchanged |
| `docs/dev_todo/android_modernization.md` | Correct the "RN already covers this" assessment |

### Deleted (by end of program)

`*BottomSheet.kt` (4), `EventListAdapter.kt`, `DismissedEventListAdapter.kt`, `HorizontalSwipeAwareRefreshLayout.kt`, `TimeIntervalPickerController.kt`, and their layouts.

## Testing Plan

**Rule for every screen port:** existing tests are ported, not rewritten. Swap the selectors and leave the assertions alone. If an assertion has to change, call it out in the PR description, since it signals a behavior change.

### Unit Tests (Robolectric)

- **Harness smoke test:** a themed composable renders. Dark theme resolves the night colors when `setDefaultNightMode` forces dark.
- **Filter sheets:** each option sets the expected `FilterState` field. "All calendars" toggles select-all and select-none. Calendar search narrows the list and caps the result count as today. Sheet state survives recreation (`rememberSaveable`). Empty calendar list shows the empty state.
- **Event rows:** each status icon combination renders. Snoozed-until text uses `CNPlusTestClock`-driven formatting. Long titles truncate. A missing or zero calendar color falls back.
- **Tabs:** an empty list shows the empty state. Filter changes re-filter. Search via `SearchableFragment` filters. Swipe dismisses and shows undo, and undo restores. A dismiss that isn't undone persists after timeout (driven by the test clock, no sleeps). Multi-select enter/exit and select-all match `EventListAdapterSelectionTest` scenarios. Pull-to-refresh triggers a reload.
- **Event view state holder:** each dialog-chain transition. Cancel at each step returns to viewing. Snooze-until in the past is rejected as today.
- **Providers:** every ported host still resolves storage and clock through its companion providers. Fixtures inject them and the composable sees the injected data.

### Instrumentation Tests

Only where Robolectric can't validate it:

- Real swipe gestures on `SwipeToDismissBox` (Ultron Compose `swipeLeft`/`swipeRight`) and that horizontal swipe doesn't trigger pull-to-refresh.
- Bottom sheet show/dismiss with real window insets (nav bar overlap).
- `MainActivityModernTest` and `ViewEventActivityTest` ported end-to-end. Intents to calendar apps and notification-tap entry still land on the right screen.
- Scroll performance sanity on a long list (Milestone 3 checkpoint, manual plus a basic frame-time check if cheap).

## Future Enhancements

1. **Navigation Compose** for the modern main screen, if the Fragment host becomes friction after the main scaffold phase.
2. **Screenshot tests** (Roborazzi runs on Robolectric) for rows and sheets. These are cheap once composables exist and catch theme regressions.
3. **Settings in Compose**, only alongside a settings redesign.
4. **Delete the legacy UI**, which becomes possible once this program finishes and the modern UI has a release or two of soak time.
5. **R8/minification**, if the pilot shows Compose bloats the APK. It's a prerequisite only if the pilot says so.

## Notes

- **Port, don't redesign.** Changing behavior and the view layer in the same PR makes test failures ambiguous.
- **Composables never touch storage, `Settings`, or the clock directly.** Hosts pass data in. This keeps the DI-provider pattern and `CNPlusClockInterface` discipline intact.
- **No `System.currentTimeMillis()` in composables.** Time-dependent text takes `now` or a formatter built from the injected clock.
- **No broad `Exception` catches** in new UI code. **No sleeps** in Compose tests: use `waitUntil` / idling, and advance `mainClock` for animations.
- `ViewEventActivity` (the 21-line shim subclass) must keep working. Notification intents target it.
- Baseline measurements (filled in during the pilot): APK size before/after: _TBD_; CI duration before/after: _TBD_.

## Related Work

- `docs/dev_todo/android_modernization.md` — the Compose section to correct; also covers coroutines and Hilt, which this plan deliberately doesn't pull in
- `docs/dev_todo/main_activity_split.md` — created the `MainActivityModern` seam this plan builds on
- `docs/dev_todo/events_view_lookahead.md` — origin of the modern UI, tabs, and filter pills
- `docs/dev_todo/deprecated_features.md` — why `EditEventActivity` and Quiet Hours are excluded
- `docs/dev_todo/multi_select_batch_operations.md` — multi-select behavior the Active tab port must preserve
- `docs/testing/dependency_injection_patterns.md` — the provider pattern hosts must keep
- `docs/dev_completed/constructor-mocking-android.md` — MockK limits; another reason composables take plain data
- `docs/architecture/clock_implementation.md`
