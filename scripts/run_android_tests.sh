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

# Define accessible paths for coverage data
DEVICE_COVERAGE_PATH="/data/data/${APP_PACKAGE}/coverage/${COVERAGE_FILE_NAME}"
EXTERNAL_COVERAGE_PATH="/sdcard/${COVERAGE_FILE_NAME}"

# Create the coverage directory
adb shell "run-as $APP_PACKAGE mkdir -p /data/data/${APP_PACKAGE}/coverage" || true

# Run the tests using adb directly with timeout and specified coverage path
echo "Running instrumentation tests with $TEST_TIMEOUT timeout..."
INSTRUMENTATION_FAILED=0
timeout $TEST_TIMEOUT adb shell am instrument -w -r \
  -e debug false \
  -e coverage true \
  -e coverageFile "${DEVICE_COVERAGE_PATH}" \
  -e outputFormat "xml" \
  -e resultFile "/data/local/tmp/test-results.xml" \
  "${TEST_PACKAGE}/${TEST_RUNNER}" || {
    INSTRUMENTATION_FAILED=$?
    if [ $INSTRUMENTATION_FAILED -eq 124 ]; then
      echo "Error: Tests timed out after $TEST_TIMEOUT"
    else
      echo "Error: Tests failed with exit code $INSTRUMENTATION_FAILED"
    fi
    
    # Try to pull any partial test results that might exist
    adb pull /data/local/tmp/test-results.xml ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${ARCH_SUFFIX}-partial.xml || true
  }

# Check if the coverage file was generated
echo "Checking if coverage file was generated at: ${DEVICE_COVERAGE_PATH}"
if adb shell "run-as $APP_PACKAGE ls -la ${DEVICE_COVERAGE_PATH}" 2>/dev/null; then
  echo "Coverage file exists! Attempting to extract it..."
  
  # Copy from app private storage to external storage so we can pull it
  adb shell "run-as $APP_PACKAGE cat ${DEVICE_COVERAGE_PATH} > ${EXTERNAL_COVERAGE_PATH}" || {
    echo "Failed to copy coverage file to external storage."
  }
  
  # Pull the coverage file
  adb pull "${EXTERNAL_COVERAGE_PATH}" ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME} && {
    echo "Successfully pulled coverage file!"
    # Clean up
    adb shell "rm ${EXTERNAL_COVERAGE_PATH}" || true
  } || {
    echo "Failed to pull coverage file from external storage."
  }
else
  echo "No coverage file found at the expected location. Let's try a broader search..."
  # Find any .ec files in app data
  EC_FILES=$(adb shell "run-as $APP_PACKAGE find /data/data/${APP_PACKAGE} -name '*.ec' 2>/dev/null" | tr -d '\r')
  
  if [ -n "$EC_FILES" ]; then
    echo "Found coverage files in app data: $EC_FILES"
    for EC_FILE in $EC_FILES; do
      echo "Attempting to pull $EC_FILE..."
      EC_FILENAME=$(basename "$EC_FILE")
      adb shell "run-as $APP_PACKAGE cat $EC_FILE > /sdcard/$EC_FILENAME" && \
      adb pull "/sdcard/$EC_FILENAME" ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME} && {
        echo "Successfully pulled coverage file from $EC_FILE"
        adb shell "rm /sdcard/$EC_FILENAME" || true
        break
      } || {
        echo "Failed to pull $EC_FILE"
      }
    done
  else
    echo "No coverage files found in app data."
  fi
fi

# Pull test results XML
echo "Pulling test result XML file..."
adb pull "/data/local/tmp/test-results.xml" ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${ARCH_SUFFIX}.xml || {
  echo "Warning: Failed to pull test results file directly."
}

# Check if we have the coverage file now
if [ -f "./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME}" ]; then
  echo "Coverage file found locally. Proceeding with JaCoCo report generation..."
  
  # Copy coverage file to the expected location for JaCoCo
  mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/
  cp ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME} \
     ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/

  # Create symbolic link to match expected test results path
  mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/connected
  ln -sf "../androidTest-results/connected" ./$MAIN_PROJECT_MODULE/build/outputs/connected/

  # On successful test run, create a placeholder file that can be used to check if tests ran
  touch ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/TESTS_EXECUTED
  
  # Run the Gradle JaCoCo report generation
  echo "Running JaCoCo report generation..."
  ./gradlew :$MAIN_PROJECT_MODULE:jacocoAndroidTestReport --info
  
  echo "Coverage report should be available at: ./$MAIN_PROJECT_MODULE/build/reports/jacoco/jacocoAndroidTestReport/"
  
  # Check if the report was generated
  if [ -d "./$MAIN_PROJECT_MODULE/build/reports/jacoco/jacocoAndroidTestReport/" ]; then
    echo "✅ JaCoCo report successfully generated"
  else
    echo "⚠️ JaCoCo report generation may have failed"
  fi
else
  echo "❌ Failed to find or generate coverage data"
  
  # As a last resort, try to generate a report even without coverage data
  # This might work if the build system can find coverage from other sources
  echo "Attempting to generate JaCoCo report without direct coverage data..."
  
  # Create placeholder file to indicate tests were run
  touch ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/TESTS_EXECUTED
  
  # Create symbolic link to test results path
  mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/connected
  ln -sf "../androidTest-results/connected" ./$MAIN_PROJECT_MODULE/build/outputs/connected/
  
  # Try to generate the report
  ./gradlew :$MAIN_PROJECT_MODULE:jacocoAndroidTestReport --info || {
    echo "❌ JaCoCo report generation failed"
  }
fi

# If instrumentation failed, exit with the same code
if [ $INSTRUMENTATION_FAILED -ne 0 ]; then
  echo "Android tests completed with errors. Exit code: $INSTRUMENTATION_FAILED"
  exit $INSTRUMENTATION_FAILED
fi

echo "Android tests completed successfully!"