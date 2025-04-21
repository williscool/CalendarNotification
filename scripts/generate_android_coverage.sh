#!/bin/bash
set -e

# Script to prepare coverage data for JaCoCo report generation
# This script extracts coverage data from the device and puts it where JaCoCo expects it,
# allowing standard Gradle commands to generate the report

echo "Preparing Android coverage data for JaCoCo..."

# Get the architecture from command line or default to x86_64
ARCH=${1:-x86_64}
MAIN_PROJECT_MODULE=${2:-app}
FORCE_PULL=${3:-false}  # New parameter to force pull even if local file exists

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
DEVICE_JACOCO_PATH="/data/data/${APP_PACKAGE}/files/coverage.ec"
LOCAL_COVERAGE_PATH="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME}"
JACOCO_COVERAGE_PATH="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/${COVERAGE_FILE_NAME}"

# Make sure necessary directories exist
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/

# Function to check if a file is empty (zero bytes)
is_file_empty() {
  local file_path="$1"
  if [ ! -s "$file_path" ]; then
    return 0  # True, file is empty
  else
    return 1  # False, file has content
  fi
}

# Always reset the coverage files to avoid using stale data
> "$LOCAL_COVERAGE_PATH"
> "$JACOCO_COVERAGE_PATH"
HAS_COVERAGE=false

# Try to directly pull the coverage file using run-as package dump
echo "Checking for coverage file..."
adb shell "run-as $APP_PACKAGE ls -la ${DEVICE_JACOCO_PATH}" 2>/dev/null

if adb shell "run-as $APP_PACKAGE ls -la ${DEVICE_JACOCO_PATH}" 2>/dev/null; then
  echo "Found coverage file. Trying extraction methods..."
  
  # Make device's external storage writeable just in case
  adb shell "mount -o rw,remount /sdcard" 2>/dev/null || true
  
  # Method 1: Try direct byte streaming with hexdump and xxd
  echo "Method 1: Using hexdump for binary transfer..."
  if adb shell "run-as $APP_PACKAGE hexdump -ve '1/1 \"%.2x\"' ${DEVICE_JACOCO_PATH} > /sdcard/coverage.hex"; then
    adb pull "/sdcard/coverage.hex" "${LOCAL_COVERAGE_PATH}.hex" && \
    cat "${LOCAL_COVERAGE_PATH}.hex" | xxd -r -p > "$LOCAL_COVERAGE_PATH" && {
      echo "Method 1 succeeded!"
      LOCAL_SIZE=$(stat -c%s "$LOCAL_COVERAGE_PATH" 2>/dev/null || echo "0")
      echo "Local file size: $LOCAL_SIZE bytes"
      if [ "$LOCAL_SIZE" -gt "0" ]; then
        HAS_COVERAGE=true
        adb shell "rm /sdcard/coverage.hex" || true
        rm -f "${LOCAL_COVERAGE_PATH}.hex" || true
      fi
    }
  fi
  
  # If first method failed, try direct ADB pull with permissions hack
  if [ "$HAS_COVERAGE" = false ]; then
    echo "Method 2: Using chmod to make coverage file readable..."
    if adb shell "run-as $APP_PACKAGE cp ${DEVICE_JACOCO_PATH} /sdcard/coverage.ec && chmod 644 /sdcard/coverage.ec"; then
      adb pull "/sdcard/coverage.ec" "$LOCAL_COVERAGE_PATH" && {
        echo "Method 2 succeeded!"
        LOCAL_SIZE=$(stat -c%s "$LOCAL_COVERAGE_PATH" 2>/dev/null || echo "0")
        echo "Local file size: $LOCAL_SIZE bytes"
        if [ "$LOCAL_SIZE" -gt "0" ]; then
          HAS_COVERAGE=true
          adb shell "rm /sdcard/coverage.ec" || true
        fi
      }
    fi
  fi
  
  # If both methods failed, try splitting the file into smaller chunks
  if [ "$HAS_COVERAGE" = false ]; then
    echo "Method 3: Splitting file into chunks..."
    CHUNK_SIZE=1024  # 1KB chunks
    TEMP_DIR=$(mktemp -d)
    TOTAL_SIZE=$(adb shell "run-as $APP_PACKAGE stat -c%s ${DEVICE_JACOCO_PATH}" 2>/dev/null || echo "0")
    CHUNKS=$(( ($TOTAL_SIZE + $CHUNK_SIZE - 1) / $CHUNK_SIZE ))
    
    echo "File size: $TOTAL_SIZE bytes, splitting into $CHUNKS chunks"
    
    for i in $(seq 0 $(($CHUNKS - 1))); do
      OFFSET=$(($i * $CHUNK_SIZE))
      echo "Pulling chunk $((i+1))/$CHUNKS (offset: $OFFSET)..."
      
      adb shell "run-as $APP_PACKAGE dd if=${DEVICE_JACOCO_PATH} bs=$CHUNK_SIZE skip=$i count=1 2>/dev/null | base64" > "$TEMP_DIR/chunk_$i.b64"
      cat "$TEMP_DIR/chunk_$i.b64" | base64 --decode > "$TEMP_DIR/chunk_$i.bin"
      
      cat "$TEMP_DIR/chunk_$i.bin" >> "$LOCAL_COVERAGE_PATH"
    done
    
    # Check if we successfully extracted the coverage data
    LOCAL_SIZE=$(stat -c%s "$LOCAL_COVERAGE_PATH" 2>/dev/null || echo "0")
    echo "Method 3 result: Local file size = $LOCAL_SIZE bytes"
    
    if [ "$LOCAL_SIZE" -gt "0" ]; then
      HAS_COVERAGE=true
    fi
    
    # Clean up temporary files
    rm -rf "$TEMP_DIR"
  fi
