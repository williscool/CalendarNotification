# GitHub Actions Performance Optimizations

**Status**: Completed (January 2026)

## Summary

Comprehensive optimization of GitHub Actions CI pipeline, reducing typical CI run times from ~45-50 minutes to ~20-25 minutes through caching improvements, lighter job configurations, and cache warming strategies.

## Key Achievements

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Build (arm64, warm cache) | ~32 min | ~10 min | **-22 min** |
| Build (x86_64, warm cache) | ~31 min | ~13 min | **-18 min** |
| Unit Tests | ~20 min | ~12 min | **-8 min** |
| Integration Shards | ~12-14 min | ~4-6 min | **-6-8 min each** |
| Total CI (warm cache) | ~45-50 min | ~20-25 min | **~50% faster** |

---

## Implemented Optimizations

### 1. Removed Debug Dry-Run Steps

**Files changed**: `.github/workflows/actions.yml`

Removed diagnostic `--dry-run` steps that were adding ~2-4 minutes per build:
- "Debug Main Build Task Graph" step
- "Debug Unit Test Task Graph" step
- "List Gradle Tasks" step from common-setup

**Savings**: ~6-8 minutes aggregate per CI run

---

### 2. Skip Codegen for Integration Test Shards

**Files changed**: `.github/actions/common-setup/action.yml`, `.github/workflows/actions.yml`

Added `skip_codegen` input to common-setup. Integration test shards only run pre-built APKs via `am instrument` - they don't need React Native codegen.

**Savings**: ~3 min × 4 shards = **~12 min aggregate**

---

### 3. Lighter Setup for Integration Test Shards (`test_runner_only`)

**Files changed**: `.github/actions/common-setup/action.yml`, `.github/actions/cache-update/action.yml`, `.github/workflows/actions.yml`

Added `test_runner_only` mode that skips unnecessary steps for jobs that only run pre-built APKs on emulators:

**Skipped steps**:
- Node.js setup
- Yarn config/install
- JS bundle cache/generation
- Gradle caches
- Gradlew permissions
- Ccachify scripts
- React Native caches
- Free disk space cleanup

**Kept steps**:
- JDK setup
- Android SDK/emulator setup

**Savings**: ~2 min × 4 shards = **~8 min aggregate**

---

### 4. Skip Disk Cleanup for Test Runners

**Files changed**: `.github/actions/common-setup/action.yml`

The `free-disk-space` action removes ~8GB of unused software (.NET, Haskell, etc.) but takes ~1-2 minutes. Test runners using `test_runner_only` don't need this because they don't download large Gradle/Node dependencies.

**Savings**: ~1-2 min × 4 shards = **~4-8 min aggregate**

---

### 5. Cache Warming Workflow

**Files created**: `.github/workflows/cache-warming.yml`

New workflow that proactively warms caches on master branch:

**Triggers**:
- Daily at 9 AM UTC (4 AM EST)
- On push to master when dependency files change
- Manual dispatch

**Jobs**:
1. **warm-build-caches** (arm64-v8a, x86_64): Full debug+release+test builds with ccache
2. **warm-test-caches**: Unit test compilation
3. **warm-integration-test-caches**: Android emulator/AVD setup

**Key insight**: PR branches can read caches from their base branch (master), so warming master makes all PRs faster.

---

### 6. Conditional Cache Saving

**Files changed**: `.github/actions/common-setup/action.yml`, `.github/actions/cache-update/action.yml`, `.github/workflows/actions.yml`

Added cache-hit outputs from common-setup and conditional saving in cache-update to avoid redundant cache writes:

- Skip saving if cache was already hit
- Reduces cache storage costs and save time
- Prevents cache key conflicts between parallel jobs

---

### 7. Broader Cache Restore Keys

**Files changed**: `.github/actions/common-setup/action.yml`

Improved `restore-keys` patterns for Gradle and Android AVD caches to enable partial cache hits:

```yaml
restore-keys: |
  ${{ runner.os }}-gradle-${{ inputs.arch }}-${{ hashFiles('...') }}-
  ${{ runner.os }}-gradle-${{ inputs.arch }}-
  ${{ runner.os }}-gradle-
```

This allows new branches to get cache hits from similar previous builds even if the exact key doesn't match.

---

### 8. ccache for Native Compilation

**Files changed**: `.github/workflows/cache-warming.yml`, (already in `actions.yml`)

