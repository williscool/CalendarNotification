
# Code Changes

This is a legacy codebase (2016) with a robust test suite added in 2024-25.

**Permitted without confirmation:**
- Bug fixes with corresponding test coverage
- Refactoring with existing test coverage
- Removal of deprecated features (QuietHours, CalendarEditor)

**Requires tests first:**
- New features or significant changes

When in doubt, add tests first.

# Work to make tests as faithful to the real code as possible

its ok to mock out core android apis that the instrumentation testsuite doesn't work well with i.e. making push notifications but try to use the real original code for anything that exists in this codebase


# Don't try to boil the ocean. Dont try to make big sweeping changes when more focused ones will do

Always think of the minimum viable solution to a problem or change to make. make sure that works and then build on top of it. break things down into small testable pieces first. That said 

**NO CHEATING!** I.e. don't comment out or skip a test to solve a problem unless its just a temporary bandaid while working on something more important.

# keep all code implementations as consise as possible.

Everything it needs nothing it doesn't. Every new line of code is one that potentially doesn't work 😄


# Check documentation if implementing something potentially complex or nonstandard.

See `docs/README.md` for the full documentation index. often there are things we've learned already i.e. 

**Key references:**

- `docs/dev_completed/constructor-mocking-android.md` - **MockK limitations**: `mockkStatic`, `mockkConstructor`, and `anyConstructed` almost always fail in Android instrumentation tests. Use dependency injection patterns instead.

- `docs/architecture/calendar_monitoring.md` - Deep detail on how calendar monitoring works (EVENT_REMINDER broadcasts, manual rescans, etc.)

- `docs/architecture/clock_implementation.md` - How `CNPlusClockInterface` enables testable time-dependent code


# Copyright Headers

For new or updated copyright headers, use:

```
Copyright (C) 2025 William Harris (wharris+cnplus@upscalews.com)
```

(William inherited the application from Sergey Parshin in 2020)

# Never Catch the broad Exception class

no catching Exception e. it can lead to hiding important bugs to catch. always do something more specific

# Never use System.currentTimeMillis() directly

Use `CNPlusClockInterface` instead - it enables testable time-dependent code.

**Production code:** Inject `CNPlusSystemClock()` or access via interface property
**Test code:** Use `TestTimeConstants.STANDARD_TEST_TIME` or `CNPlusTestClock`

See `docs/architecture/clock_implementation.md` and `docs/dev_todo/system_current_time_millis_removal.md` for details.