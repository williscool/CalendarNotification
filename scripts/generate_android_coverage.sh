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

# Define paths for coverage data and test results
DEVICE_COVERAGE_PATH="/data/data/${APP_PACKAGE}/coverage/${COVERAGE_FILE_NAME}"
DEVICE_JACOCO_PATH="/data/data/${APP_PACKAGE}/files/coverage.ec"
DEVICE_TEST_RESULT_PATH="/data/local/tmp/test-results.xml"
LOCAL_COVERAGE_PATH="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME}"
JACOCO_COVERAGE_PATH="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/${COVERAGE_FILE_NAME}"
LOCAL_TEST_RESULT_PATH="./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${APP_PACKAGE}.xml"

# Make sure necessary directories exist
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/
# Additional directory for dorny/test-reporter expected paths
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/connected/

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

  echo "File exported to: $JACOCO_COVERAGE_PATH"
  
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

# Now handle the XML test results for dorny/test-reporter
echo "Processing XML test results for test reporting..."

# Define paths for test results
DEVICE_TEST_RESULT_PATH="/storage/emulated/0/Android/data/com.github.quarck.calnotify/files/report-0.xml"
APP_CACHE_XML_PATH="/data/data/${APP_PACKAGE}/cache/test-results.xml"
LOCAL_TEST_RESULT_PATH="./$MAIN_PROJECT_MODULE/build/outputs/androidTest-results/connected/TEST-${APP_PACKAGE}.xml"
DORNY_TEST_RESULT_PATH="./$MAIN_PROJECT_MODULE/build/outputs/connected/TEST-${APP_PACKAGE}.xml"

# Ensure directories exist
mkdir -p "$(dirname "$LOCAL_TEST_RESULT_PATH")"
mkdir -p "$(dirname "$DORNY_TEST_RESULT_PATH")"

# First try direct pull (if we have permission)
echo "Attempting to pull XML test results directly..."
if adb pull "$DEVICE_TEST_RESULT_PATH" "$LOCAL_TEST_RESULT_PATH" 2>/dev/null; then
  echo "✅ Successfully pulled test results directly!"
  XML_RETRIEVED=true
else
  echo "⚠️ Direct pull failed. Trying alternative methods..."
  XML_RETRIEVED=false
  
  # Check if the file exists in the app's cache directory (XmlRunListener fallback location)
  echo "Checking for XML in app's cache directory..."
  if adb shell "run-as $APP_PACKAGE ls -la $APP_CACHE_XML_PATH" 2>/dev/null | grep -q "test-results.xml"; then
    echo "Found XML file in app's cache directory!"
    
    # Extract using binary chunking, similar to coverage file approach
    echo "Extracting XML file using chunking method..."
    TEMP_DIR=$(mktemp -d)
    XML_HEX_FILE="$TEMP_DIR/test-results.hex"
    
    if adb shell "run-as $APP_PACKAGE cat $APP_CACHE_XML_PATH | xxd -p" > "$XML_HEX_FILE"; then
      echo "Converted XML to hex format"
      xxd -r -p < "$XML_HEX_FILE" > "$LOCAL_TEST_RESULT_PATH" && {
        echo "✅ Successfully extracted XML via hex conversion!"
        XML_RETRIEVED=true
        rm -rf "$TEMP_DIR"
      }
    else
      echo "xxd approach failed, trying hexdump..."
      if adb shell "run-as $APP_PACKAGE hexdump -ve '1/1 \"%.2x\"' $APP_CACHE_XML_PATH" > "$XML_HEX_FILE"; then
        echo "Converted XML to hex format using hexdump"
        xxd -r -p < "$XML_HEX_FILE" > "$LOCAL_TEST_RESULT_PATH" && {
          echo "✅ Successfully extracted XML via hexdump method!"
          XML_RETRIEVED=true
          rm -rf "$TEMP_DIR"
        }
      fi
    fi
    
    # If binary methods failed, try Base64
    if [ "$XML_RETRIEVED" = false ]; then
      echo "Trying Base64 approach..."
      if adb shell "run-as $APP_PACKAGE cat $APP_CACHE_XML_PATH | base64" > "$TEMP_DIR/test-results.b64"; then
        base64 --decode < "$TEMP_DIR/test-results.b64" > "$LOCAL_TEST_RESULT_PATH" && {
          echo "✅ Successfully extracted XML via Base64 method!"
          XML_RETRIEVED=true
          rm -rf "$TEMP_DIR"
        }
      fi
    fi
  fi
fi

