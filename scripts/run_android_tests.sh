#!/bin/bash
set -e

# Script to run Android tests in CI

echo "Starting Android tests..."

# Get the architecture from command line or default to x86_64
ARCH=${1:-x86_64}
MAIN_PROJECT_MODULE=${2:-app}
ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=${3:-5}
TEST_TIMEOUT=${4:-10m}

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

# Make sure we have the code coverage directory and test results directory
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/
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

# Run the tests using adb directly with timeout
echo "Running instrumentation tests with $TEST_TIMEOUT timeout..."
timeout $TEST_TIMEOUT adb shell am instrument -w -r \
  -e debug false \
  -e coverage true \
  -e coverageFile "/sdcard/${COVERAGE_FILE_NAME}" \
  -e outputFormat "xml" \
  -e resultFile "/sdcard/test-results.xml" \
  "${TEST_PACKAGE}/${TEST_RUNNER}" || {
    EXIT_CODE=$?
    if [ $EXIT_CODE -eq 124 ]; then
      echo "Error: Tests timed out after $TEST_TIMEOUT"
    else
      echo "Error: Tests failed with exit code $EXIT_CODE"
    fi
    
    # Try to pull any partial test results that might exist
    adb pull /sdcard/test-results.xml ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${ARCH_SUFFIX}-partial.xml || true
    exit $EXIT_CODE
  }

# Pull coverage data from the device
echo "Tests completed, pulling coverage data..."
adb pull /sdcard/${COVERAGE_FILE_NAME} ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/ || {
  echo "Warning: Failed to pull coverage file. It may not have been generated."
}

# Pull test results XML
echo "Pulling test result XML file..."
adb pull /sdcard/test-results.xml ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${ARCH_SUFFIX}.xml || {
  echo "Warning: Failed to pull test results file."
}

# Copy coverage file to the expected location for JaCoCo report
if [ -f "./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME}" ]; then
  echo "Copying coverage data for JaCoCo processing..."
  mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/
  cp ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME} \
     ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/
fi

# Create symbolic link to match expected test results path
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/connected
ln -sf "../androidTest-results/connected" ./$MAIN_PROJECT_MODULE/build/outputs/connected/

# On successful test run, create a placeholder file that can be used to check if tests ran
touch ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/TESTS_EXECUTED

echo "Android tests completed successfully!"