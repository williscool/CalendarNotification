#!/bin/bash
set -euo pipefail

# Builds the unit-test coverage reports from the sharded unit-test jobs, with
# JaCoCo's CLI. Recreates the two reports the old single unit-test job's Gradle
# tasks made -- same class filters, same output paths -- so the coverage-report
# job and test-summary.yml read them unchanged:
#
#   reports/coverage/test/x8664/debug   AGP's report (read by the PR comment)
#   reports/jacoco/unitTestCoverage     jacocoUnitTestReport's (also excludes
#                                       *Test* and com/facebook/react)
#
# Also gathers every shard's test-result XMLs under test-results/.
#
# Usage: merge_unit_test_coverage.sh [shards dir]
#   shards dir: holds the downloaded unit-test-shard-* artifacts (default: shards)
#
# Env overrides: JACOCO_VERSION, BUILD_DIR, DEBUG_VARIANT, SOURCE_DIR

SHARDS_DIR="${1:-shards}"
# What the Gradle reports ran (their HTML footer); see merge-integration-coverage
JACOCO_VERSION="${JACOCO_VERSION:-0.8.12}"
BUILD_DIR="${BUILD_DIR:-android/app/build}"
DEBUG_VARIANT="${DEBUG_VARIANT:-x8664Debug}"
SOURCE_DIR="${SOURCE_DIR:-android/app/src/main/java}"

curl -sSfL -o jacococli.jar "https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/$JACOCO_VERSION/org.jacoco.cli-$JACOCO_VERSION-nodeps.jar"

# Test result XMLs from every shard, under the paths the single job used
mkdir -p "$BUILD_DIR"
for shard in "$SHARDS_DIR"/unit-test-shard-*; do
  rsync -a --include='*/' --include='*.xml' --exclude='*' "$shard/test-results/" "$BUILD_DIR/test-results/"
done

# Every shard compiled the same classes; take them from shard 0
classes_root="$SHARDS_DIR/unit-test-shard-0"

# stage_classes <dest> <javac classes dir> <extra rsync excludes...>
stage_classes() {
  local dest=$1 javac_dir=$2
  shift 2
  mkdir -p "$dest"
  for dir in "$classes_root/tmp/kotlin-classes/$DEBUG_VARIANT" "$javac_dir"; do
    [ -d "$dir" ] || continue
    rsync -a --exclude 'R.class' --exclude 'R$*.class' --exclude 'BuildConfig.*' --exclude 'Manifest*.*' \
      "$@" "$dir/" "$dest/"
  done
  echo "$dest: $(find "$dest" -name '*.class' | wc -l) classes"
}

# AGP 8 writes javac output (e.g. the generated PackageList) under
# intermediates/javac/<variant>/compile<Variant>JavaWithJavac/classes, and AGP's
# report includes it. jacocoUnitTestReport's pattern still names the old
# intermediates/javac/<variant>/classes, so its report has none.
agp_javac=$(find "$classes_root/intermediates/javac/$DEBUG_VARIANT" -type d -name classes | head -1)
stage_classes agp-classes "$agp_javac"
stage_classes custom-classes "$classes_root/intermediates/javac/$DEBUG_VARIANT/classes" \
  --exclude '*Test*.*' \
  --exclude 'com/facebook/react/' --exclude 'com/facebook/hermes/' --exclude 'com/facebook/jni/' \
  --exclude 'org/jacoco/'

mapfile -t exec_files < <(find "$SHARDS_DIR" -name '*.exec')
echo "Coverage files: ${#exec_files[@]}"
if [ "${#exec_files[@]}" -eq 0 ]; then
  echo "::error::No unit test .exec files found"
  exit 1
fi

AGP_OUT="$BUILD_DIR/reports/coverage/test/x8664/debug"
CUSTOM_OUT="$BUILD_DIR/reports/jacoco/unitTestCoverage"
mkdir -p "$AGP_OUT" "$CUSTOM_OUT/xml" "$CUSTOM_OUT/csv"  # the CLI doesn't create these

java -jar jacococli.jar report "${exec_files[@]}" --classfiles agp-classes \
  --sourcefiles "$SOURCE_DIR" \
  --html "$AGP_OUT" --xml "$AGP_OUT/report.xml"

java -jar jacococli.jar report "${exec_files[@]}" --classfiles custom-classes \
  --sourcefiles "$SOURCE_DIR" \
  --html "$CUSTOM_OUT/html" --xml "$CUSTOM_OUT/xml/report.xml" --csv "$CUSTOM_OUT/csv/report.csv"
