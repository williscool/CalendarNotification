# Fix for Intermittent Build Issue: Resources.getConfiguration() NPE in Android Instrumentation Tests

## What Was the Problem?

Some Android instrumentation tests were intermittently failing with a `NullPointerException` (NPE) when the system tried to access `Resources.getConfiguration()` on a mocked `Context`. This happened because the mock context returned a real `Resources` object, but the underlying `Configuration` was `null` in the test environment. This caused failures during test setup or when Android tried to bind the application context.

## Why Did This Happen?

- The test code used `every { getResources() } returns realContext.resources` to delegate resource calls to the real context.
- In the test environment, the real `Resources` object sometimes had a `null` `Configuration` property, which is not expected in production.
- When Android or the app code called `getConfiguration()` on this `Resources` object, it triggered an NPE.

## How Was It Fixed?

- Instead of delegating to the real context's resources, we created a proper mock `Resources` object using MockK.
- This mock always returns a valid, non-null `Configuration` object:

  ```kotlin
  val mockConfiguration = android.content.res.Configuration().apply { setToDefaults() }
  val mockResources = mockk<android.content.res.Resources>(relaxed = true) {
      every { getConfiguration() } returns mockConfiguration
      every { configuration } returns mockConfiguration
      every { displayMetrics } returns realContext.resources.displayMetrics
  }
  every { getResources() } returns mockResources
  every { resources } returns mockResources
  ```
- This ensures that any call to `getConfiguration()` or `configuration` on the mocked resources will always return a valid object, preventing the NPE.

## Why Does This Work?

- Android expects `Resources.getConfiguration()` to always return a valid object, even in tests.
- By providing a mock that always returns a valid `Configuration`, we match the expectations of the Android framework and avoid test flakiness.
- This approach is minimal and only affects test code, so it is safe and does not impact production behavior.

## General Takeaway

When mocking Android `Context` or `Resources` in tests, always ensure that required properties like `Configuration` are non-null. Use a mock that returns a valid object for these properties to avoid intermittent or environment-specific test failures.
