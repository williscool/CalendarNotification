#!/bin/bash
set -e

# Script to prepare coverage data for JaCoCo report generation
# This script extracts coverage data from the device and puts it where JaCoCo expects it,
# allowing standard Gradle commands to generate the report

echo "Preparing Android coverage data for JaCoCo..."

# Get the architecture from command line or default to x86_64
ARCH=${1:-x86_64}
MAIN_PROJECT_MODULE=${2:-app}

# Determine the build variant suffix based on architecture
if [ "$ARCH" == "arm64-v8a" ]; then
  ARCH_SUFFIX="Arm64V8a"
else
  ARCH_SUFFIX="X8664"
fi

echo "Processing coverage for architecture: $ARCH (using suffix: $ARCH_SUFFIX)"

# Navigate to the Android directory
cd android

# Create coverage file name based on architecture
COVERAGE_FILE_NAME="${ARCH_SUFFIX}DebugAndroidTest.ec"

# Application package name
APP_PACKAGE="com.github.quarck.calnotify"

# Check if device is connected
echo "Checking for connected devices..."
DEVICE_COUNT=$(adb devices | grep -v "List" | grep -v "^$" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "Error: No connected devices found. Please connect a device or start an emulator."
  exit 1
fi
echo "Found $DEVICE_COUNT connected device(s)"

# Define paths for coverage data
DEVICE_COVERAGE_PATH="/data/data/${APP_PACKAGE}/coverage/${COVERAGE_FILE_NAME}"
EXTERNAL_COVERAGE_PATH="/sdcard/${COVERAGE_FILE_NAME}"
LOCAL_COVERAGE_PATH="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME}"
JACOCO_COVERAGE_PATH="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/${COVERAGE_FILE_NAME}"

# Make sure necessary directories exist
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/

# Check if we already have the coverage file locally
if [ -f "$LOCAL_COVERAGE_PATH" ]; then
  echo "Coverage file already exists locally. Using existing file."
  HAS_COVERAGE=true
else
  echo "No local coverage file found. Attempting to retrieve from device..."
  HAS_COVERAGE=false
  
  # Check if the coverage file exists in app's private storage
  if adb shell "run-as $APP_PACKAGE ls -la ${DEVICE_COVERAGE_PATH}" 2>/dev/null; then
    echo "Coverage file found on device! Extracting..."
    
    # Copy from app private storage to external storage so we can pull it
    adb shell "run-as $APP_PACKAGE cat ${DEVICE_COVERAGE_PATH} > ${EXTERNAL_COVERAGE_PATH}" && \
    adb pull "${EXTERNAL_COVERAGE_PATH}" "$LOCAL_COVERAGE_PATH" && {
      echo "Successfully pulled coverage file from device!"
      adb shell "rm ${EXTERNAL_COVERAGE_PATH}" || true
      HAS_COVERAGE=true
    } || {
      echo "Failed to pull coverage file from app data."
    }
  else
    echo "No coverage file found at expected location. Searching app data directory..."
    
    # Try to find any .ec files in the app data
    EC_FILES=$(adb shell "run-as $APP_PACKAGE find /data/data/${APP_PACKAGE} -name '*.ec' 2>/dev/null" | tr -d '\r')
    
    if [ -n "$EC_FILES" ]; then
      echo "Found coverage files in app data: $EC_FILES"
      for EC_FILE in $EC_FILES; do
        echo "Attempting to pull $EC_FILE..."
        EC_FILENAME=$(basename "$EC_FILE")
        adb shell "run-as $APP_PACKAGE cat $EC_FILE > /sdcard/$EC_FILENAME" && \
        adb pull "/sdcard/$EC_FILENAME" "$LOCAL_COVERAGE_PATH" && {
          echo "Successfully pulled coverage file from $EC_FILE"
          adb shell "rm /sdcard/$EC_FILENAME" || true
          HAS_COVERAGE=true
          break
        } || {
          echo "Failed to pull $EC_FILE"
        }
      done
    else
      echo "No coverage files found in app data."
    fi
  fi
fi

# Pull test results XML if it exists
echo "Checking for test results XML on device..."
if adb shell "ls /data/local/tmp/test-results.xml" 2>/dev/null; then
  echo "Test results XML found. Pulling from device..."
  adb pull "/data/local/tmp/test-results.xml" ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${ARCH_SUFFIX}.xml || {
    echo "Warning: Failed to pull test results file."
  }
fi

# If we have the coverage file, set up for JaCoCo
if [ "$HAS_COVERAGE" = true ]; then
  echo "Setting up coverage data for JaCoCo..."
  
  # Copy to JaCoCo expected location
  cp "$LOCAL_COVERAGE_PATH" "$JACOCO_COVERAGE_PATH"
  
  # Create required test execution indicator file
  touch ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/TESTS_EXECUTED
  
  # Create symbolic link to test results directory if needed
  mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/connected
  ln -sf "../androidTest-results/connected" ./$MAIN_PROJECT_MODULE/build/outputs/connected/ 2>/dev/null || true
  
  echo "✅ Coverage data prepared successfully!"
  echo "You can now run JaCoCo report generation with standard Gradle commands:"
  echo "  cd android && ./gradlew jacocoAndroidTestReport"
else
  echo "❌ Failed to find or extract coverage data."
  echo "Please run tests first with: ./scripts/run_android_tests.sh"
  exit 1
fi

echo "Coverage data preparation completed"