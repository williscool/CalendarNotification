#!/bin/bash
set -e

# Script to run Android tests in CI
# ONLY responsible for running tests and generating coverage files on device

echo "Starting Android tests..."

# Get the architecture from command line or default to x86_64
ARCH=${1:-x86_64}
MAIN_PROJECT_MODULE=${2:-app}
ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=${3:-5}
TEST_TIMEOUT=${4:-30m}  # Increased default timeout to 30 minutes

# Determine the build variant suffix based on architecture
if [ "$ARCH" == "arm64-v8a" ]; then
  ARCH_SUFFIX="Arm64V8a"
else
  ARCH_SUFFIX="X8664"
fi

echo "Running tests for architecture: $ARCH (using suffix: $ARCH_SUFFIX)"

# Navigate to the Android directory
cd android

# Set environment variables
export ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=$ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL
export BUILD_ARCH=$ARCH

# Make sure we have the results directory
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/

# Check if device is connected
echo "Checking for connected devices..."
DEVICE_COUNT=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "Error: No connected devices found. Please connect a device or start an emulator."
  exit 1
fi
echo "Found $DEVICE_COUNT connected device(s)"

echo "Starting instrumentation tests via ADB..."

# Application package name
APP_PACKAGE="com.github.quarck.calnotify"
# Test package name is APP_PACKAGE.test
TEST_PACKAGE="${APP_PACKAGE}.test"
# Test runner from build.gradle
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"

# Create coverage file name based on architecture
COVERAGE_FILE_NAME="${ARCH_SUFFIX}DebugAndroidTest.ec"

# The APKs should be in these specific locations
APP_APK="./$MAIN_PROJECT_MODULE/build/outputs/apk/${ARCH_SUFFIX,,}/debug/app-${ARCH_SUFFIX,,}-debug.apk"
TEST_APK="./$MAIN_PROJECT_MODULE/build/outputs/apk/androidTest/${ARCH_SUFFIX,,}/debug/app-${ARCH_SUFFIX,,}-debug-androidTest.apk"

# Verify that they exist
if [ ! -f "$APP_APK" ]; then
  echo "Error: App APK not found at expected path: $APP_APK"
  echo "Looking for alternatives..."
  APP_APK=$(find ./$MAIN_PROJECT_MODULE/build/outputs -name "*-${ARCH_SUFFIX,,}-debug.apk" 2>/dev/null | head -1)
  
  if [ -z "$APP_APK" ]; then
    echo "Error: No app APK found in build outputs"
    echo "Current directory: $(pwd)"
    echo "Available APK files:"
    find ./$MAIN_PROJECT_MODULE/build/outputs -type f -name "*.apk" | sort
    exit 1
  fi
fi

if [ ! -f "$TEST_APK" ]; then
  echo "Error: Test APK not found at expected path: $TEST_APK"
  echo "Looking for alternatives..."
  TEST_APK=$(find ./$MAIN_PROJECT_MODULE/build/outputs -name "*-${ARCH_SUFFIX,,}-debug-androidTest.apk" 2>/dev/null | head -1)
  
  if [ -z "$TEST_APK" ]; then
    echo "Error: No test APK found in build outputs"
    echo "Current directory: $(pwd)"
    echo "Available APK files:"
    find ./$MAIN_PROJECT_MODULE/build/outputs -type f -name "*.apk" | sort
    exit 1
  fi
fi

echo "Found App APK: $APP_APK"
echo "Found Test APK: $TEST_APK"

# Uninstall previous versions if they exist (don't fail if they don't)
echo "Uninstalling any existing app versions..."
adb uninstall $APP_PACKAGE || true
adb uninstall $TEST_PACKAGE || true

# Wait a moment to ensure uninstall completes
sleep 2

# Install the app and test APKs
echo "Installing app APK..."
adb install -r "$APP_APK"
echo "Installing test APK..."
adb install -r "$TEST_APK"

# Define path for coverage data on device
DEVICE_COVERAGE_PATH="/data/data/${APP_PACKAGE}/coverage/${COVERAGE_FILE_NAME}"

# Create the coverage directory on device
adb shell "run-as $APP_PACKAGE mkdir -p /data/data/${APP_PACKAGE}/coverage" || true

# Run the tests using adb directly with timeout and specified coverage path
echo "Running instrumentation tests with $TEST_TIMEOUT timeout..."
TEST_EXIT_CODE=0
timeout $TEST_TIMEOUT adb shell am instrument -w -r \
  -e debug false \
  -e coverage true \
  -e coverageFile "${DEVICE_COVERAGE_PATH}" \
  -e outputFormat "xml" \
  -e resultFile "/data/local/tmp/test-results.xml" \
  "${TEST_PACKAGE}/${TEST_RUNNER}" || {
    TEST_EXIT_CODE=$?
    if [ $TEST_EXIT_CODE -eq 124 ]; then
      echo "Error: Tests timed out after $TEST_TIMEOUT"
    else
      echo "Error: Tests failed with exit code $TEST_EXIT_CODE"
    fi
    exit $TEST_EXIT_CODE
  }

# Check if the coverage file was generated (just verification, don't pull)
echo "Verifying coverage file was generated at: ${DEVICE_COVERAGE_PATH}"
if adb shell "run-as $APP_PACKAGE ls -la ${DEVICE_COVERAGE_PATH}" 2>/dev/null; then
  echo "✅ Coverage file generated successfully!"
  echo "To pull coverage data and prepare for JaCoCo, run:"
  echo "  ./scripts/generate_android_coverage.sh ${ARCH} ${MAIN_PROJECT_MODULE}"
else
  echo "⚠️ Tests completed but no coverage file was generated at expected location"
  echo "Checking for coverage files in other locations..."
  EC_FILES=$(adb shell "run-as $APP_PACKAGE find /data/data/${APP_PACKAGE} -name '*.ec' 2>/dev/null" | tr -d '\r')
  
  if [ -n "$EC_FILES" ]; then
    echo "Found coverage files in app data: $EC_FILES"
    echo "Run generate_android_coverage.sh to pull these files and prepare for JaCoCo"
  else
    echo "No coverage files found in app data."
  fi
fi

# If tests failed (non-zero exit code), exit with the same code
if [ $TEST_EXIT_CODE -ne 0 ]; then
  echo "Android tests completed with errors. Exit code: $TEST_EXIT_CODE"
  exit $TEST_EXIT_CODE
fi

echo "Android tests completed successfully!"