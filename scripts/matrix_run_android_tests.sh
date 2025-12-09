#!/bin/bash
set -e

# =============================================================================
# matrix_run_android_tests.sh - Run Android instrumentation tests with sharding
# =============================================================================
#
# Supports both named flags and environment variables (flags override env vars).
#
# Usage:
#   ./matrix_run_android_tests.sh --shard-index 0 --num-shards 4
#   SHARD_INDEX=0 NUM_SHARDS=4 ./matrix_run_android_tests.sh
#
# For complete usage, run: ./matrix_run_android_tests.sh --help

# --- Constants ---
APP_PACKAGE="com.github.quarck.calnotify"
TEST_PACKAGE="${APP_PACKAGE}.test"
TEST_RUNNER="com.atiurin.ultron.allure.UltronAllureTestRunner"
UI_TEST_PACKAGE="com.github.quarck.calnotify.ui"

# Sharding strategy (with 8 shards):
#   Shards 0-3: UI tests (slow) - get 4 shards
#   Shards 4-7: Non-UI tests (fast) - get 4 shards
UI_SHARD_COUNT=4  # Number of shards dedicated to UI tests

# --- Default Configuration (from env vars or defaults) ---
SHARD_INDEX="${SHARD_INDEX:-}"
NUM_SHARDS="${NUM_SHARDS:-}"
ARCH="${ARCH:-x86_64}"
MODULE="${MODULE:-app}"
TEST_TIMEOUT="${TEST_TIMEOUT:-30m}"
SINGLE_TEST="${SINGLE_TEST:-}"

# --- Functions ---
show_usage() {
  cat << EOF
Usage: $(basename "$0") [OPTIONS]

Run Android instrumentation tests with smart sharding support.

Smart Sharding Strategy (with 8 shards):
  Shards 0-3: UI tests (slow) - 4 parallel shards
  Shards 4-7: Non-UI tests (fast) - 4 parallel shards

Options:
  --shard-index N    Which shard to run (0-indexed). Env: SHARD_INDEX
  --num-shards N     Total number of shards. Env: NUM_SHARDS
  --arch ARCH        Build architecture (default: x86_64). Env: ARCH
  --module MODULE    Gradle module name (default: app). Env: MODULE
  --timeout TIME     Test timeout (default: 30m). Env: TEST_TIMEOUT
  --single-test TEST Run specific test class or class#method. Env: SINGLE_TEST
  --help             Show this help message

Examples:
  # Run all tests (no sharding)
  $(basename "$0")

  # Run UI tests shard 0 (of 4 UI shards)
  $(basename "$0") --shard-index 0 --num-shards 8

  # Run non-UI tests shard 0 (of 4 non-UI shards)
  $(basename "$0") --shard-index 4 --num-shards 8

  # Via env vars
  SHARD_INDEX=1 NUM_SHARDS=8 $(basename "$0")

  # Run single test
  $(basename "$0") --single-test com.example.MyTest#testMethod
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      --shard-index)  SHARD_INDEX="$2"; shift 2 ;;
      --num-shards)   NUM_SHARDS="$2"; shift 2 ;;
      --arch)         ARCH="$2"; shift 2 ;;
      --module)       MODULE="$2"; shift 2 ;;
      --timeout)      TEST_TIMEOUT="$2"; shift 2 ;;
      --single-test)  SINGLE_TEST="$2"; shift 2 ;;
      --help)         show_usage; exit 0 ;;
      *) echo "Error: Unknown option: $1"; show_usage; exit 1 ;;
    esac
  done
}

get_arch_suffix() {
  if [ "$ARCH" == "arm64-v8a" ]; then
    echo "Arm64V8a"
  else
    echo "X8664"
  fi
}

check_device() {
  echo "Checking for connected devices..."
  local device_count
  device_count=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)
  if [ "$device_count" -eq 0 ]; then
    echo "Error: No connected devices found."
    exit 1
  fi
  echo "Found $device_count connected device(s)"
}

find_apk() {
  local apk_path="$1"
  local search_pattern="$2"
  local apk_type="$3"

  if [ -f "$apk_path" ]; then
    echo "$apk_path"
    return 0
  fi

  echo "Warning: $apk_type APK not found at: $apk_path" >&2
  echo "Searching for alternatives..." >&2
  local found_apk
  found_apk=$(find "./$MODULE/build/outputs" -name "$search_pattern" 2>/dev/null | head -1)
  
  if [ -z "$found_apk" ]; then
    echo "Error: No $apk_type APK found" >&2
    find "./$MODULE/build/outputs" -type f -name "*.apk" | sort >&2
    exit 1
  fi
  echo "$found_apk"
}

