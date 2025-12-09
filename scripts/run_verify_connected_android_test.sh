#!/bin/bash
set -e

# Script to run Android tests in CI using connectedAndroidTest

echo "Starting Android tests..."

# Get the architecture from command line or default to x86_64
ARCH=${1:-x86_64}
MAIN_PROJECT_MODULE=${2:-app}
ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=${3:-5}

# Application package name
APP_PACKAGE="com.github.quarck.calnotify"
TEST_PACKAGE="${APP_PACKAGE}.test"

# Determine the build variant suffix based on architecture
if [ "$ARCH" == "arm64-v8a" ]; then
  ARCH_SUFFIX="Arm64V8a"
else
  ARCH_SUFFIX="X8664"
fi

echo "Running tests for architecture: $ARCH (using suffix: $ARCH_SUFFIX)"

# Uninstall any existing app versions to avoid signature conflicts
# This is necessary when the emulator has a cached app from a previous run
echo "Uninstalling any existing app versions..."
adb uninstall "$APP_PACKAGE" 2>/dev/null || true
adb uninstall "$TEST_PACKAGE" 2>/dev/null || true
sleep 2

# Navigate to the Android directory
cd android

# Set environment variables
export ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL=$ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL
export BUILD_ARCH=$ARCH

# Run the tests with the appropriate architecture suffix
./gradlew -PBUILD_ARCH="$ARCH" \
         -PreactNativeArchitectures="$ARCH" \
         :"$MAIN_PROJECT_MODULE":connected${ARCH_SUFFIX}DebugAndroidTest \
         -Pandroid.testInstrumentationRunnerArguments.class=com.github.quarck.calnotify.calendarmonitor.ComponentIsolationTest \
         --parallel --max-workers=$(nproc) --build-cache

echo "Android tests completed successfully!" 