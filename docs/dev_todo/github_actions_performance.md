# GitHub Actions Performance Optimizations

## Overview

Analysis of GitHub Actions workflow performance based on run 21023241683 (January 15, 2026).

### Key Timing Findings

| Job | Duration | Key Bottlenecks |
|-----|----------|-----------------|
| Build Android App (x86_64) | ~32 min | Common Setup: 10 min, Dry-run: 2 min, Build: 18.5 min |
| Build Android App (arm64) | ~31 min | Similar breakdown |
| Unit Tests | ~20 min | Common Setup: 10 min, Dry-run: 2.5 min, Tests: 7 min |
| Verify Connected Android Test | ~26 min | Common Setup: 10 min, Build+Test: 15 min |
| Integration Test (per shard) | ~12-14 min | Common Setup: 7-8 min, Tests: 3-4 min |

---

## Actionable Optimizations

### 1. Remove "Debug Main Build Task Graph" Dry-Run Step

**Location**: `.github/workflows/actions.yml` lines 115-131

**Current code**:
```yaml
- name: Debug Main Build Task Graph
  run: |
    cd android && \
    echo "=== Main Build Task Graph ===" && \
    ./gradlew ... --dry-run ...
```

**Action**: Remove this step entirely (or make conditional on a debug input flag).

**Measured impact**: ~2 minutes per build job × 2 architectures = **~4 minutes saved**

**Risk**: Very low - purely diagnostic output

---

### 2. Remove "Debug Unit Test Task Graph" Dry-Run Step

**Location**: `.github/workflows/actions.yml` lines 444-457

**Current code**:
```yaml
- name: Debug Unit Test Task Graph
  run: |
    cd android && \
    echo "=== Unit Test Task Graph ===" && \
    ./gradlew ... --dry-run ...
```

**Action**: Remove this step entirely.

**Measured impact**: ~2.5 minutes saved

**Risk**: Very low - purely diagnostic output

---

### 3. Remove "List Gradle Tasks" from Common Setup

**Location**: `.github/actions/common-setup/action.yml` lines 276-281

**Current code**:
```yaml
- name: List Gradle Tasks
  run: |
    cd android && \
    ./gradlew tasks --all > gradle-tasks.txt
    cat gradle-tasks.txt
```

**Action**: Remove this step entirely. It runs on every job and is purely diagnostic.

**Measured impact**: ~30-60 seconds per job × 10+ jobs = **~5-10 minutes aggregate saved**

**Risk**: Low - only diagnostic, Gradle gets warmed up by other steps anyway

---

## Estimated Total Savings

| Optimization | Time Saved |
|--------------|------------|
| Remove build dry-run | ~4 min |
| Remove unit test dry-run | ~2.5 min |
| Remove gradle tasks list | ~5-10 min |
| **Total** | **~11-16 minutes per CI run** |

---

## Known Issues (Not Actionable)

### `verify-connected-android-test` Rebuilds Everything

**Why it can't use pre-built APKs**: The Android Gradle Plugin's `connectedAndroidTest` task has hardcoded dependencies on `assembleDebug` and `assembleDebugAndroidTest`. There is no official way to skip these build steps - the AGP's `DeviceProviderInstrumentTestTask` explicitly requires them.

**Previous investigation** (early 2025): Attempted to use pre-built APKs with `connectedAndroidTest`, including reviewing AGP source code. No viable solution found.

**Current workaround**: The main `integration-test` job uses `am instrument` directly with pre-built APKs (via `matrix_run_android_tests.sh`). The `verify-connected-android-test` job exists as a sanity check to ensure `connectedAndroidTest` still works if ever needed, but it's not required for PR merges.

### Integration Test Shard Flakiness/Retries

**Observed**: All 4 shards were on "attempt 3" in run 21023241683. Shard 0 failed twice before succeeding (started 42 min after other shards).

**Root cause**: Resource contention on GitHub Actions runners when multiple emulators run simultaneously. This is a known limitation of the free tier runners.

**Mitigation options**:
- Reduce shard count (current: 4, previously tried 8 which was too flaky)
- Use paid runners with more resources
- Accept occasional retries as cost of parallelism

### `set_build_datetime` Job

The separate job ensures timestamp consistency across all jobs that use it for artifact naming. While it adds ~30 seconds of runner startup overhead, removing it would require a different mechanism to share a consistent timestamp across jobs.

---

## Implementation Checklist

- [x] Remove "Debug Main Build Task Graph" step from `actions.yml`
- [x] Remove "Debug Unit Test Task Graph" step from `actions.yml`
- [x] Remove "List Gradle Tasks" step from `common-setup/action.yml`
- [ ] Verify CI still passes after changes
- [ ] Monitor CI run times to confirm improvements