install_apks() {
  local app_apk="$1"
  local test_apk="$2"

  echo "Uninstalling existing app versions..."
  adb uninstall "$APP_PACKAGE" 2>/dev/null || true
  adb uninstall "$TEST_PACKAGE" 2>/dev/null || true
  sleep 2

  echo "Installing app APK: $app_apk"
  adb install -r "$app_apk"
  echo "Installing test APK: $test_apk"
  adb install -r "$test_apk"
}

build_instrument_command() {
  local coverage_path="$1"
  
  local cmd="am instrument -w -r"
  cmd+=" -e debug false"
  cmd+=" -e collect.coverage true"
  cmd+=" -e coverage true"
  cmd+=" -e coverageFile \"$coverage_path\""
  cmd+=" -e coverageDataFileLocation \"$coverage_path\""
  cmd+=" -e jacoco true"
  cmd+=" -e outputFormat \"xml\""
  cmd+=" -e jaco-agent.destfile \"$coverage_path\""
  cmd+=" -e jaco-agent.includes \"com.github.quarck.calnotify.*\""
  cmd+=" -e listener \"de.schroepf.androidxmlrunlistener.XmlRunListener\""

  # Smart sharding: UI tests get shards 0-(UI_SHARD_COUNT-1), non-UI get the rest
  # With 4 total shards and UI_SHARD_COUNT=2:
  #   Shard 0: UI tests, shard 0 of 2
  #   Shard 1: UI tests, shard 1 of 2
  #   Shard 2: Non-UI tests, shard 0 of 2
  #   Shard 3: Non-UI tests, shard 1 of 2
  if [ -n "$NUM_SHARDS" ] && [ -n "$SHARD_INDEX" ]; then
    if [ "$SHARD_INDEX" -lt "$UI_SHARD_COUNT" ]; then
      # UI test shard
      cmd+=" -e package $UI_TEST_PACKAGE"
      cmd+=" -e numShards $UI_SHARD_COUNT"
      cmd+=" -e shardIndex $SHARD_INDEX"
      echo "Shard $SHARD_INDEX: Running UI tests (shard $SHARD_INDEX of $UI_SHARD_COUNT)" >&2
    else
      # Non-UI test shard
      local non_ui_shard_count=$((NUM_SHARDS - UI_SHARD_COUNT))
      local non_ui_shard_index=$((SHARD_INDEX - UI_SHARD_COUNT))
      cmd+=" -e notPackage $UI_TEST_PACKAGE"
      cmd+=" -e numShards $non_ui_shard_count"
      cmd+=" -e shardIndex $non_ui_shard_index"
      echo "Shard $SHARD_INDEX: Running non-UI tests (shard $non_ui_shard_index of $non_ui_shard_count)" >&2
    fi
  else
    # No sharding - run all tests (no package filter)
    echo "Running all tests (no sharding)" >&2
  fi

  # Add single test filter if specified (overrides package filter)
  if [ -n "$SINGLE_TEST" ]; then
    cmd+=" -e class $SINGLE_TEST"
  fi

  cmd+=" \"$TEST_PACKAGE/$TEST_RUNNER\""
  echo "$cmd"
}

pull_allure_results() {
  # Note: Creates structure at ./$MODULE/build/outputs/allure-results/allure-results/
  # The workflow uploads from the inner allure-results dir
  local allure_dir="./$MODULE/build/outputs/allure-results"
  mkdir -p "$allure_dir" 2>/dev/null || true

  if ! adb shell "run-as $APP_PACKAGE ls /data/data/$APP_PACKAGE/files/allure-results 2>/dev/null" | grep -q "." 2>/dev/null; then
    echo "No Allure results on device"
    return 0
  fi

  echo "Pulling Allure results..."
  # tar extracts "allure-results" dir INTO $allure_dir, creating nested structure
  if adb exec-out "run-as $APP_PACKAGE tar cf - -C /data/data/$APP_PACKAGE/files allure-results 2>/dev/null" 2>/dev/null | tar xf - -C "$allure_dir" 2>/dev/null; then
    echo "Allure results pulled successfully"
    # Show what we got
    find "$allure_dir" -type f 2>/dev/null | head -5 || true
  else
    adb pull "/data/data/$APP_PACKAGE/files/allure-results" "$allure_dir/" 2>/dev/null || true
  fi
}

