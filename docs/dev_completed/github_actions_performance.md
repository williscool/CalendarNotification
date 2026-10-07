# GitHub Actions Performance Optimizations

**Status**: Completed (January 2026; second pass October 2026, see below)

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

## October 2026: Second Pass

After the flaky emulator tests were root-caused and fixed (#285, #286, #289, #290, #294, #297), runs were stable enough to measure, and a second pass took a typical full run from **~21.5 min to ~12 min**, and the required **CI Result** check from **~11 min to ~7.5-8 min**.

| Milestone | Before (late Sept) | After |
|---|---|---|
| Builds done | ~9.7 min | ~5.3 min |
| Emulator shards start | ~9.7 min | ~5.3 min |
| CI Result green | ~11 min | ~7.5-8 min |
| Whole run | ~21.5 min | ~11.8-12.3 min |

### What changed

| PR | Change | Effect |
|---|---|---|
| #288 | Cache warming runs on **every** merge to master, not just dependency changes | First run of each PR and tag builds restore warm caches (cold runs took 20-28 min) |
| #307 | Caches saved **only on master** | -1.3 min per build; PR-scoped saves never sped up later pushes |
| #308 | Disk cleanup only below 40 GB free | -1.7 min per build; runners now have 145 GB disks (~86 GB free) |
| #309 | Merged integration coverage via **JaCoCo's CLI**, not Gradle | Merge job ~4.6 → ~0.9 min (JaCoCo pinned to 0.8.12, what Gradle actually ran) |
| #310 | **8 emulator shards** (4 UI + 4 non-UI) | Slowest shard ~6.4 → ~5 min. The December "8 is flaky" was the foldable profile timing out |
| #311 | Unit tests in **2 parallel JVM forks** (half the cores) | Test task ~5.1 → ~4.1 min; 4 forks contended for CPU |
| #314 | Jest in its own job; unit tests drop ccache and the JS bundle | ~1 min off unit tests |
| #315, #316, #328 | **Unit tests sharded** by test-class hash (3 shards), coverage merged with JaCoCo's CLI | Unit tests off the critical path |
| #319 | JS bundle cache key fixed (`hashFiles` doesn't expand braces; save and lookup keys differed) | Bundle cache actually hits; -0.6 min per build |
| #320 | PRs build **debug only**; release APKs/AABs only for tags and manual runs | Gradle step ~2.7 → ~1.8 min |
| #321 | JS bundle generated in the background during setup (`flock`-synchronized) | Overlaps bundling with setup when JS changed |
| **#323** | **Each per-arch build compiles native code for its own ABI only** | See below |
| #324 | Build split into `build-x86_64` / `build-arm64` (YAML anchors); emulator shards wait for x86_64 only | Removes up to ~2 min of waiting on arm64 |
| #327 | Android emulator package cached with the AVD | No per-shard emulator download, which once came back corrupt and killed a shard |
| #329 | connectedAndroidTest check moved to its own workflow (merges, daily, infra PRs) | ~10-14 min job off every PR |

### The native ABI fix (#323)

Every per-arch build compiled native code for **both** ABIs, and op-sqlite for **all four**. Several earlier attempts (#52, #62, #71, #72, #76) only addressed the app module. There were two causes:

1. **AGP unions `abiFilters`** across `defaultConfig`, product flavors and build types, so a flavor's single-ABI list can't override a both-ABI list elsewhere. The fix computes one list from `BUILD_ARCH` and keeps it **only** in `defaultConfig`.
2. **op-sqlite sets no `abiFilters`**, so it built the NDK default of four ABIs. The root `subprojects` hook now sets library filters from `reactNativeArchitectures`.

The second ABI was dead weight anyway: the x86_64 APK's `arm64-v8a` folder lacked the React Native libraries' `.so` files and couldn't run on arm64.

### What's left on the critical paths

- **Whole run:** build x86_64 (~5.3) → slowest emulator shard (~4-6, real test time) → coverage merge/report (~1).
- **CI Result:** the slowest unit-test shard. Each pays ~5 min of fixed setup (cache restores, codegen, Gradle configuration, resource merge, compile) before ~1.5-2 min of tests. Codegen time is real work, not Gradle overhead (#317 folded it into the main build and saved nothing).

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
- On every push to master (originally only on dependency-file changes, from when it ran minimal Gradle tasks; widened once it became a full ccache build)
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

As of October 2026:

```
set_build_datetime ─┬─► build-x86_64 ─┬─► integration-test (8 shards) ─► merge-integration-coverage ─┐
(+ docs-only check) │                 │                                                             │
                    ├─► build-arm64 ──┴─► sign ─► comment-pr                                        │
                    │                                                                               │
                    ├─► unit-tests (3 shards) ─► merge-unit-test-coverage ──────────────────────────┤
                    │                                                                               ▼
                    ├─► jest-tests                                                           coverage-report
                    │
                    └─► CI Result  (needs builds, sign, unit tests + their merge, jest, safety checks)

connectedAndroidTest check: separate workflow (connected-android-test.yml)
```

---

## Known Limitations

### `verify-connected-android-test` Always Rebuilds

The Android Gradle Plugin's `connectedAndroidTest` task has hardcoded dependencies on `assembleDebug` and `assembleDebugAndroidTest`. There is no way to use pre-built APKs.

**Workaround**: Main integration tests use `am instrument` directly via `matrix_run_android_tests.sh`. The `verify-connected-android-test` job is a non-blocking sanity check.

**Update (Oct 2026):** the check moved out of `actions.yml` into its own workflow, `.github/workflows/connected-android-test.yml`. It runs on merges to master, daily, on manual dispatch, and on PRs that touch the build or test infrastructure, instead of on every PR.

### Integration Test Shard Flakiness

Resource contention on GitHub Actions free-tier runners causes occasional emulator failures. Mitigated by:
- Reducing shard count from 8 to 4
- Using retry logic in test runner
- Accepting occasional retries as cost of parallelism

**Update (Oct 2026):** the recurring "flakes" turned out to be real, fixable bugs, so treat a new one as a bug first. Examples: background work outliving a test's mocks (#285, #286), a launcher ANR dialog stealing focus (#289), tests racing async loads (#290, #297), and a permission-grant race (#294). The logcat artifact (`emulator-log-shard-N`) and Allure screenshots usually name the cause. With those fixed, CI runs 8 shards again.

### First Run on New Branch

Even with cache warming, the very first CI run on a brand new branch may be slower because:
- ccache keys include timestamps, so exact matches are rare
- Partial cache restores via restore-keys still require some recompilation

Subsequent runs on the same branch benefit from caches saved by the first run.

**Update (Oct 2026):** PR runs no longer save caches (#307). Every PR run restores master's caches instead, which cache warming refreshes on every merge (#288).

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
