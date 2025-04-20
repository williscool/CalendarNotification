#!/bin/bash
set -e

# Script to generate coverage reports from Android tests that have already been run
# This allows separating test execution from report generation

echo "Starting Android coverage report generation..."

# Get the architecture from command line or default to x86_64
ARCH=${1:-x86_64}
MAIN_PROJECT_MODULE=${2:-app}
REPORT_FORMAT=${3:-html} # html, xml, or csv

# Determine the build variant suffix based on architecture
if [ "$ARCH" == "arm64-v8a" ]; then
  ARCH_SUFFIX="Arm64V8a"
else
  ARCH_SUFFIX="X8664"
fi

echo "Generating coverage for architecture: $ARCH (using suffix: $ARCH_SUFFIX)"

# Navigate to the Android directory
cd android

# Create coverage file name based on architecture
COVERAGE_FILE_NAME="${ARCH_SUFFIX}DebugAndroidTest.ec"

# Expected coverage file locations
LOCAL_COVERAGE_FILE="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/${COVERAGE_FILE_NAME}"
TARGET_COVERAGE_FILE="./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/${COVERAGE_FILE_NAME}"

# Check if coverage file exists at the main location
if [ ! -f "$LOCAL_COVERAGE_FILE" ]; then
  echo "Error: Coverage file not found at: $LOCAL_COVERAGE_FILE"
  echo "Run tests first with: ./scripts/run_android_tests.sh"
  exit 1
fi

# Make sure appropriate directories exist
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/
mkdir -p ./$MAIN_PROJECT_MODULE/build/outputs/connected

# Create required test execution indicator file
touch ./$MAIN_PROJECT_MODULE/build/outputs/code_coverage/connected/TESTS_EXECUTED

# Copy the coverage file to where JaCoCo expects it
echo "Copying coverage file for JaCoCo processing..."
cp "$LOCAL_COVERAGE_FILE" "$TARGET_COVERAGE_FILE"

# Create symbolic link to test results directory
ln -sf "../androidTest-results/connected" ./$MAIN_PROJECT_MODULE/build/outputs/connected/ 2>/dev/null || true

# Generate JaCoCo report
echo "Generating JaCoCo coverage report..."

if [ "$REPORT_FORMAT" == "xml" ]; then
  # Generate XML report
  ./gradlew :$MAIN_PROJECT_MODULE:jacocoAndroidTestReport -PjacocoReportFormat=xml
elif [ "$REPORT_FORMAT" == "csv" ]; then
  # Generate CSV report
  ./gradlew :$MAIN_PROJECT_MODULE:jacocoAndroidTestReport -PjacocoReportFormat=csv
else
  # Generate HTML report (default)
  ./gradlew :$MAIN_PROJECT_MODULE:jacocoAndroidTestReport
fi

# Check if the report was generated
if [ -d "./$MAIN_PROJECT_MODULE/build/reports/jacoco/jacocoAndroidTestReport/" ]; then
  echo "✅ JaCoCo report successfully generated"
  echo "Report available at: ./$MAIN_PROJECT_MODULE/build/reports/jacoco/jacocoAndroidTestReport/"
else
  echo "❌ Failed to generate JaCoCo report"
  
  # Try alternative approach - sometimes the task needs to be run twice
  echo "Trying alternative approach..."
  ./gradlew :$MAIN_PROJECT_MODULE:createDebugCoverageReport :$MAIN_PROJECT_MODULE:jacocoAndroidTestReport --info
  
  if [ -d "./$MAIN_PROJECT_MODULE/build/reports/jacoco/jacocoAndroidTestReport/" ]; then
    echo "✅ JaCoCo report successfully generated on second attempt"
    echo "Report available at: ./$MAIN_PROJECT_MODULE/build/reports/jacoco/jacocoAndroidTestReport/"
  else
    echo "❌ Failed to generate JaCoCo report"
    exit 1
  fi
fi

echo "Android coverage report generation completed"