# --- Main ---
main() {
  parse_args "$@"

  # Print configuration
  echo "=== Matrix Android Test Runner ==="
  echo "Architecture: $ARCH"
  echo "Module: $MODULE"
  echo "Timeout: $TEST_TIMEOUT"
  if [ -n "$NUM_SHARDS" ] && [ -n "$SHARD_INDEX" ]; then
    if [ "$SHARD_INDEX" -lt "$UI_SHARD_COUNT" ]; then
      echo "Sharding: UI tests - shard $SHARD_INDEX of $UI_SHARD_COUNT"
    else
      local non_ui_idx=$((SHARD_INDEX - UI_SHARD_COUNT))
      local non_ui_count=$((NUM_SHARDS - UI_SHARD_COUNT))
      echo "Sharding: Non-UI tests - shard $non_ui_idx of $non_ui_count"
    fi
  else
    echo "Sharding: disabled (running all tests)"
  fi
  if [ -n "$SINGLE_TEST" ]; then
    echo "Single test: $SINGLE_TEST"
  fi
  echo "=================================="

  # Derive architecture suffix
  local arch_suffix
  arch_suffix=$(get_arch_suffix)
  local arch_lower="${arch_suffix,,}"

  # Navigate to android directory
  cd android

  # Set up environment
  export BUILD_ARCH="$ARCH"
  export COLLECT_COVERAGE=true
  export COVERAGE_ENABLED=true

  # Create output directories
  mkdir -p "./$MODULE/build/outputs/androidTest-results/connected/"

  # Check device
  check_device

  # Find APKs
  local app_apk test_apk
  app_apk=$(find_apk "./$MODULE/build/outputs/apk/$arch_lower/debug/app-$arch_lower-debug.apk" \
                     "*-$arch_lower-debug.apk" "App")
  test_apk=$(find_apk "./$MODULE/build/outputs/apk/androidTest/$arch_lower/debug/app-$arch_lower-debug-androidTest.apk" \
                      "*-$arch_lower-debug-androidTest.apk" "Test")

  echo "App APK: $app_apk"
  echo "Test APK: $test_apk"

  # Install APKs
  install_apks "$app_apk" "$test_apk"

  # Determine coverage file path (shard-specific if sharding enabled)
  local coverage_path
  if [ -n "$SHARD_INDEX" ]; then
    coverage_path="/data/data/$APP_PACKAGE/files/coverage_shard_${SHARD_INDEX}.ec"
  else
    coverage_path="/data/data/$APP_PACKAGE/files/coverage.ec"
  fi

  # Create coverage directory on device
  adb shell "run-as $APP_PACKAGE mkdir -p /data/data/$APP_PACKAGE/files" || true

  # Build and run instrument command
  local instrument_cmd
  instrument_cmd=$(build_instrument_command "$coverage_path")
  
  echo "Running tests with timeout $TEST_TIMEOUT..."
  echo "Coverage file: $coverage_path"

  local test_exit_code=0
  timeout "$TEST_TIMEOUT" adb shell "$instrument_cmd" || {
    test_exit_code=$?
    if [ $test_exit_code -eq 124 ]; then
      echo "Error: Tests timed out after $TEST_TIMEOUT"
    else
      echo "Error: Tests failed with exit code $test_exit_code"
    fi
  }

  # Verify coverage file
  echo "Verifying coverage file..."
  if adb shell "run-as $APP_PACKAGE ls -la $coverage_path" 2>/dev/null; then
    echo "Coverage file generated successfully"
  else
    echo "Warning: Coverage file not found at $coverage_path"
    adb shell "run-as $APP_PACKAGE find /data/data/$APP_PACKAGE -name '*.ec' 2>/dev/null" | tr -d '\r' || true
  fi

  # Pull Allure results (non-fatal)
  pull_allure_results || true

  if [ $test_exit_code -ne 0 ]; then
    echo "Tests completed with errors (exit code: $test_exit_code)"
    exit $test_exit_code
  fi

  echo "Tests completed successfully!"
}

main "$@"

