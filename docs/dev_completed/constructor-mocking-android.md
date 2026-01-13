# Testing Constructor-Instantiated Dependencies in Legacy Android Code

## Background

While building a thorough test suite for this legacy application, we encountered significant challenges when trying to mock or intercept dependencies that are instantiated directly inside methods (e.g., `EventsStorage(context)`)—especially for classes that extend Android framework classes like `SQLiteOpenHelper`.

## The Problem

- **Direct Instantiation:** Many methods in the codebase instantiate dependencies directly (e.g., `EventsStorage(context)`), rather than accepting them as parameters or using dependency injection.
- **MockK Limitations:** Modern mocking libraries like MockK provide `mockkConstructor` and `mockkStatic` to intercept such instantiations. However, these features are unreliable or outright fail in Android instrumentation tests, especially for classes that:
  - Extend Android framework classes (e.g., `SQLiteOpenHelper`)
  - Have complex constructors or static initializers
  - Are used within extension functions or nested lambdas
- **Typical Errors:** Attempts to use these mocking features resulted in errors such as:
  - `FAILURE TO RETRANSFORM Unable to perform redefinition... Verifier rejected class...`
  - `ArrayIndexOutOfBoundsException` or other runtime crashes
  - Mock verifications failing even when the stubbing worked

## Why This Happens

- **Android Runtime (ART) Restrictions:** ART does not support the same level of bytecode manipulation as the JVM, especially for classes loaded from the Android framework or with certain static initializers.
- **MockK's Approach:** MockK's constructor and static mocking rely on bytecode transformation, which is fragile in the context of Android instrumentation tests.
- **Legacy Code Patterns:** The codebase's pattern of direct instantiation (rather than dependency injection) makes it difficult to substitute test doubles without risky or brittle mocking.

## The Solution: Minimal Testability Refactor

To enable reliable and faithful testing, we made a minimal change:

- **Add an Optional Dependency Parameter:**
  - For methods like `ApplicationController.dismissEvent`, add an optional parameter (e.g., `db: EventsStorageInterface? = null`).
  - In production, the method behaves as before (instantiates the dependency if not provided).
  - In tests, we can inject a mock or fake, ensuring full control and verifiability.

This approach:
- Preserves the original code's behavior for production and most callers.
- Enables robust, faithful tests without brittle or unreliable mocking.
- Requires only a small, targeted change to the codebase.

## Takeaway

**Constructor mocking for Android framework classes is unreliable in instrumentation tests. For legacy code, the minimum viable path to testability is to allow optional injection of dependencies in methods under test.**

See the relevant test and implementation changes for an example of this approach.

---

**See also:** [Dependency Injection Patterns](../testing/dependency_injection_patterns.md) for the full guide on when to use optional parameters vs companion object providers.
