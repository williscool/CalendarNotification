# Refactor: Test Consolidation (Non-UI)

**GitHub Issue:** [#174 - Test suite getting bloated](https://github.com/williscool/CalendarNotification/issues/174)

**Replaces:** [#205](https://github.com/williscool/CalendarNotification/pull/205) (Jan 2026 plan; its still-valid decisions are carried over below)

## Overview

Cut the test suite's CI time without losing coverage, by targeting where the **runtime** goes, not the line count. After the October 2026 CI performance pass, what's left on the critical paths is mostly real test execution. This plan covers the **non-UI** tests; UI tests are handled by the Compose migration (`compose_migration.md`, "CI Cost"), which rewrites the screens they cover.

## Background

#205 framed the problem well but measured size (lines, file count). For CI time, runtime is what matters, and the two don't line up: a 1,480-line Robolectric file can run in seconds while one emulator class takes a minute.

**Emulator tests (Oct 2026, run 37554726268):** ~14.6 min of test time across 8 shards: **~9.0 min UI (62%), ~5.6 min non-UI** across 37 classes. The non-UI time is concentrated:

| Class | Time | Tests | Per test |
|---|---|---|---|
| `calendar.CalendarProviderBasicTest` | 1.1 min | 7 | **9.1 s** |
| `calendar.CalendarProviderEventTest` | 0.7 min | 8 | 5.3 s |
| `calendar.CalendarProviderReminderTest` | 0.7 min | 8 | 5.0 s |
| `dismissedeventsstorage.EventDismissTest` | 0.6 min | 16 | 2.4 s |
| `app.CalendarReloadManagerTest` | 0.4 min | 11 | 2.1 s |
| `calendarmonitor.FixturedCalendarMonitorServiceTest` | 0.3 min | 5 | 4.1 s |
| `deprecated_raw_calendarmonitor.CalendarMonitorServiceTest` | 0.3 min | 4 | 4.4 s |

Most emulator tests take under 1 s. The `CalendarProvider*` classes take 5-9 s **per test** without any sleeps, so the cost is per-test setup and cleanup. Separately, the 32 tests that build a `BaseCalendarTestFixture` spend a median 2.3 s (p90 8 s) in fixture setup, ~2 min in total, mostly creating MockK mocks.

**Unit tests (Robolectric):** ~7.2 min of test-class time over 3 shards, each of which pays ~5 min of fixed setup first. Their slowest non-UI class is `ApplicationControllerCoreRobolectricTest` (~1 min). The required **CI Result** check waits on the slowest shard, so trimming the heavy classes here shortens it directly.

### Decisions carried over from #205

- **Robolectric + instrumented pairs are intentional.** Robolectric gives fast PR feedback; instrumentation verifies real Android, and it's what release builds are gated on. Consolidate a pair only if both halves are truly identical in value.
- **SQLite/Room tests stay on instrumentation.** Robolectric's SQLite support is rudimentary and the project uses custom extensions (cr-sqlite). Storage breaking breaks everything.
- **`deprecated_raw_calendarmonitor/` stays** until the fixtures are as easy to follow as those tests. Making fixtures clearer is the prerequisite, not deleting them.
- **No coverage regression.** That's the success criterion, not lines removed.

## Plan

### Phase 1: Measure

Make test runtime visible per class for both suites, so every later change shows its effect.

- **A small script** (e.g. `scripts/test_runtime_report.py`) summarising per-class and per-test time from a run's artifacts: the shard Allure results (emulator) and the JUnit XMLs in `unit-test-results-and-coverage-*` (unit). The numbers in this doc were produced this way by hand.
- **Coverage baseline** from the CI coverage comment / `full-code-coverage-reports-*` artifact, per class. #205's January CSV baselines are stale; regenerate.

### Phase 2: Fix the expensive setups

Target per-test cost, which also helps every new test in those classes.

- **`CalendarProvider*` tests (5-9 s/test):** profile where setup and cleanup time goes, via timestamps in their logcat (`emulator-log-shard-N`): calendar creation and deletion in the real provider, fixture construction, permission checks. Likely fixes are setting up immutable state once per class (`@BeforeClass`) where tests don't mutate it, and cheaper cleanup (delete only what the test created).
- **`BaseCalendarTestFixture`:** mock creation dominates its ~2.3 s. Reuse mocks across a class where it's safe, and avoid building components a test never touches.

**Isolation first:** sharing setup across tests is how the cross-test leaks fixed in October (#285, #286) crept in. Share only immutable setup, and keep per-test cleanup of anything a test changes.

### Phase 3: Consolidate low-value tests

#205's Phase 1 and 2 ideas, applied where the runtime data says they pay off:

- **Remove tests of trivial logic** that can't catch a real bug (getters/setters, restating a constant).
- **Merge near-duplicate tests within a suite** with parameterised tests, e.g. formatter variants in `EventFormatterRobolectricTest`.
- **One setup, several assertions:** where tests repeat the same expensive setup to check one thing each, group them.
- **Cross-suite pairs:** only case by case, per the carried-over decision above.

#205's test-data helpers (`EventMother`, `MonitorAlertMother`, `AlarmManagerTestHelper` on its branch) are worth reviving if they make the consolidated tests clearer.

### Phase 4 (future): Fixture clarity

#205's Phase 3: document and simplify the fixtures until they're as readable as `deprecated_raw_calendarmonitor/`, then revisit those tests. Out of scope until Phases 1-3 land.

## Files Changed Summary

| File | Change |
|------|--------|
| `scripts/test_runtime_report.py` (new) | Per-class/per-test runtime from CI artifacts |
| `androidTest/.../calendar/CalendarProvider*Test.kt`, `testutils/CalendarProviderTestFixture.kt` | Cheaper setup/cleanup |
| `androidTest/.../testutils/BaseCalendarTestFixture.kt` and mock providers | Less mock construction per test |
| Selected `test/...` and `androidTest/...` classes | Remove trivial tests, parameterise duplicates, group assertions |

## Testing

This plan changes tests, so the guardrails are the CI outputs rather than new tests:

- **Coverage:** the PR coverage comment (overall %, ~35.8% in Oct 2026) and the per-class reports must not drop. Each PR states the before/after numbers.
- **Runtime:** each PR states before/after time for the classes it touches, from the Phase 1 script.
- **No sleeps, no `System.currentTimeMillis()`**: use `CNPlusTestClock` and real synchronisation (futures, latches, idling), as everywhere else.
- **MockK limits:** `mockkStatic`/`mockkConstructor` fail in instrumentation tests. Prefer dependency injection (`docs/dev_completed/constructor-mocking-android.md`).

## Open Questions

- Is per-class coverage enough to judge a removal, or do some tests need per-test coverage (JaCoCo sessions) to show they add nothing unique?
- Should the Phase 1 report run automatically in CI (job summary), or stay a script run when needed?
