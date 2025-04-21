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
SINGLE_TEST=${5}  # Optional parameter for running a single test

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

# Add more environment variables to better handle coverage with JaCoCo
export COLLECT_COVERAGE=true
export COVERAGE_ENABLED=true

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

# Create a more unique coverage file path that JaCoCo can recognize
COVERAGE_FILE_PATH="/data/data/${APP_PACKAGE}/files/coverage.ec"

# Prepare test command - with more explicit coverage flags
INSTRUMENT_COMMAND="am instrument -w -r \
  -e debug false \
  -e collect.coverage true \
  -e coverage true \
  -e coverageFile \"${COVERAGE_FILE_PATH}\" \
  -e coverageDataFileLocation \"${COVERAGE_FILE_PATH}\" \
  -e jacoco true \
  -e outputFormat \"xml\" \
  -e jaco-agent.destfile \"${COVERAGE_FILE_PATH}\" \
  -e jaco-agent.includes \"com.github.quarck.calnotify.*\" \
  -e listener \"de.schroepf.androidxmlrunlistener.XmlRunListener\" \
  -e resultFile \"/data/local/tmp/test-results.xml\""

# If a specific test is specified, add it to the command
if [ -n "$SINGLE_TEST" ]; then
  echo "Running single test: $SINGLE_TEST"
  
  # Parse the test class and method (if provided)
  if [[ "$SINGLE_TEST" == *"#"* ]]; then
    # Format is className#methodName
    TEST_CLASS=${SINGLE_TEST%#*}
    TEST_METHOD=${SINGLE_TEST#*#}
    INSTRUMENT_COMMAND="$INSTRUMENT_COMMAND \
      -e class ${TEST_CLASS}#${TEST_METHOD}"
  else
    # Just class name, run all methods in that class
    INSTRUMENT_COMMAND="$INSTRUMENT_COMMAND \
      -e class ${SINGLE_TEST}"
  fi
  
  echo "Test filter: $SINGLE_TEST"
fi

# Complete the command with the package name and test runner
INSTRUMENT_COMMAND="$INSTRUMENT_COMMAND \"${TEST_PACKAGE}/${TEST_RUNNER}\""

# Execute the test command
timeout $TEST_TIMEOUT adb shell "$INSTRUMENT_COMMAND" || {
  TEST_EXIT_CODE=$?
  if [ $TEST_EXIT_CODE -eq 124 ]; then
    echo "Error: Tests timed out after $TEST_TIMEOUT"
  else
    echo "Error: Tests failed with exit code $TEST_EXIT_CODE"
  fi
  exit $TEST_EXIT_CODE
}

# Check if the coverage file was generated (just verification, don't pull)
echo "Verifying coverage file was generated at: ${COVERAGE_FILE_PATH}"
if adb shell "run-as $APP_PACKAGE ls -la ${COVERAGE_FILE_PATH}" 2>/dev/null; then
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

# After the tests have run successfully, create a minimal XML test result file manually
echo "Generating XML test results from instrumentation output..."

# Define paths for test results
LOCAL_TEST_RESULT_PATH="./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${APP_PACKAGE}.xml"
DORNY_TEST_RESULT_PATH="./$MAIN_PROJECT_MODULE/build/outputs/connected/TEST-${APP_PACKAGE}.xml"

# Ensure the directories exist
mkdir -p "$(dirname "$LOCAL_TEST_RESULT_PATH")"
mkdir -p "$(dirname "$DORNY_TEST_RESULT_PATH")"

# Generate a valid XML test result file using the instrumentation output
{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<testsuites>'
  echo '  <testsuite name="AndroidTests" tests="1" failures="0" errors="0" skipped="0" timestamp="'"$(date -u +"%Y-%m-%dT%H:%M:%S")"'" hostname="localhost" time="1">'

  # Parse the instrumentation output to get test results
  TESTS_COUNT=0
  FAILURES_COUNT=0
  
  while IFS= read -r LINE; do
    if [[ "$LINE" == *"INSTRUMENTATION_STATUS: test="* ]]; then
      TEST_NAME=${LINE#*INSTRUMENTATION_STATUS: test=}
      echo "    <testcase classname=\"$APP_PACKAGE\" name=\"$TEST_NAME\" time=\"1\">"
      echo "    </testcase>"
      TESTS_COUNT=$((TESTS_COUNT + 1))
    fi
    
    if [[ "$LINE" == *"INSTRUMENTATION_STATUS_CODE: 0"* ]]; then
      # Test passed
      : # No action needed for passed tests
    fi
    
    if [[ "$LINE" == *"INSTRUMENTATION_STATUS_CODE: -2"* || "$LINE" == *"INSTRUMENTATION_STATUS_CODE: -3"* || "$LINE" == *"INSTRUMENTATION_STATUS_CODE: -4"* ]]; then
      # Test failed or error
      FAILURES_COUNT=$((FAILURES_COUNT + 1))
      echo "      <failure message=\"Test failed\" type=\"AssertionError\">Test failure</failure>"
    fi
  done < <(adb logcat -d | grep "INSTRUMENTATION_")
  
  # Update testsuite attributes
  XML_CONTENT=$(cat "$LOCAL_TEST_RESULT_PATH")
  XML_CONTENT=${XML_CONTENT/tests="1"/tests="$TESTS_COUNT"}
  XML_CONTENT=${XML_CONTENT/failures="0"/failures="$FAILURES_COUNT"}
  echo "$XML_CONTENT" > "$LOCAL_TEST_RESULT_PATH"
  
  echo '  </testsuite>'
  echo '</testsuites>'
} > "$LOCAL_TEST_RESULT_PATH"

# Copy to the second expected path
cp "$LOCAL_TEST_RESULT_PATH" "$DORNY_TEST_RESULT_PATH"

echo "✅ Generated XML test results at:"
echo "  - $LOCAL_TEST_RESULT_PATH"
echo "  - $DORNY_TEST_RESULT_PATH"

# If tests failed (non-zero exit code), exit with the same code
if [ $TEST_EXIT_CODE -ne 0 ]; then
  echo "Android tests completed with errors. Exit code: $TEST_EXIT_CODE"
  exit $TEST_EXIT_CODE
fi

echo "Android tests completed successfully!"