# If we still don't have the file, search the device for test result XML files
if [ "$XML_RETRIEVED" = false ]; then
  echo "Searching for XML files in app data directories..."
  
  # Only look for XML files in specific locations, and exclude shared_prefs
  SEARCH_LOCATIONS=(
    "/data/data/$APP_PACKAGE/files"
    "/data/data/$APP_PACKAGE/cache"
    "/data/data/$APP_PACKAGE/app_"
    "/data/data/$APP_PACKAGE/databases"
    "/data/data/$APP_PACKAGE/no_backup"
    "/data/local/tmp"
    "/sdcard/Android/data/$APP_PACKAGE"
  )
  
  XML_FILES=""
  for LOCATION in "${SEARCH_LOCATIONS[@]}"; do
    FOUND_FILES=$(adb shell "run-as $APP_PACKAGE find $LOCATION -name '*.xml' 2>/dev/null" | tr -d '\r')
    if [ -n "$FOUND_FILES" ]; then
      XML_FILES="$XML_FILES $FOUND_FILES"
    fi
  done
  
  # Also look for files with "test" or "report" in the name
  FOUND_FILES=$(adb shell "run-as $APP_PACKAGE find /data/data/$APP_PACKAGE -name '*test*.xml' -o -name '*report*.xml' 2>/dev/null" | tr -d '\r')
  if [ -n "$FOUND_FILES" ]; then
    XML_FILES="$XML_FILES $FOUND_FILES"
  fi
  
  if [ -n "$XML_FILES" ]; then
    echo "Found XML files to check:"
    echo "$XML_FILES"
    
    # Try each XML file and see if it has JUnit test results format (not app preferences)
    for XML_FILE in $XML_FILES; do
      echo "Checking if $XML_FILE contains test results..."
      # Check for specific JUnit XML elements, not just any XML
      if adb shell "run-as $APP_PACKAGE grep -E '<testsuite|<testcase' $XML_FILE" 2>/dev/null | grep -q -E 'testsuite|testcase'; then
        echo "📋 Found JUnit test results at $XML_FILE"
        
        # Try Base64 extraction
        TEMP_FILE=$(mktemp)
        if adb shell "run-as $APP_PACKAGE cat $XML_FILE | base64" > "$TEMP_FILE.b64"; then
          base64 --decode < "$TEMP_FILE.b64" > "$LOCAL_TEST_RESULT_PATH" && {
            echo "✅ Successfully extracted XML from $XML_FILE!"
            XML_RETRIEVED=true
            rm -f "$TEMP_FILE.b64" "$TEMP_FILE"
            break
          }
        fi
        
        # If Base64 fails, try hexdump
        if [ "$XML_RETRIEVED" = false ]; then
          if adb shell "run-as $APP_PACKAGE hexdump -ve '1/1 \"%.2x\"' $XML_FILE" > "$TEMP_FILE.hex"; then
            xxd -r -p < "$TEMP_FILE.hex" > "$LOCAL_TEST_RESULT_PATH" && {
              echo "✅ Successfully extracted XML from $XML_FILE using hexdump!"
              XML_RETRIEVED=true
              rm -f "$TEMP_FILE.hex" "$TEMP_FILE"
              break
            }
          fi
        fi
        
        rm -f "$TEMP_FILE.b64" "$TEMP_FILE.hex" "$TEMP_FILE"
      fi
    done
  fi
fi

# Check if the XML file has valid JUnit test report content
is_valid_junit_xml() {
  local xml_file="$1"
  
  # Check if file exists and is not empty
  if [ ! -f "$xml_file" ] || [ ! -s "$xml_file" ]; then
    return 1
  fi
  
  # Check for essential JUnit XML elements
  if grep -q "<testsuite" "$xml_file" && grep -q "<testcase" "$xml_file"; then
    return 0  # Valid JUnit XML
  else
    return 1  # Not valid JUnit XML
  fi
}

# If we successfully pulled the test results, validate and copy to all expected locations
if [ -f "$LOCAL_TEST_RESULT_PATH" ] && [ -s "$LOCAL_TEST_RESULT_PATH" ]; then
  if is_valid_junit_xml "$LOCAL_TEST_RESULT_PATH"; then
    echo "✅ Valid JUnit XML test results found! at $LOCAL_TEST_RESULT_PATH "
    
    # Path for the second location expected by dorny/test-reporter
    DORNY_TEST_RESULT_PATH="./$MAIN_PROJECT_MODULE/build/outputs/connected/TEST-${APP_PACKAGE}.xml"
    
    # Copy to the second expected path
    cp "$LOCAL_TEST_RESULT_PATH" "$DORNY_TEST_RESULT_PATH"
    
    echo "Copied XML results to $DORNY_TEST_RESULT_PATH"
    
    # Make the XML files more compatible with dorny/test-reporter if needed
    for XML_PATH in "$LOCAL_TEST_RESULT_PATH" "$DORNY_TEST_RESULT_PATH"; do
      # Update timestamps if they're missing or malformed
      sed -i 's/timestamp="[^"]*"/timestamp="'"$(date -u +"%Y-%m-%dT%H:%M:%S")"'"/g' "$XML_PATH" 2>/dev/null || true
      
      # Ensure the XML has the correct format for the XmlRunListener
      # Add hostname attribute if missing
      sed -i 's/<testsuite /<testsuite hostname="localhost" /g' "$XML_PATH" 2>/dev/null || true
    done
    
    echo "✅ XML test results ready for dorny/test-reporter at both expected locations!"
  else
    echo "⚠️ Retrieved XML is not a valid JUnit test result file."
    rm -f "$LOCAL_TEST_RESULT_PATH"  # Remove invalid file
    XML_RETRIEVED=false
  fi
else
  echo "⚠️ No test results XML found or file is empty."
  XML_RETRIEVED=false
fi