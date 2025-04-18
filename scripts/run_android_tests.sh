#!/bin/bash
set -e

# Script to run Android tests in CI

echo "Starting Android tests..."

# Get the architecture from command line or default to x86_64
ARCH=${1:-x86_64}
MAIN_PROJECT_MODULE=${2:-app}
ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=${3:-5}

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

echo "APKs built, starting instrumentation tests via ADB..."

# Application package name
APP_PACKAGE="com.github.quarck.calnotify"
# Test package name is typically APP_PACKAGE.test but may vary
TEST_PACKAGE="${APP_PACKAGE}.androidTest"
# Test runner from build.gradle
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"

# Create coverage file name based on architecture
COVERAGE_FILE_NAME="${ARCH_SUFFIX}DebugAndroidTest.ec"

# Find the app APK and test APK
APP_APK=$(find ./$MAIN_PROJECT_MODULE/build/outputs/apk/${ARCH_SUFFIX,,}/debug -name "*-debug.apk" | head -1)
TEST_APK=$(find ./$MAIN_PROJECT_MODULE/build/outputs/apk/androidTest/${ARCH_SUFFIX,,}/debug -name "*-debug-androidTest.apk" | head -1)

if [ -z "$APP_APK" ]; then
  echo "Error: App APK not found"
  exit 1
fi

if [ -z "$TEST_APK" ]; then
  echo "Error: Test APK not found"
  exit 1
fi

echo "Found App APK: $APP_APK"
echo "Found Test APK: $TEST_APK"

# Uninstall previous versions if they exist (don't fail if they don't)
adb uninstall $APP_PACKAGE || true
adb uninstall $TEST_PACKAGE || true

# Install the app and test APKs
echo "Installing app APK..."
adb install -r "$APP_APK"
echo "Installing test APK..."
adb install -r "$TEST_APK"

# Run the tests using adb directly
echo "Running instrumentation tests..."
adb shell am instrument -w -r \
  -e debug false \
  -e coverage true \
  -e coverageFile "/sdcard/${COVERAGE_FILE_NAME}" \
  -e outputFormat "xml" \
  -e resultFile "/sdcard/test-results.xml" \
  "${TEST_PACKAGE}/${TEST_RUNNER}"

# Pull coverage data from the device
echo "Tests completed, pulling coverage data..."
adb pull /sdcard/${COVERAGE_FILE_NAME} ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/

# Pull test results XML
echo "Pulling test result XML file..."
adb pull /sdcard/test-results.xml ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${ARCH_SUFFIX}.xml

# Copy coverage file to the expected location for JaCoCo report
cp ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME} \
   ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/

# Create symbolic link to match expected test results path
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/connected
ln -sf "../androidTest-results/connected" ./$MAIN_PROJECT_MODULE/build/outputs/connected/

# On successful test run, create a placeholder file that can be used to check if tests ran
touch ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/TESTS_EXECUTED

echo "Android tests completed successfully!" 