The ccache stores compiled C/C++ objects (React Native native modules, NDK code). Critical finding:

| Scenario | Native Build Time | Files Compiled |
|----------|-------------------|----------------|
| With ccache hit | ~2.5 min | 0/596 |
| Without ccache | ~18.5 min | 596/596 |

**Root cause of slow fresh-branch builds**: ccache entries are branch-scoped. New PR branches couldn't find ccache from master because cache-warming wasn't creating one.

**Fix**: Added ccache to cache-warming with full build coverage (debug + release + test APKs = 596 native files).

---

### 9. Fixed Yarn Cache Path

**Files changed**: `.github/actions/cache-update/action.yml`

Bug fix: The yarn cache save was using shell substitution `$(yarn config get cacheFolder)` which doesn't work in GitHub Actions `with:` blocks. Changed to hardcoded `.yarn/cache` path.

---

## Architecture Overview

### Cache Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Cache Warming (master)                        │
│  - Runs daily at 9 AM UTC                                       │
│  - Creates ccache, Gradle, Yarn, Android AVD caches             │
│  - Full debug+release+test builds to populate ccache (596 files)│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PR Branch Builds                              │
│  - Restore caches from master via restore-keys                  │
│  - ccache hits → native compilation skipped                     │
│  - Gradle cache hits → incremental builds                       │
│  - Save updated caches for future runs on same branch           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              Integration Test Shards (test_runner_only)          │
│  - Skip Node/Yarn/Gradle setup                                  │
│  - Skip disk cleanup                                            │
│  - Only restore Android AVD cache                               │
│  - Download pre-built APKs from build job                       │
│  - Run tests via am instrument                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Job Dependencies

```
set_build_datetime ─┬─► build (arm64, x86_64) ─┬─► sign ─► comment-pr
                    │                          │
                    │                          └─► integration-test (4 shards)
                    │                                      │
                    │                                      └─► merge-integration-coverage
                    │
                    ├─► unit-tests ─────────────────────────────────┐
                    │                                               │
                    ├─► verify-connected-android-test               │
                    │                                               ▼
                    └───────────────────────────────────────► coverage-report
```

---

## Known Limitations

### `verify-connected-android-test` Always Rebuilds

The Android Gradle Plugin's `connectedAndroidTest` task has hardcoded dependencies on `assembleDebug` and `assembleDebugAndroidTest`. There is no way to use pre-built APKs.

**Workaround**: Main integration tests use `am instrument` directly via `matrix_run_android_tests.sh`. The `verify-connected-android-test` job is a non-blocking sanity check.

### Integration Test Shard Flakiness

Resource contention on GitHub Actions free-tier runners causes occasional emulator failures. Mitigated by:
- Reducing shard count from 8 to 4
- Using retry logic in test runner
- Accepting occasional retries as cost of parallelism

### First Run on New Branch

Even with cache warming, the very first CI run on a brand new branch may be slower because:
- ccache keys include timestamps, so exact matches are rare
- Partial cache restores via restore-keys still require some recompilation

Subsequent runs on the same branch benefit from caches saved by the first run.

---

## Files Modified

### Workflows
- `.github/workflows/actions.yml` - Main CI workflow
- `.github/workflows/cache-warming.yml` - **New** cache warming workflow

### Composite Actions
- `.github/actions/common-setup/action.yml` - Added `skip_codegen`, `test_runner_only` inputs
- `.github/actions/cache-update/action.yml` - Added conditional saving, `test_runner_only` support

---

## Monitoring

To verify cache effectiveness, check these in CI run logs:

1. **ccache stats** (in Post ccache step):
   ```
   Hits: 596 / 596 (100.0%)  ← Good
   Hits: 0 / 596 (0.00%)     ← Cache miss, slow build
   ```

2. **Cache restore messages**:
   ```
   Cache hit for: Linux-gradle-deps-v1-...     ← Exact hit
   Cache hit for restore-key: Linux-gradle-... ← Partial hit
   Cache not found for input keys: ...         ← Miss
   ```

3. **Integration shard Common Setup time**:
   - With `test_runner_only`: ~2 min
   - Without: ~4+ min

---

## Related Documentation

- `docs/testing/test_sharding.md` - Integration test sharding strategy
- `docs/dev_completed/constructor-mocking-android.md` - Why we use `am instrument` instead of `connectedAndroidTest`
