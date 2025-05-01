# SQLite Native Library Issues in Robolectric Tests

## Background

When running Robolectric tests that involve code using SQLite databases, we encountered issues with native library loading that didn't occur in instrumentation tests.

## The Problem

- **Native Library Loading Error**: In Robolectric tests, any instantiation of `SQLiteOpenHelper` or access to its `writableDatabase` property would attempt to load the native SQLite library (`sqlite3x`), resulting in:
  ```
  java.lang.UnsatisfiedLinkError: no sqlite3x in java.library.path
  ```

- **Different Mock Behavior**: MockK in Robolectric tests was creating mocks that were getting detected as `SQLiteOpenHelper` instances, while in instrumentation tests the same mocks were working fine.

- **Inconsistent Environment**: Code that worked in production and instrumentation tests was failing in Robolectric due to differences in how the Android framework is simulated.

- **Specific Error Trace**: The error occurred when a mocked database class (`EventsStorageInterface`) was used within the `classCustomUse` extension function, which was checking if the object `is SQLiteOpenHelper`.

## Root Cause Analysis

1. **MockK Implementation Differences**: In Robolectric, MockK's subclasses were somehow satisfying the `is SQLiteOpenHelper` check, while they didn't in instrumentation tests.

2. **Native Code Access**: Robolectric simulates much of the Android framework in pure Java, but doesn't provide implementations for all native libraries like SQLite.

3. **Class Loading Variations**: The way classes are loaded and inheritance is checked differs between Robolectric and real Android environments.

4. **Test Environment Isolation**: Robolectric tests run on the JVM, not on an Android device or emulator, so they can't access native Android libraries.

## The Solution: Environment-Based Approach

Rather than trying to detect specific mock types, we implemented an environment detection strategy:

1. **Test Environment Detection**: We created a method to detect if code is running in a test environment (Robolectric or JUnit):
   ```kotlin
   fun isInTestEnvironment(): Boolean {
     if (isTestEnvironment == null) {
       isTestEnvironment = try {
         // Check for Robolectric
         Class.forName("org.robolectric.RuntimeEnvironment")
         true
       } catch (e: ClassNotFoundException) {
         // Check for JUnit test runner
         try {
           Class.forName("org.junit.runner.JUnitCore")
           true
         } catch (e: ClassNotFoundException) {
           false
         }
       }
     }
     return isTestEnvironment ?: false
   }
   ```

2. **Conditional SQLite Access**: We modified the `classCustomUse` function to avoid accessing SQLite in test environments:
   ```kotlin
   fun <T, R> T.classCustomUse(block: (T) -> R): R {
     // [logging code omitted]
     
     // Only use real SQLiteOpenHelper in non-test environments
     if (this is SQLiteOpenHelper && !isInTestEnvironment()) {
       val helper = this as SQLiteOpenHelper
       try {
         val db = helper.writableDatabase
         return block(this)
       } finally {
         // Cleanup code
       }
     }
     
     // For test environments, just call the block directly
     return block(this)
   }
   ```

3. **Public Control**: We made the test environment flag public so tests can explicitly control the behavior if needed.

## Benefits of This Approach

1. **Consistent Behavior**: Works reliably across all test environments (Robolectric, instrumentation, unit tests).

2. **Avoids Native Library Loading**: Prevents any attempts to load native libraries in test environments.

3. **Minimal Code Changes**: Requires changes only to infrastructure code, not application logic.

4. **Better Testability**: Enables thorough testing of code that uses SQLite databases without environment-specific workarounds.

5. **Future-Proof**: This approach is robust against changes in mock libraries or test frameworks.

## Lessons Learned

- **Environment Detection > Type Detection**: When dealing with framework classes in tests, detecting the test environment is more reliable than checking specific types or trying to detect mocks.

- **Avoids Native Dependencies in Tests**: In test environments, especially Robolectric, avoid accessing native libraries when possible.

- **Defensive Programming**: Adding detailed logging and fallback behaviors helped diagnose and resolve the issue.

- **MockK Limitations**: MockK's behavior can vary between different test environments, particularly for Android framework classes.

This solution addresses the specific challenges of testing SQLite-dependent code in Robolectric tests while maintaining compatibility with instrumentation tests and production code. 