else
  echo "Could not find coverage file at ${DEVICE_JACOCO_PATH}"
  
  # Look for any coverage files (.ec) in the app's data directory
  echo "Searching for any coverage files in app data..."
  EC_FILES=$(adb shell "run-as $APP_PACKAGE find /data/data/${APP_PACKAGE} -name '*.ec' | sort" 2>/dev/null | tr -d '\r')
  
  if [ -n "$EC_FILES" ]; then
    echo "Found coverage files:"
    echo "$EC_FILES"
    
    # Try to pull the first non-empty coverage file
    for EC_FILE in $EC_FILES; do
      SIZE=$(adb shell "run-as $APP_PACKAGE stat -c%s ${EC_FILE}" 2>/dev/null || echo "0")
      echo "File: $EC_FILE, Size: $SIZE bytes"
      
      if [ "$SIZE" -gt "0" ]; then
        echo "Attempting to pull $EC_FILE using hexdump method..."
        if adb shell "run-as $APP_PACKAGE hexdump -ve '1/1 \"%.2x\"' ${EC_FILE} > /sdcard/coverage.hex"; then
          adb pull "/sdcard/coverage.hex" "${LOCAL_COVERAGE_PATH}.hex" && \
          cat "${LOCAL_COVERAGE_PATH}.hex" | xxd -r -p > "$LOCAL_COVERAGE_PATH" && {
            LOCAL_SIZE=$(stat -c%s "$LOCAL_COVERAGE_PATH" 2>/dev/null || echo "0")
            echo "Local file size: $LOCAL_SIZE bytes"
            if [ "$LOCAL_SIZE" -gt "0" ]; then
              HAS_COVERAGE=true
              adb shell "rm /sdcard/coverage.hex" || true
              rm -f "${LOCAL_COVERAGE_PATH}.hex" || true
              break
            fi
          }
        fi
      fi
    done
  else
    echo "No coverage files found in app data."
  fi
fi

# If we have the coverage file, set up for JaCoCo
if [ "$HAS_COVERAGE" = true ]; then
  echo "Setting up coverage data for JaCoCo..."
  
  # Copy to JaCoCo expected location
  cp "$LOCAL_COVERAGE_PATH" "$JACOCO_COVERAGE_PATH"
  
  # Verify file was copied correctly
  JACOCO_FILE_SIZE=$(stat -c%s "$JACOCO_COVERAGE_PATH" 2>/dev/null || echo "unknown")
  echo "JaCoCo coverage file size: $JACOCO_FILE_SIZE bytes"
  
  if is_file_empty "$JACOCO_COVERAGE_PATH"; then
    echo "❌ Error: JaCoCo coverage file is empty after copying. Coverage report will be empty."
    exit 1
  fi
  
  # Create required test execution indicator file
  touch ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/TESTS_EXECUTED
  
  echo "✅ Coverage data prepared successfully! ($(du -h "$JACOCO_COVERAGE_PATH" | cut -f1) bytes)"
  echo "You can now run JaCoCo report generation with standard Gradle commands:"
  echo "  cd android && ./gradlew jacocoAndroidTestReport"
else
  echo "❌ Failed to find or extract valid coverage data."
  echo "Please run tests first with: ./scripts/run_android_tests.sh"
  exit 1
fi

echo "Coverage data preparation completed"