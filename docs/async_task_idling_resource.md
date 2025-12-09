# AsyncTask Idling Resource

## Purpose

Synchronizes Espresso UI tests with `background { }` async operations to prevent flaky tests caused by race conditions.

## How It Works

1. `AsyncTaskCallback` interface in `AsyncUtils.kt` defines `onTaskStarted()` / `onTaskCompleted()`
2. `AsyncOperation` calls these hooks during task lifecycle
3. `globalAsyncTaskCallback` is a `@Volatile` global that tests can set
4. `AsyncTaskIdlingResource` implements both `IdlingResource` and `AsyncTaskCallback`
5. Espresso waits until counter reaches 0 before proceeding

## Usage

Tests using `UITestFixture` get this automatically:

```kotlin
@Before
fun setup() {
    fixture.setup(waitForAsyncTasks = true)  // Registers idling resource
}

@After
fun teardown() {
    fixture.cleanup()  // Unregisters and nulls globalAsyncTaskCallback
}
```

## Production Impact

None. The callback defaults to `null`, so production code just does a few no-op null checks.

## Added: December 2024

